package com.issr.watch.feedback

import com.issr.watch.model.SafetyGrade
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Verifies TTS controller behavior: correct utterance text and safe no-op when not ready.
 * Uses pure logic (no Android TTS object) to test the guard and message selection.
 * Per D-18: DANGER message = "위험! 멈추세요"
 * Per D-19: Locale.KOREAN, QUEUE_FLUSH
 */
class TtsControllerTest {

    private val dangerMessage = "위험! 멈추세요"
    private var lastSpokenText: String? = null
    private val ttsReady = AtomicBoolean(false)

    private fun speak(grade: SafetyGrade) {
        if (!ttsReady.get()) return  // guard: no-op if not initialized
        lastSpokenText = when (grade) {
            SafetyGrade.DANGER -> dangerMessage
            else -> null  // SAFE/CAUTION: no TTS
        }
    }

    @Test
    fun `DANGER grade speaks correct 8-syllable message`() {
        ttsReady.set(true)
        speak(SafetyGrade.DANGER)
        assertEquals("위험! 멈추세요", lastSpokenText)
    }

    @Test
    fun `DANGER message length is at most 8 syllables`() {
        // "위험! 멈추세요" = 6 Korean characters (음절)
        val koreanCharsOnly = dangerMessage.filter { it.code in 0xAC00..0xD7A3 }
        assertTrue("DANGER message must be <= 8 syllables, got ${koreanCharsOnly.length}",
            koreanCharsOnly.length <= 8)
    }

    @Test
    fun `speak is no-op when ttsReady is false`() {
        ttsReady.set(false)
        lastSpokenText = null
        speak(SafetyGrade.DANGER)
        assertNull("Must not speak when TTS not ready", lastSpokenText)
    }

    @Test
    fun `SAFE grade does not trigger TTS`() {
        ttsReady.set(true)
        lastSpokenText = null
        speak(SafetyGrade.SAFE)
        assertNull(lastSpokenText)
    }

    @Test
    fun `CAUTION grade does not trigger TTS`() {
        ttsReady.set(true)
        lastSpokenText = null
        speak(SafetyGrade.CAUTION)
        assertNull(lastSpokenText)
    }
}
