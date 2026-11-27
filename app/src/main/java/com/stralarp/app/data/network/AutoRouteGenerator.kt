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
 * Tries several shapes and radius scales, keeps the closest match.
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
        var radiusM = if (loop) targetMeters / (2 * PI) else targetMeters / 2.0
        // Roads are longer than straight lines
        radiusM *= 0.78

        var best: List<TrackPoint> = emptyList()
        var bestDiff = Double.MAX_VALUE

        val shapes = if (loop) listOf("circle", "ellipse", "rounded", "figure8") else listOf("outback")

        // Up to 5 radius iterations × shape variants
        repeat(5) { attempt ->
            for (shape in shapes) {
                val waypoints = when (shape) {
                    "ellipse" -> ellipseWaypoints(centerLat, centerLon, radiusM, radiusM * 0.72)
                    "rounded" -> roundedSquareWaypoints(centerLat, centerLon, radiusM)
                    "figure8" -> figure8Waypoints(centerLat, centerLon, radiusM * 0.55)
                    "outback" -> outAndBackWaypoints(centerLat, centerLon, radiusM)
                    else -> circleWaypoints(centerLat, centerLon, radiusM, waypointCount(targetKm))
                }

                val snapped = snapWaypoints(waypoints)
                if (snapped.size < 3) continue

                val routed = routeAlong(snapped, closeLoop = loop)
                if (routed.size < 4) continue

                val dist = totalDistance(routed)
                val diff = kotlin.math.abs(dist - targetMeters)
                if (diff < bestDiff) {
                    bestDiff = diff
                    best = routed
                }
                // Good enough: within 8%
                if (diff / targetMeters < 0.08) {
                    return@withContext best
                }
            }

            if (best.isNotEmpty()) {
                val dist = totalDistance(best)
                if (dist > 0) {
                    radiusM *= (targetMeters / dist).coerceIn(0.60, 1.60)
                }
            } else {
                radiusM *= 1.08
            }
        }

        best
    }

    private fun waypointCount(targetKm: Double): Int = when {
        targetKm <= 4 -> 7
        targetKm <= 10 -> 8
        targetKm <= 20 -> 10
        else -> 12
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
            val r = radiusM * (0.90 + Random.nextDouble() * 0.18)
            points.add(destinationPoint(lat, lon, r, angle))
        }
        return points
    }

    private fun ellipseWaypoints(
        lat: Double,
        lon: Double,
        radiusAM: Double,
        radiusBM: Double
    ): List<TrackPoint> {
        val count = 8
        val start = Random.nextDouble(0.0, 2 * PI)
        val points = mutableListOf<TrackPoint>()
        for (i in 0 until count) {
            val t = start + 2 * PI * i / count
            // Approximate ellipse in local frame
            val dx = radiusAM * cos(t)
            val dy = radiusBM * sin(t)
            val r = kotlin.math.sqrt(dx * dx + dy * dy)
            val bearing = atan2(dx, dy) // note: lon/lat orientation via destination
            points.add(destinationPoint(lat, lon, r, bearing))
        }
        return points
    }

    private fun roundedSquareWaypoints(
        lat: Double,
        lon: Double,
        radiusM: Double
    ): List<TrackPoint> {
        val start = Random.nextDouble(0.0, 2 * PI)
        // 8 points around a rounded square (corners pushed out slightly)
        val angles = listOf(0.0, 0.35, PI / 2, PI / 2 + 0.35, PI, PI + 0.35, 3 * PI / 2, 3 * PI / 2 + 0.35)
        return angles.map { a ->
            val angle = start + a
            val cornerBoost = if ((a / (PI / 4)).toInt() % 2 == 0) 1.08 else 0.92
            destinationPoint(lat, lon, radiusM * cornerBoost, angle)
        }
    }

    private fun figure8Waypoints(
        lat: Double,
        lon: Double,
        lobeRadiusM: Double
    ): List<TrackPoint> {
        val bearing = Random.nextDouble(0.0, 2 * PI)
        val lobeA = destinationPoint(lat, lon, lobeRadiusM, bearing)
        val lobeB = destinationPoint(lat, lon, lobeRadiusM, bearing + PI)
        val midA1 = destinationPoint(lat, lon, lobeRadiusM * 0.7, bearing + 0.6)
        val midA2 = destinationPoint(lat, lon, lobeRadiusM * 0.7, bearing - 0.6)
        val midB1 = destinationPoint(lat, lon, lobeRadiusM * 0.7, bearing + PI + 0.6)
        val midB2 = destinationPoint(lat, lon, lobeRadiusM * 0.7, bearing + PI - 0.6)
        return listOf(
            TrackPoint(lat, lon),
            midA1, lobeA, midA2,
            TrackPoint(lat, lon),
            midB1, lobeB, midB2
        )
    }

    private fun outAndBackWaypoints(
        lat: Double,
        lon: Double,
        halfDistanceM: Double
    ): List<TrackPoint> {
        val bearing = Random.nextDouble(0.0, 2 * PI)
        val far = destinationPoint(lat, lon, halfDistanceM, bearing)
        val mid1 = destinationPoint(lat, lon, halfDistanceM * 0.35, bearing + 0.12)
        val mid2 = destinationPoint(lat, lon, halfDistanceM * 0.7, bearing - 0.08)
        return listOf(TrackPoint(lat, lon), mid1, mid2, far)
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
