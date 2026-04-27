package com.issr.watch.presentation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.issr.watch.BuildConfig
import com.issr.watch.feedback.HapticController
import com.issr.watch.model.SafetyGrade
import com.issr.watch.sensor.ImuBatch
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

class MainActivity : ComponentActivity() {

    companion object {
        const val TAG = "MainActivity"
    }

    private var isRunning by mutableStateOf(false)
    private lateinit var hapticController: HapticController
    private lateinit var stompClient: StompClient

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Log.d(TAG, "Location permission granted: $granted")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        hapticController = HapticController(this)
        stompClient = StompClient(lifecycleScope).apply {
            onGradeReceived = { grade ->
                hapticController.vibrate(grade)
                ImuService.updateActiveGrade(grade)
            }
            onReconnectFailed = {
                Log.e(TAG, "StompClient reconnect failed 5 times; alerting user via haptic")
                hapticController.vibrate(SafetyGrade.DANGER)
            }
        }

        setContent {
            MainScreen(
                isRunning = isRunning,
                onStartClick = { startSession() },
                onStopClick = { stopSession() }
            )
        }
    }

    private fun startSession() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val sessionId = createSessionOnServer()
            if (sessionId == null) {
                Log.e(TAG, "Failed to create session; cannot start")
                return@launch
            }
            Log.d(TAG, "Session created: $sessionId")

            ImuService.onBatchReady = { batch ->
                sendBatchToServer(batch, sessionId)
            }

            val intent = Intent(this@MainActivity, ImuService::class.java).apply {
                action = ImuService.ACTION_START
                putExtra(ImuService.EXTRA_SESSION_ID, sessionId)
            }
            startService(intent)
            stompClient.connect(sessionId)

            withContext(Dispatchers.Main) {
                isRunning = true
            }
        }
    }

    private fun stopSession() {
        ImuService.onBatchReady = null
        stompClient.disconnect()

        val intent = Intent(this, ImuService::class.java).apply {
            action = ImuService.ACTION_STOP
        }
        startService(intent)

        isRunning = false
    }

    private fun sendBatchToServer(batch: ImuBatch, sessionId: String) {
        try {
            val samplesJson = batch.samples.joinToString(",", "[", "]") { sample ->
                """{"ax":${sample.ax},"ay":${sample.ay},"az":${sample.az},"gx":${sample.gx},"gy":${sample.gy},"gz":${sample.gz},"timestamp_ms":${sample.timestampMs}}"""
            }
            val locationPart = if (batch.lat != null) {
                val accuracyPart = if (batch.gpsAccuracyMeters != null) {
                    ""","gps_accuracy_meters":${batch.gpsAccuracyMeters}"""
                } else {
                    ""
                }
                ""","lat":${batch.lat},"lng":${batch.lng}$accuracyPart"""
            } else {
                ""
            }
            val body = """{"samples":$samplesJson,"window_ms":${batch.windowMs}$locationPart}"""
            Log.d(TAG, "IMU POST session=$sessionId samples=${batch.samples.size} body=$body")

            val url = URL("${BuildConfig.SERVER_BASE_URL}/api/v1/sessions/$sessionId/imu")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.outputStream.use { output ->
                output.write(body.toByteArray())
            }

            val responseCode = connection.responseCode
            Log.d(TAG, "IMU POST response: $responseCode")
            connection.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "IMU POST failed: ${e.message}", e)
        }
    }

    private fun createSessionOnServer(): String? {
        return try {
            val url = URL("${BuildConfig.SERVER_BASE_URL}/api/v1/sessions")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.outputStream.use { output ->
                output.write("{}".toByteArray())
            }

            if (connection.responseCode != 201 && connection.responseCode != 200) {
                Log.e(TAG, "Session creation failed: ${connection.responseCode}")
                return null
            }

            val responseText = connection.inputStream.bufferedReader().use { it.readText() }
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
