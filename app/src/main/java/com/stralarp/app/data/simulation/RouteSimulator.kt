package com.stralarp.app.data.simulation

import com.stralarp.app.data.model.Route
import com.stralarp.app.data.model.SimulationParams
import com.stralarp.app.data.model.TrackPoint
import java.time.ZoneOffset
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.random.Random

class RouteSimulator {

    fun simulate(route: Route, params: SimulationParams): Route {
        if (route.points.isEmpty()) return route

        var points = route.points

        if (params.scaleElevation != 1.0f || params.smoothElevation) {
            points = processElevation(points, params)
        }

        if (params.injectGpsJitter) {
            points = injectJitter(points, params.jitterMeters)
        }

        points = applyRealisticTiming(points, params)

        return route.copy(
            points = points,
            startTime = points.firstOrNull()?.time
        )
    }

    private fun processElevation(points: List<TrackPoint>, params: SimulationParams): List<TrackPoint> {
        if (points.isEmpty()) return points
        var eles = points.map { it.elevation ?: 0.0 }.toMutableList()

        if (params.scaleElevation != 1.0f) {
            val base = eles.first()
            for (i in eles.indices) {
                eles[i] = base + (eles[i] - base) * params.scaleElevation
            }
        }

        if (params.smoothElevation && eles.size > 4) {
            val smoothed = eles.toMutableList()
            for (i in 2 until eles.size - 2) {
                smoothed[i] = (eles[i - 2] + eles[i - 1] + eles[i] + eles[i + 1] + eles[i + 2]) / 5.0
            }
            eles = smoothed
        }

        return points.mapIndexed { i, p -> p.copy(elevation = eles[i]) }
    }

    private fun injectJitter(points: List<TrackPoint>, meters: Double): List<TrackPoint> {
        if (meters <= 0.0) return points
        val R = 6371000.0
        return points.map { p ->
            val dLat = (Random.nextDouble(-meters, meters) / R) * (180.0 / PI)
            val dLon = (Random.nextDouble(-meters, meters) / (R * cos(Math.toRadians(p.lat)))) * (180.0 / PI)
            p.copy(lat = p.lat + dLat, lon = p.lon + dLon)
        }
    }

    private fun applyRealisticTiming(points: List<TrackPoint>, params: SimulationParams): List<TrackPoint> {
        if (points.size < 2) return points

        val totalDistM = points.zipWithNext { a, b -> a.distanceTo(b) }.sum()
        if (totalDistM < 1.0) return points

        val targetDurationSec = params.targetDurationSeconds
            ?: (totalDistM / 1000.0 * params.targetPaceMinPerKm * 60.0).toLong()

        val interval = params.samplingIntervalSeconds.coerceAtLeast(1)

        val cumDist = DoubleArray(points.size)
        for (i in 1 until points.size) {
            cumDist[i] = cumDist[i - 1] + points[i - 1].distanceTo(points[i])
        }

        val startInstant = params.startDateTime.toInstant(ZoneOffset.UTC)
        val result = mutableListOf<TrackPoint>()
        var currentTime = startInstant
        var lastIdx = 0

        var t = 0L
        while (t <= targetDurationSec) {
            val progress = t.toDouble() / targetDurationSec
            val targetDist = progress * totalDistM

            while (lastIdx < cumDist.size - 1 && cumDist[lastIdx + 1] < targetDist) {
                lastIdx++
            }
            val segStart = lastIdx
            val segEnd = min(lastIdx + 1, points.size - 1)
            val segLen = cumDist[segEnd] - cumDist[segStart]
            val frac = if (segLen > 0) (targetDist - cumDist[segStart]) / segLen else 0.0

            val p1 = points[segStart]
            val p2 = points[segEnd]
            val lat = p1.lat + (p2.lat - p1.lat) * frac
            val lon = p1.lon + (p2.lon - p1.lon) * frac
            val ele = if (p1.elevation != null && p2.elevation != null) {
                p1.elevation + (p2.elevation - p1.elevation) * frac
            } else p1.elevation ?: p2.elevation

            result.add(TrackPoint(lat = lat, lon = lon, elevation = ele, time = currentTime))

            currentTime = currentTime.plusSeconds(interval.toLong())
            t += interval
        }

        if (result.isNotEmpty() && points.isNotEmpty()) {
            val last = points.last()
            result[result.lastIndex] = last.copy(time = startInstant.plusSeconds(targetDurationSec))
        }

        return result
    }
}
