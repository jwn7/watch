package com.issr.watch.service

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.issr.watch.model.SafetyGrade
import com.issr.watch.sensor.ImuBatch
import com.issr.watch.sensor.ImuSample
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class ImuService : LifecycleService() {

    companion object {
        const val TAG = "ImuService"
        const val ACTION_START = "com.issr.watch.ACTION_START"
        const val ACTION_STOP  = "com.issr.watch.ACTION_STOP"
        const val EXTRA_SESSION_ID = "session_id"
        const val BATCH_WINDOW_MS = 1000L  // 1초 배치 (데이터 수집 모드)

        // External callbacks — set by MainActivity before starting service
        var onBatchReady: ((ImuBatch) -> Unit)? = null
        var onGradeReceived: ((SafetyGrade) -> Unit)? = null
    }

    private lateinit var sensorManager: SensorManager
    private var locationManager: LocationManager? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var tts: TextToSpeech
    private val ttsReady = AtomicBoolean(false)

    private val pendingSamples = CopyOnWriteArrayList<ImuSample>()
    private val currentSessionId = AtomicReference<String?>(null)
    private val lastLocation = AtomicReference<Location?>(null)
    private val accelData = FloatArray(3)
    private val gyroData  = FloatArray(3)
    private var lastAccelTime = 0L
    private var lastGyroTime  = 0L

    private val locationListener = LocationListener { location ->
        lastLocation.set(location)
    }

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    System.arraycopy(event.values, 0, accelData, 0, 3)
                    lastAccelTime = event.timestamp / 1_000_000L  // ns → ms
                }
                Sensor.TYPE_GYROSCOPE -> {
                    System.arraycopy(event.values, 0, gyroData, 0, 3)
                    lastGyroTime = event.timestamp / 1_000_000L
                }
            }
            // Enqueue sample only when both sensors have reported (timestamp within 20ms tolerance)
            if (lastAccelTime > 0 && lastGyroTime > 0 &&
                kotlin.math.abs(lastAccelTime - lastGyroTime) < 20) {
                pendingSamples.add(
                    ImuSample(
                        ax = accelData[0], ay = accelData[1], az = accelData[2],
                        gx = gyroData[0],  gy = gyroData[1],  gz = gyroData[2],
                        timestampMs = lastAccelTime,
                        calibrationBias = null  // D-05: raw data, no calibration
                    )
                )
            }
        }
        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) { /* unused */ }
    }

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        NotificationHelper.createChannel(this)
        // Pre-warm TTS (D-19: Locale.KOREAN, async init)
        tts = TextToSpeech(applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts.setLanguage(Locale.KOREAN)
                ttsReady.set(
                    result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED
                )
                Log.d(TAG, "TTS ready: ${ttsReady.get()}, result=$result")
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        // CRITICAL: startForeground MUST be called first (C-3 mitigation)
        startForeground(
            NotificationHelper.NOTIFICATION_ID,
            NotificationHelper.build(this, SafetyGrade.SAFE)
        )

        when (intent?.action) {
            ACTION_START -> {
                val sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
                    ?: return START_STICKY
                currentSessionId.set(sessionId)
                acquireWakeLock()
                registerSensors()
                registerLocation()
                startBatchLoop()
                Log.d(TAG, "ImuService started for session: $sessionId")
            }
            ACTION_STOP -> {
                stopCollection()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "issr-watch:ImuService"
        ).apply {
            acquire(2 * 60 * 60 * 1000L)  // 2-hour max per ROADMAP spec
        }
        Log.d(TAG, "WakeLock acquired (2h timeout)")
    }

    private fun registerSensors() {
        val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val gyro  = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        // SENSOR_DELAY_GAME = 20,000µs = 50Hz
        // maxReportLatencyUs = 500,000µs = 500ms FIFO batching
        sensorManager.registerListener(sensorListener, accel,
            SensorManager.SENSOR_DELAY_GAME, 500_000)
        sensorManager.registerListener(sensorListener, gyro,
            SensorManager.SENSOR_DELAY_GAME, 500_000)
        Log.d(TAG, "Sensors registered at SENSOR_DELAY_GAME (50Hz)")
    }

    @Suppress("MissingPermission")
    private fun registerLocation() {
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val hasPerm = checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        if (!hasPerm) {
            Log.w(TAG, "Location permission not granted — GPS skipped")
            return
        }
        locationManager?.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            1_000L,   // 1초 간격
            1f,       // 1m 이동 시
            locationListener
        )
        Log.d(TAG, "GPS location updates registered")
    }

    private fun startBatchLoop() {
        val sessionId = currentSessionId.get() ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            while (true) {
                delay(BATCH_WINDOW_MS)
                val snapshot = pendingSamples.toList()
                pendingSamples.clear()
                if (snapshot.isNotEmpty()) {
                    val loc = lastLocation.get()
                    val batch = ImuBatch(
                        sessionId = sessionId,
                        samples = snapshot,
                        windowMs = BATCH_WINDOW_MS.toInt(),
                        lat = loc?.latitude,
                        lng = loc?.longitude
                    )
                    onBatchReady?.invoke(batch)
                }
            }
        }
    }

    /**
     * Called by StompClient when a safety grade is received from server.
     * Updates the foreground notification and triggers haptic + TTS (per WATCH-03/WATCH-04).
     * Haptic/TTS implementations are in HapticController/TtsController (Plan 04).
     */
    fun onGradeUpdate(grade: SafetyGrade) {
        updateNotification(grade)
        onGradeReceived?.invoke(grade)
        // TTS: only DANGER triggers TTS per D-18/D-19
        if (grade == SafetyGrade.DANGER && ttsReady.get()) {
            tts.speak("위험! 멈추세요", TextToSpeech.QUEUE_FLUSH, null, "danger_utterance")
        }
    }

    private fun updateNotification(grade: SafetyGrade) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(NotificationHelper.NOTIFICATION_ID, NotificationHelper.build(this, grade))
    }

    private fun stopCollection() {
        sensorManager.unregisterListener(sensorListener)
        locationManager?.removeUpdates(locationListener)
        locationManager = null
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
        currentSessionId.set(null)
        pendingSamples.clear()
        Log.d(TAG, "ImuService stopped — sensors unregistered, WakeLock released")
    }

    override fun onDestroy() {
        stopCollection()
        tts.stop()
        tts.shutdown()
        super.onDestroy()
    }
}
