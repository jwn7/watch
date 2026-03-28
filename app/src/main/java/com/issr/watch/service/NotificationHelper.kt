package com.issr.watch.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.issr.watch.model.SafetyGrade

object NotificationHelper {

    const val CHANNEL_ID = "imu_service_channel"
    const val NOTIFICATION_ID = 1001

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "보행 안전 모니터링",
            NotificationManager.IMPORTANCE_LOW  // LOW: no sound, persistent
        ).apply {
            description = "IMU 데이터 수집 중"
            setShowBadge(false)
        }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    /**
     * Builds the foreground notification showing current safety grade.
     * Per D-08: ongoing notification must display current safety grade text.
     */
    fun build(context: Context, grade: SafetyGrade = SafetyGrade.SAFE): Notification {
        val (statusText, contentText) = when (grade) {
            SafetyGrade.SAFE    -> "안전" to "보행 중 — 위험 없음"
            SafetyGrade.CAUTION -> "주의" to "보행 중 — 주의 구간"
            SafetyGrade.DANGER  -> "위험" to "보행 중 — 위험 구간"
        }
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("ISSR 보행 안전: $statusText")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}
