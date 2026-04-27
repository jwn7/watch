package com.issr.watch.feedback

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import com.issr.watch.model.SafetyGrade
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class TtsController(private val context: Context) {

    companion object {
        const val TAG = "TtsController"
        const val DANGER_UTTERANCE_ID = "danger_utterance"
        const val DANGER_MESSAGE = "위험! 멈추세요"
    }

    private var tts: TextToSpeech? = null
    private val ttsReady = AtomicBoolean(false)

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

    fun speak(grade: SafetyGrade) {
        if (!ttsReady.get()) {
            Log.d(TAG, "TTS not ready - skipping speak for $grade")
            return
        }

        when (grade) {
            SafetyGrade.DANGER -> {
                tts?.speak(DANGER_MESSAGE, TextToSpeech.QUEUE_FLUSH, null, DANGER_UTTERANCE_ID)
            }

            SafetyGrade.CAUTION, SafetyGrade.SAFE -> Unit
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ttsReady.set(false)
    }
}
