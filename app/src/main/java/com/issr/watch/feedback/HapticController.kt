package com.issr.watch.feedback

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.issr.watch.model.SafetyGrade

/**
 * Controls haptic feedback for safety grades.
 *
 * Per D-14/D-15/D-16:
 *   DANGER:  100ms on/off × 5 pulses, amplitude 255
 *   CAUTION: 200ms on / 100ms off × 2, amplitude 180
 *   SAFE:    no vibration (silence = safe)
 *
 * Uses VibrationEffect.createWaveform() per D-17.
 * Deprecated Vibrator.vibrate(long) is NOT used.
 */
class HapticController(private val context: Context) {

    companion object {
        const val TAG = "HapticController"

        // D-14: DANGER — 100ms on/off × 5, amplitude 255
        // timings: [off_before_first_pulse, on, off, on, off, on, off, on, off, on, off]
        val DANGER_TIMINGS    = longArrayOf(0, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100)
        val DANGER_AMPLITUDES = intArrayOf(  0, 255,   0, 255,   0, 255,   0, 255,   0, 255,   0)

        // D-15: CAUTION — 200ms on / 100ms off / 200ms on × 2, amplitude 180
        val CAUTION_TIMINGS    = longArrayOf(0, 200, 100, 200, 100, 200, 100, 200)
        val CAUTION_AMPLITUDES = intArrayOf(  0, 180,   0, 180,   0, 180,   0, 180)
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

    /**
     * Trigger haptic feedback for the given safety grade.
     * SAFE triggers no vibration.
     */
    fun vibrate(grade: SafetyGrade) {
        when (grade) {
            SafetyGrade.DANGER  -> vibratePattern(DANGER_TIMINGS, DANGER_AMPLITUDES)
            SafetyGrade.CAUTION -> vibratePattern(CAUTION_TIMINGS, CAUTION_AMPLITUDES)
            SafetyGrade.SAFE    -> { /* D-16: silence = safe */ }
        }
    }

    /**
     * Cancel any ongoing vibration immediately.
     */
    fun cancel() {
        vibrator.cancel()
    }

    private fun vibratePattern(timings: LongArray, amplitudes: IntArray) {
        if (!vibrator.hasVibrator()) {
            Log.w(TAG, "No vibrator available on this device")
            return
        }
        val effect = if (vibrator.hasAmplitudeControl()) {
            // Full amplitude control: use specified amplitudes
            VibrationEffect.createWaveform(timings, amplitudes, -1 /* no repeat */)
        } else {
            // No amplitude control: create with default amplitudes (clips to 100%)
            VibrationEffect.createWaveform(timings, -1 /* no repeat */)
        }
        vibrator.vibrate(effect)
    }
}
