package com.issr.watch.presentation

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.issr.watch.BuildConfig
import com.issr.watch.feedback.HapticController
import com.issr.watch.model.SafetyGrade
import com.issr.watch.service.ImuService
import com.issr.watch.stomp.StompClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL

/**
 * Entry point for the Wear OS app.
 *
 * Responsibilities:
 * 1. Display MainScreen (start/stop button)
 * 2. On START: call POST /api/v1/sessions → get sessionId → start ImuService → connect StompClient
 * 3. On STOP: stop ImuService → disconnect StompClient
 * 4. Wire ImuService.onBatchReady → StompClient.sendBatch
 * 5. Wire StompClient.onGradeReceived → HapticController.vibrate
 * 6. Wire StompClient.onReconnectFailed → HapticController.vibrate(DANGER) (connection alert)
 */
class MainActivity : ComponentActivity() {

    companion object {
        const val TAG = "MainActivity"
    }

    private var isRunning by mutableStateOf(false)
    private lateinit var hapticController: HapticController
    private lateinit var stompClient: StompClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        hapticController = HapticController(this)
        stompClient = StompClient(lifecycleScope).apply {
            onGradeReceived = { grade ->
                hapticController.vibrate(grade)
                // TTS is handled inside ImuService.onGradeUpdate (pre-warmed there)
                ImuService.onGradeReceived?.invoke(grade)
            }
            onReconnectFailed = {
                Log.e(TAG, "StompClient reconnect failed 5 times — alerting user via haptic")
                hapticController.vibrate(SafetyGrade.DANGER)
            }
        }

        setContent {
            MainScreen(
                isRunning = isRunning,
                onStartClick = { startSession() },
                onStopClick  = { stopSession() }
            )
        }
    }

    private fun startSession() {
        lifecycleScope.launch(Dispatchers.IO) {
            val sessionId = createSessionOnServer()
            if (sessionId == null) {
                Log.e(TAG, "Failed to create session — cannot start")
                return@launch
            }
            Log.d(TAG, "Session created: $sessionId")

            // Wire ImuBatch callback: ImuService → StompClient
            ImuService.onBatchReady = { batch ->
                stompClient.sendBatch(batch)
            }

            // Start ImuService (ForegroundService)
            val intent = Intent(this@MainActivity, ImuService::class.java).apply {
                action = ImuService.ACTION_START
                putExtra(ImuService.EXTRA_SESSION_ID, sessionId)
            }
            startService(intent)

            // Connect STOMP
            stompClient.connect(sessionId)

            withContext(Dispatchers.Main) {
                isRunning = true
            }
        }
    }

    private fun stopSession() {
        ImuService.onBatchReady = null
        ImuService.onGradeReceived = null

        val intent = Intent(this, ImuService::class.java).apply {
            action = ImuService.ACTION_STOP
        }
        startService(intent)

        stompClient.disconnect()
        isRunning = false
    }

    /**
     * POST /api/v1/sessions → returns sessionId string or null on failure.
     * Blocking — call only from Dispatchers.IO coroutine.
     */
    private fun createSessionOnServer(): String? {
        return try {
            val url = URL("${BuildConfig.SERVER_BASE_URL}/api/v1/sessions")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.outputStream.write("{}".toByteArray())

            if (conn.responseCode != 201 && conn.responseCode != 200) {
                Log.e(TAG, "Session creation failed: ${conn.responseCode}")
                return null
            }
            val responseText = conn.inputStream.bufferedReader().readText()
            Log.d(TAG, "Session response: $responseText")
            val jsonElement = Json.parseToJsonElement(responseText)
            jsonElement.jsonObject["session_id"]?.jsonPrimitive?.content
        } catch (e: Exception) {
            Log.e(TAG, "Session creation exception: ${e.message}", e)
            null
        }
    }

    override fun onDestroy() {
        stompClient.disconnect()
        super.onDestroy()
    }
}
