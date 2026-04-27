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
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "IMU 데이터를 수집하는 중입니다."
            setShowBadge(false)
        }
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    fun build(context: Context, grade: SafetyGrade = SafetyGrade.SAFE): Notification {
        val (statusText, contentText) = when (grade) {
            SafetyGrade.SAFE -> "안전" to "보행 중입니다. 위험 구간이 없습니다."
            SafetyGrade.CAUTION -> "주의" to "보행 중입니다. 주의 구간이 감지되었습니다."
            SafetyGrade.DANGER -> "위험" to "보행 중입니다. 위험 구간이 감지되었습니다."
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
