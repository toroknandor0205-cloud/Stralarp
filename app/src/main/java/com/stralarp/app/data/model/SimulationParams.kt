package com.stralarp.app.data.model

import java.time.LocalDateTime

data class SimulationParams(
    val startDateTime: LocalDateTime = LocalDateTime.now(),
    val targetPaceMinPerKm: Double = 5.5,
    val targetDurationSeconds: Long? = null,
    val usePace: Boolean = true,
    val paceVariabilityPercent: Float = 8f,
    val samplingIntervalSeconds: Int = 2,
    val injectGpsJitter: Boolean = true,
    val jitterMeters: Double = 1.8,
    val scaleElevation: Float = 1.0f,
    val smoothElevation: Boolean = true,
    val injectHeartRate: Boolean = false,
    val baseHeartRate: Int = 145
)
