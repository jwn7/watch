package com.issr.watch.stomp

import android.util.Log
import com.issr.watch.BuildConfig
import com.issr.watch.model.SafetyGrade
import com.issr.watch.sensor.ImuBatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random

/**
 * OkHttp WebSocket STOMP 1.2 client.
 *
 * CRITICAL: No SockJS. Server uses plain RFC 6455 WebSocket at /ws.
 * Per Phase 1 decision: SockJS causes handshake failure with OkHttp native WebSocket.
 *
 * Threading model: OkHttp callbacks run on OkHttp dispatcher thread.
 * All coroutine operations use a Channel bridge to avoid thread-safety issues.
 */
class StompClient(private val coroutineScope: CoroutineScope) {

    companion object {
        const val TAG = "StompClient"
        const val HEARTBEAT_INTERVAL_MS = 9_000L  // Send every 9s for 10s server window
        const val BASE_DELAY_MS = 1_000L
        const val MAX_DELAY_MS  = 30_000L
        const val MAX_RECONNECT_ATTEMPTS = 5
    }

    var onGradeReceived: ((SafetyGrade) -> Unit)? = null
    var onReconnectFailed: (() -> Unit)? = null

    private val currentWebSocket = AtomicReference<WebSocket?>(null)
    private val incomingFrames = Channel<String>(Channel.UNLIMITED)
    private var sessionId: String? = null
    private var reconnectJob: Job? = null
    private var heartbeatJob: Job? = null
    private var frameProcessingJob: Job? = null

    private val json = Json { ignoreUnknownKeys = true }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)  // 0 = no timeout for persistent WebSocket
        .build()

    /**
     * Connect to the STOMP server and subscribe to the safety grade topic.
     * Call before sendBatch().
     *
     * @param sessionId The walking session UUID obtained from POST /api/v1/sessions
     */
    fun connect(sessionId: String) {
        this.sessionId = sessionId
        reconnectJob?.cancel()
        startFrameProcessingLoop()
        doConnect(sessionId)
    }

    private fun doConnect(sessionId: String) {
        val request = Request.Builder()
            .url(BuildConfig.SERVER_WS_URL)  // D-13: no hardcoding
            .build()

        okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket opened — sending CONNECT frame")
                currentWebSocket.set(webSocket)
                webSocket.send(buildConnectFrame(10_000))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                // Bridge to coroutine via Channel (Pitfall 5 mitigation)
                incomingFrames.trySend(text)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.w(TAG, "WebSocket closed: $code $reason")
                currentWebSocket.set(null)
                heartbeatJob?.cancel()
                startReconnect(sessionId)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}")
                currentWebSocket.set(null)
                heartbeatJob?.cancel()
                startReconnect(sessionId)
            }
        })
    }

    private fun startFrameProcessingLoop() {
        frameProcessingJob?.cancel()
        frameProcessingJob = coroutineScope.launch(Dispatchers.IO) {
            for (rawFrame in incomingFrames) {
                processFrame(rawFrame)
            }
        }
    }

    private fun processFrame(raw: String) {
        val frame = parseStompFrame(raw) ?: return  // heartbeat or blank
        when (frame.command) {
            "CONNECTED" -> {
                Log.d(TAG, "STOMP CONNECTED — subscribing to grade topic")
                val destination = "/topic/session/$sessionId/grade"
                currentWebSocket.get()?.send(buildSubscribeFrame(destination, "sub-0"))
                startHeartbeat()
            }
            "MESSAGE" -> {
                val body = frame.body.trim()
                if (body.isNotEmpty()) {
                    parseGradeFromJson(body)?.let { grade ->
                        onGradeReceived?.invoke(grade)
                    }
                }
            }
            "ERROR" -> {
                Log.e(TAG, "STOMP ERROR frame: ${frame.body}")
            }
        }
    }

    private fun parseGradeFromJson(body: String): SafetyGrade? {
        return try {
            val jsonObj = json.parseToJsonElement(body).jsonObject
            when (jsonObj["grade"]?.jsonPrimitive?.content) {
                "SAFE"    -> SafetyGrade.SAFE
                "CAUTION" -> SafetyGrade.CAUTION
                "DANGER"  -> SafetyGrade.DANGER
                else -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse grade JSON: $body", e)
            null
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = coroutineScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                currentWebSocket.get()?.send("\n")  // STOMP 1.2 heartbeat frame
            }
        }
    }

    /**
     * Send an IMU batch to the server.
     * Must be called only after connect() succeeds (STOMP CONNECTED received).
     */
    fun sendBatch(batch: ImuBatch) {
        val ws = currentWebSocket.get() ?: run {
            Log.w(TAG, "sendBatch called but WebSocket not connected — dropping batch")
            return
        }
        val body = Json.encodeToString(ImuBatch.serializer(), batch)
        val destination = "/app/session/${batch.sessionId}/imu"
        val frame = buildSendFrame(destination, body)
        ws.send(frame)
    }

    /**
     * Gracefully disconnect STOMP and close WebSocket.
     */
    fun disconnect() {
        reconnectJob?.cancel()
        heartbeatJob?.cancel()
        frameProcessingJob?.cancel()
        currentWebSocket.get()?.let {
            it.send(buildDisconnectFrame())
            it.cancel()
        }
        currentWebSocket.set(null)
        sessionId = null
    }

    /**
     * Exponential backoff reconnect.
     * Base: 1s, max: 30s, jitter: ±500ms. After MAX_RECONNECT_ATTEMPTS, notify via callback.
     * Per CONTEXT.md D-12 / ROADMAP Phase 3 task 4 / Pitfall M-5.
     */
    private fun startReconnect(sessionId: String) {
        reconnectJob?.cancel()
        reconnectJob = coroutineScope.launch(Dispatchers.IO) {
            var attempt = 0
            while (isActive) {
                attempt++
                val jitter = Random.nextLong(-500L, 500L)
                val backoff = (BASE_DELAY_MS * (1L shl (attempt - 1).coerceAtMost(5)) + jitter)
                    .coerceIn(0L, MAX_DELAY_MS)

                Log.d(TAG, "Reconnect attempt $attempt in ${backoff}ms")
                delay(backoff)

                if (attempt >= MAX_RECONNECT_ATTEMPTS) {
                    Log.e(TAG, "Reconnect failed after $attempt attempts")
                    onReconnectFailed?.invoke()
                    // Continue retrying (but notify user each time beyond threshold)
                }

                doConnect(sessionId)
                // Wait for onOpen or onFailure to signal result
                delay(10_000L)  // Give 10s for connection to establish before next attempt
                if (currentWebSocket.get() != null) {
                    Log.d(TAG, "Reconnect succeeded on attempt $attempt")
                    return@launch
                }
            }
        }
    }
}
