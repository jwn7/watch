package com.issr.watch.stomp

import org.junit.Assert.*
import org.junit.Test

class StompParserTest {

    @Test
    fun `parseStompFrame parses CONNECTED frame`() {
        val raw = "CONNECTED\nversion:1.2\n\nsome-body\u0000"
        val frame = parseStompFrame(raw)
        assertNotNull(frame)
        assertEquals("CONNECTED", frame!!.command)
        assertEquals("1.2", frame.headers["version"])
    }

    @Test
    fun `parseStompFrame parses MESSAGE frame with body`() {
        val raw = "MESSAGE\ndestination:/topic/grade\n\n{\"grade\":\"DANGER\"}\u0000"
        val frame = parseStompFrame(raw)
        assertNotNull(frame)
        assertEquals("MESSAGE", frame!!.command)
        assertEquals("/topic/grade", frame.headers["destination"])
        assertTrue(frame.body.contains("DANGER"))
    }

    @Test
    fun `parseStompFrame returns null for heartbeat newline`() {
        val frame = parseStompFrame("\n")
        assertNull(frame)
    }

    @Test
    fun `parseStompFrame returns null for blank string`() {
        val frame = parseStompFrame("")
        assertNull(frame)
    }
}
