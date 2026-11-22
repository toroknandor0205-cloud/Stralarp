package com.stralarp.app.data.model

import java.time.Instant

data class Route(
    val name: String = "Stralarp Route",
    val description: String = "",
    val activityType: String = "running",
    val creator: String = "Stralarp",
    val points: List<TrackPoint> = emptyList(),
    val startTime: Instant? = null
) {
    val totalDistanceMeters: Double
        get() {
            if (points.size < 2) return 0.0
            var dist = 0.0
            for (i in 1 until points.size) {
                dist += points[i - 1].distanceTo(points[i])
            }
            return dist
        }

    val totalDistanceKm: Double get() = totalDistanceMeters / 1000.0
    val totalDistanceMiles: Double get() = totalDistanceMeters / 1609.344

    val elevationGain: Double
        get() {
            var gain = 0.0
            for (i in 1 until points.size) {
                val prev = points[i - 1].elevation ?: continue
                val curr = points[i].elevation ?: continue
                if (curr > prev) gain += (curr - prev)
            }
            return gain
        }

    val elevationLoss: Double
        get() {
            var loss = 0.0
            for (i in 1 until points.size) {
                val prev = points[i - 1].elevation ?: continue
                val curr = points[i].elevation ?: continue
                if (curr < prev) loss += (prev - curr)
            }
            return loss
        }
}
