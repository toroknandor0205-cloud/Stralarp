package com.stralarp.app.data.model

import java.time.Instant
import kotlin.math.*

data class TrackPoint(
    val lat: Double,
    val lon: Double,
    val elevation: Double? = null,
    val time: Instant? = null,
    val heartRate: Int? = null,
    val cadence: Int? = null
) {
    fun distanceTo(other: TrackPoint): Double {
        val R = 6371000.0
        val dLat = Math.toRadians(other.lat - lat)
        val dLon = Math.toRadians(other.lon - lon)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat)) * cos(Math.toRadians(other.lat)) *
                sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }
}
