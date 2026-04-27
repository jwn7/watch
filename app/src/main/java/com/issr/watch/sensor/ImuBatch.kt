package com.issr.watch.sensor

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ImuSample(
    @SerialName("ax") val ax: Float,
    @SerialName("ay") val ay: Float,
    @SerialName("az") val az: Float,
    @SerialName("gx") val gx: Float,
    @SerialName("gy") val gy: Float,
    @SerialName("gz") val gz: Float,
    @SerialName("timestamp_ms") val timestampMs: Long,
    @SerialName("calibration_bias") val calibrationBias: FloatArray? = null  // D-05: optional, null for raw
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ImuBatch(
    @SerialName("session_id") val sessionId: String,
    @SerialName("samples") val samples: List<ImuSample>,
    @EncodeDefault @SerialName("window_ms") val windowMs: Int = 500,
    @SerialName("lat") val lat: Double? = null,
    @SerialName("lng") val lng: Double? = null,
    @SerialName("gps_accuracy_meters") val gpsAccuracyMeters: Double? = null
)
