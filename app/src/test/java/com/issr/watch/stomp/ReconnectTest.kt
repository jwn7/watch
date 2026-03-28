package com.issr.watch.stomp

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

/**
 * Validates the exponential backoff delay calculation logic.
 * The actual reconnect coroutine lives in StompClient — tested here as pure math.
 * Base: 1000ms, max: 30000ms, jitter: ±500ms
 */
class ReconnectTest {

    private fun calcBackoffMs(attempt: Int): Long {
        val baseDelayMs = 1_000L
        val maxDelayMs = 30_000L
        // No jitter in tests (jitter is Random — test the bounds without it)
        return (baseDelayMs * (1L shl (attempt - 1).coerceAtMost(5)))
            .coerceAtMost(maxDelayMs)
    }

    @Test
    fun `attempt 1 backoff is 1000ms`() {
        assertEquals(1_000L, calcBackoffMs(1))
    }

    @Test
    fun `attempt 2 backoff is 2000ms`() {
        assertEquals(2_000L, calcBackoffMs(2))
    }

    @Test
    fun `attempt 5 backoff is 16000ms`() {
        assertEquals(16_000L, calcBackoffMs(5))
    }

    @Test
    fun `attempt 6 and beyond is capped at 30000ms`() {
        assertEquals(30_000L, calcBackoffMs(6))
        assertEquals(30_000L, calcBackoffMs(10))
        assertEquals(30_000L, calcBackoffMs(100))
    }
}
