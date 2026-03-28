package com.issr.watch.stomp

import org.junit.Assert.*
import org.junit.Test

class StompClientTest {

    @Test
    fun `buildSendFrame starts with SEND`() {
        val frame = buildSendFrame("/app/session/abc/imu", "{}")
        assertTrue(frame.startsWith("SEND\n"))
    }

    @Test
    fun `buildSendFrame ends with null byte`() {
        val frame = buildSendFrame("/app/session/abc/imu", "{}")
        assertTrue(frame.endsWith("\u0000"))
    }

    @Test
    fun `buildSendFrame content-length matches body byte size`() {
        val body = "{\"session_id\":\"abc\"}"
        val frame = buildSendFrame("/app/session/abc/imu", body)
        val expectedLength = body.toByteArray(Charsets.UTF_8).size
        assertTrue(frame.contains("content-length:$expectedLength"))
    }

    @Test
    fun `buildSubscribeFrame starts with SUBSCRIBE and contains id`() {
        val frame = buildSubscribeFrame("/topic/session/abc/grade", "sub-0")
        assertTrue(frame.startsWith("SUBSCRIBE\n"))
        assertTrue(frame.contains("id:sub-0"))
        assertTrue(frame.contains("destination:/topic/session/abc/grade"))
    }

    @Test
    fun `buildConnectFrame contains heart-beat header`() {
        val frame = buildConnectFrame(10_000)
        assertTrue(frame.contains("heart-beat:10000,10000"))
        assertTrue(frame.startsWith("CONNECT\n"))
    }
}
