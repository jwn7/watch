package com.issr.watch.feedback

import org.junit.Assert.assertEquals
import org.junit.Test

class TtsControllerTest {

    @Test
    fun `danger utterance id is stable`() {
        assertEquals("danger_utterance", TtsController.DANGER_UTTERANCE_ID)
    }

    @Test
    fun `danger message matches expected korean text`() {
        assertEquals("위험! 멈추세요", TtsController.DANGER_MESSAGE)
    }
}
