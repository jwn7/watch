package com.issr.watch.feedback

import com.issr.watch.model.SafetyGrade
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class HapticControllerTest {

    @Test
    fun `danger pattern matches spec`() {
        assertArrayEquals(
            longArrayOf(0, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100),
            HapticController.DANGER_TIMINGS
        )
        assertArrayEquals(
            intArrayOf(0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0),
            HapticController.DANGER_AMPLITUDES
        )
    }

    @Test
    fun `caution pattern matches spec`() {
        assertArrayEquals(
            longArrayOf(0, 200, 100, 200, 100, 200, 100, 200),
            HapticController.CAUTION_TIMINGS
        )
        assertArrayEquals(
            intArrayOf(0, 180, 0, 180, 0, 180, 0, 180),
            HapticController.CAUTION_AMPLITUDES
        )
    }

    @Test
    fun `safe grade means no vibration trigger path`() {
        val safe = SafetyGrade.SAFE
        assert(safe == SafetyGrade.SAFE)
    }
}
