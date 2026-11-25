package com.stralarp.app.data.network

import com.stralarp.app.data.model.TrackPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Generates an approximate loop (or out-and-back) of target distance
 * around a center point, snapped to roads via OSRM.
 */
class AutoRouteGenerator(private val osrm: OsrmClient) {

    suspend fun generate(
        centerLat: Double,
        centerLon: Double,
        targetKm: Double,
        loop: Boolean = true,
        activityType: String = "running"
    ): List<TrackPoint> = withContext(Dispatchers.IO) {
        val targetMeters = (targetKm * 1000.0).coerceIn(500.0, 50_000.0)
        // Circumference ≈ 2πr  →  r = target / 2π
        var radiusM = if (loop) targetMeters / (2 * PI) else targetMeters / 2.0
        // Roads are longer than straight lines – start a bit smaller
        radiusM *= 0.82

        var best: List<TrackPoint> = emptyList()
        var bestDiff = Double.MAX_VALUE

        // Up to 3 attempts with radius adjustment
        repeat(3) { attempt ->
            val waypoints = if (loop) {
                circleWaypoints(centerLat, centerLon, radiusM, count = waypointCount(targetKm))
            } else {
                outAndBackWaypoints(centerLat, centerLon, radiusM)
            }

            val snapped = snapWaypoints(waypoints)
            if (snapped.size < 3) return@repeat

            val routed = routeAlong(snapped, closeLoop = loop)
            if (routed.size < 4) return@repeat

            val dist = totalDistance(routed)
            val diff = kotlin.math.abs(dist - targetMeters)
            if (diff < bestDiff) {
                bestDiff = diff
                best = routed
            }

            // Adjust radius for next try
            if (dist > 0) {
                val scale = targetMeters / dist
                radiusM *= scale.coerceIn(0.65, 1.55)
            }
        }

        best
    }

    private fun waypointCount(targetKm: Double): Int = when {
        targetKm <= 4 -> 6
        targetKm <= 10 -> 7
        targetKm <= 20 -> 8
        else -> 10
    }

    private fun circleWaypoints(
        lat: Double,
        lon: Double,
        radiusM: Double,
        count: Int
    ): List<TrackPoint> {
        val points = mutableListOf<TrackPoint>()
        val startAngle = Random.nextDouble(0.0, 2 * PI)
        for (i in 0 until count) {
            val angle = startAngle + 2 * PI * i / count
            // Slight radius jitter so it's not a perfect circle
            val r = radiusM * (0.88 + Random.nextDouble() * 0.24)
            points.add(destinationPoint(lat, lon, r, angle))
        }
        return points
    }

    private fun outAndBackWaypoints(
        lat: Double,
        lon: Double,
        halfDistanceM: Double
    ): List<TrackPoint> {
        val bearing = Random.nextDouble(0.0, 2 * PI)
        val far = destinationPoint(lat, lon, halfDistanceM, bearing)
        // A couple of intermediate points for nicer shape
        val mid1 = destinationPoint(lat, lon, halfDistanceM * 0.35, bearing + 0.15)
        val mid2 = destinationPoint(lat, lon, halfDistanceM * 0.7, bearing - 0.1)
        return listOf(
            TrackPoint(lat, lon),
            mid1,
            mid2,
            far
        )
    }

    private suspend fun snapWaypoints(raw: List<TrackPoint>): List<TrackPoint> {
        val snapped = mutableListOf<TrackPoint>()
        for (p in raw) {
            val n = osrm.nearest(p.lat, p.lon)
            snapped.add(n ?: p)
        }
        return snapped
    }

    private suspend fun routeAlong(
        waypoints: List<TrackPoint>,
        closeLoop: Boolean
    ): List<TrackPoint> {
        if (waypoints.isEmpty()) return emptyList()
        val result = mutableListOf<TrackPoint>()
        result.add(waypoints.first())

        val path = if (closeLoop) waypoints + waypoints.first() else waypoints
        for (i in 1 until path.size) {
            val seg = osrm.routeSegment(path[i - 1], path[i])
            if (seg.size >= 2) {
                result.addAll(seg.drop(1))
            } else {
                result.add(path[i])
            }
        }
        return result
    }

    private fun totalDistance(points: List<TrackPoint>): Double {
        if (points.size < 2) return 0.0
        var d = 0.0
        for (i in 1 until points.size) {
            d += points[i - 1].distanceTo(points[i])
        }
        return d
    }

    /** Destination point given distance (m) and bearing (radians). */
    private fun destinationPoint(
        lat: Double,
        lon: Double,
        distanceM: Double,
        bearingRad: Double
    ): TrackPoint {
        val R = 6371000.0
        val lat1 = Math.toRadians(lat)
        val lon1 = Math.toRadians(lon)
        val angDist = distanceM / R

        val lat2 = asin(
            sin(lat1) * cos(angDist) +
                cos(lat1) * sin(angDist) * cos(bearingRad)
        )
        val lon2 = lon1 + atan2(
            sin(bearingRad) * sin(angDist) * cos(lat1),
            cos(angDist) - sin(lat1) * sin(lat2)
        )
        return TrackPoint(
            lat = Math.toDegrees(lat2),
            lon = Math.toDegrees(lon2)
        )
    }
}
