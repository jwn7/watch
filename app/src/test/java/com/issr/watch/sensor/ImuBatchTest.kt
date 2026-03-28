package com.issr.watch.sensor

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImuBatchTest {

    private fun makeSample(v: Float = 0f) = ImuSample(
        ax = v, ay = v, az = v, gx = v, gy = v, gz = v, timestampMs = 0L
    )

    @Test
    fun `ImuBatch default window_ms is 500`() {
        val batch = ImuBatch(sessionId = "test-session", samples = emptyList())
        assertEquals(500, batch.windowMs)
    }

    @Test
    fun `ImuBatch accumulates 5 samples`() {
        val samples = List(5) { makeSample(it.toFloat()) }
        val batch = ImuBatch(sessionId = "test-session", samples = samples)
        assertEquals(5, batch.samples.size)
    }

    @Test
    fun `ImuBatch serializes with snake_case session_id key`() {
        val batch = ImuBatch(sessionId = "abc-123", samples = emptyList())
        val json = Json.encodeToString(ImuBatch.serializer(), batch)
        assertTrue("JSON must contain session_id key", json.contains("\"session_id\""))
        assertTrue("JSON must contain window_ms key", json.contains("\"window_ms\""))
    }

    @Test
    fun `ImuSample calibration_bias is null by default`() {
        val sample = makeSample()
        assertEquals(null, sample.calibrationBias)
    }
}
