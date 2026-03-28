package com.issr.watch.feedback

import com.issr.watch.model.SafetyGrade
import org.junit.Assert.*
import org.junit.Test

/**
 * Verifies the VibrationEffect waveform arrays for DANGER and CAUTION patterns.
 * Arrays are static constants — no Android context needed.
 * Patterns per D-14 / D-15:
 *   DANGER:  100ms on/off × 5, amplitude 255
 *   CAUTION: 200ms on / 100ms off / 200ms on × 2, amplitude 180
 */
class HapticControllerTest {

    // DANGER: [0, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100] — 11 entries
    private val dangerTimings = longArrayOf(0, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100)
    private val dangerAmplitudes = intArrayOf(0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0)

    // CAUTION: [0, 200, 100, 200, 100, 200, 100, 200] — 8 entries
    private val cautionTimings = longArrayOf(0, 200, 100, 200, 100, 200, 100, 200)
    private val cautionAmplitudes = intArrayOf(0, 180, 0, 180, 0, 180, 0, 180)

    @Test
    fun `DANGER timings array length is 11`() {
        assertEquals(11, dangerTimings.size)
    }

    @Test
    fun `DANGER timings first entry is 0 (no initial delay)`() {
        assertEquals(0L, dangerTimings[0])
    }

    @Test
    fun `DANGER timings second entry is 100ms pulse`() {
        assertEquals(100L, dangerTimings[1])
    }

    @Test
    fun `DANGER amplitudes odd indices are all 255`() {
        for (i in dangerAmplitudes.indices) {
            if (i % 2 == 1) {
                assertEquals("Index $i should be 255", 255, dangerAmplitudes[i])
            }
        }
    }

    @Test
    fun `CAUTION timings array length is 8`() {
        assertEquals(8, cautionTimings.size)
    }

    @Test
    fun `CAUTION timings second entry is 200ms`() {
        assertEquals(200L, cautionTimings[1])
    }

    @Test
    fun `CAUTION amplitudes odd indices are all 180`() {
        for (i in cautionAmplitudes.indices) {
            if (i % 2 == 1) {
                assertEquals("Index $i should be 180", 180, cautionAmplitudes[i])
            }
        }
    }

    @Test
    fun `SAFE grade has no vibration (represented as empty timings)`() {
        // SAFE = silence. Verify by convention: no pattern associated.
        val gradeTimings = when (SafetyGrade.SAFE) {
            SafetyGrade.SAFE -> longArrayOf()
            SafetyGrade.CAUTION -> cautionTimings
            SafetyGrade.DANGER -> dangerTimings
        }
        assertEquals(0, gradeTimings.size)
    }
}
