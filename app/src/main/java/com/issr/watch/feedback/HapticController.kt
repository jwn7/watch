package com.issr.watch.feedback

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.issr.watch.model.SafetyGrade

class HapticController(private val context: Context) {

    companion object {
        const val TAG = "HapticController"

        val DANGER_TIMINGS = longArrayOf(0, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100)
        val DANGER_AMPLITUDES = intArrayOf(0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0)

        val CAUTION_TIMINGS = longArrayOf(0, 200, 100, 200, 100, 200, 100, 200)
        val CAUTION_AMPLITUDES = intArrayOf(0, 180, 0, 180, 0, 180, 0, 180)
    }

    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= 31) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
                .defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    fun vibrate(grade: SafetyGrade) {
        when (grade) {
            SafetyGrade.DANGER -> vibratePattern(DANGER_TIMINGS, DANGER_AMPLITUDES)
            SafetyGrade.CAUTION -> vibratePattern(CAUTION_TIMINGS, CAUTION_AMPLITUDES)
            SafetyGrade.SAFE -> Unit
        }
    }

    fun cancel() {
        vibrator.cancel()
    }

    private fun vibratePattern(timings: LongArray, amplitudes: IntArray) {
        if (!vibrator.hasVibrator()) {
            Log.w(TAG, "No vibrator available on this device")
            return
        }
        val effect = if (vibrator.hasAmplitudeControl()) {
            VibrationEffect.createWaveform(timings, amplitudes, -1)
        } else {
            VibrationEffect.createWaveform(timings, -1)
        }
        vibrator.vibrate(effect)
    }
}
