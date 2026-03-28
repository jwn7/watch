package com.issr.watch.feedback

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import com.issr.watch.model.SafetyGrade
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Wraps TextToSpeech for safety grade announcements.
 *
 * Per D-18: DANGER message = "위험! 멈추세요" (8 syllables)
 * Per D-19: Locale.KOREAN, QUEUE_FLUSH (interrupt previous utterance)
 *
 * TTS init is async — calls before OnInitListener fires are silently ignored (no crash).
 * Pre-warm by calling init() in service onCreate().
 */
class TtsController(private val context: Context) {

    companion object {
        const val TAG = "TtsController"
        const val DANGER_UTTERANCE_ID = "danger_utterance"
        // D-18: confirmed 8-syllable Korean message
        const val DANGER_MESSAGE = "위험! 멈추세요"
    }

    private var tts: TextToSpeech? = null
    private val ttsReady = AtomicBoolean(false)

    /**
     * Initialize TTS engine. Must be called before speak().
     * Callback fires asynchronously — safe to call speak() immediately (guarded by ttsReady).
     */
    fun init() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.KOREAN) ?: TextToSpeech.LANG_NOT_SUPPORTED
                val ready = result != TextToSpeech.LANG_MISSING_DATA &&
                            result != TextToSpeech.LANG_NOT_SUPPORTED
                ttsReady.set(ready)
                Log.d(TAG, "TTS initialized: ready=$ready, result=$result")
            } else {
                Log.e(TAG, "TTS init failed with status=$status")
            }
        }
    }

    /**
     * Speak the safety grade announcement.
     * Only DANGER triggers speech (D-18/D-19).
     * No-op if TTS not initialized or language unavailable.
     */
    fun speak(grade: SafetyGrade) {
        if (!ttsReady.get()) {
            Log.d(TAG, "TTS not ready — skipping speak for $grade")
            return
        }
        when (grade) {
            SafetyGrade.DANGER -> {
                // QUEUE_FLUSH: interrupt any current utterance (D-19)
                tts?.speak(DANGER_MESSAGE, TextToSpeech.QUEUE_FLUSH, null, DANGER_UTTERANCE_ID)
            }
            SafetyGrade.CAUTION, SafetyGrade.SAFE -> {
                // No TTS for CAUTION/SAFE — haptic only
            }
        }
    }

    /**
     * Release TTS engine resources. Call from onDestroy().
     */
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ttsReady.set(false)
    }
}
