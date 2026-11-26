package com.stralarp.app.data.repository

import android.content.Context
import android.net.Uri
import com.stralarp.app.data.gpx.GpxParser
import com.stralarp.app.data.gpx.GpxWriter
import com.stralarp.app.data.model.Route
import com.stralarp.app.data.model.SimulationParams
import com.stralarp.app.data.model.TrackPoint
import com.stralarp.app.data.network.AutoRouteGenerator
import com.stralarp.app.data.network.ElevationClient
import com.stralarp.app.data.network.OsrmClient
import com.stralarp.app.data.simulation.RouteSimulator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class RouteRepository(private val context: Context) {

    private val osrm = OsrmClient()
    private val elevation = ElevationClient()
    private val simulator = RouteSimulator()
    private val parser = GpxParser()
    private val writer = GpxWriter()
    private val autoGen = AutoRouteGenerator(osrm)

    suspend fun snapWaypoints(waypoints: List<TrackPoint>): List<TrackPoint> {
        return osrm.snapToRoad(waypoints)
    }

    suspend fun nearestPoint(lat: Double, lon: Double): TrackPoint? {
        return osrm.nearest(lat, lon)
    }

    suspend fun routeSegment(from: TrackPoint, to: TrackPoint): List<TrackPoint> {
        return osrm.routeSegment(from, to)
    }

    /**
     * After moving or inserting a point at [index], rebuild the road geometry
     * for the neighboring segments so the path stays on roads.
     * [points] must already contain the new/moved point at [index].
     */
    suspend fun rebuildAroundIndex(
        points: List<TrackPoint>,
        index: Int,
        snap: Boolean
    ): List<TrackPoint> {
        if (points.isEmpty() || index !in points.indices) return points
        if (!snap || points.size < 2) return points

        val result = mutableListOf<TrackPoint>()

        // Keep everything before the previous neighbor
        val prevIdx = index - 1
        if (prevIdx < 0) {
            // First point moved/inserted: route from this point to next only
            result.add(points[0])
            if (points.size >= 2) {
                val seg = osrm.routeSegment(points[0], points[1])
                if (seg.size >= 2) {
                    result.clear()
                    result.addAll(seg)
                } else {
                    result.addAll(points.drop(1))
                }
                // Keep rest after index+1 unchanged
                if (points.size > 2) {
                    result.addAll(points.drop(2))
                }
            }
            return densifyOrKeep(result)
        }

        // Copy points up to (but not including) prev
        result.addAll(points.subList(0, prevIdx))

        val prev = points[prevIdx]
        val current = points[index]
        val nextIdx = index + 1

        // Segment prev -> current
        val seg1 = osrm.routeSegment(prev, current)
        if (seg1.size >= 2) {
            result.addAll(seg1)
        } else {
            result.add(prev)
            result.add(current)
        }

        // Segment current -> next (if any)
        if (nextIdx < points.size) {
            val next = points[nextIdx]
            // Avoid duplicating current (already last of seg1)
            val from = result.lastOrNull() ?: current
            val seg2 = osrm.routeSegment(from, next)
            if (seg2.size >= 2) {
                result.addAll(seg2.drop(1))
            } else {
                result.add(next)
            }
            // Rest of the route after next
            if (nextIdx + 1 < points.size) {
                result.addAll(points.subList(nextIdx + 1, points.size))
            }
        }

        return densifyOrKeep(result)
    }

    private fun densifyOrKeep(points: List<TrackPoint>): List<TrackPoint> {
        // Drop near-duplicate consecutive points (< 1.5 m)
        if (points.size < 2) return points
        val cleaned = mutableListOf(points.first())
        for (i in 1 until points.size) {
            if (cleaned.last().distanceTo(points[i]) >= 1.5) {
                cleaned.add(points[i])
            }
        }
        return cleaned
    }

    suspend fun enrichElevation(points: List<TrackPoint>): List<TrackPoint> {
        return elevation.enrichWithElevation(points)
    }

    suspend fun generateAutoRoute(
        centerLat: Double,
        centerLon: Double,
        targetKm: Double,
        loop: Boolean = true,
        activityType: String = "running"
    ): List<TrackPoint> {
        return autoGen.generate(centerLat, centerLon, targetKm, loop, activityType)
    }

    suspend fun parseGpx(uri: Uri): Route = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            parser.parse(stream)
        } ?: Route()
    }

    suspend fun simulate(route: Route, params: SimulationParams): Route {
        return simulator.simulate(route, params)
    }

    suspend fun exportGpx(route: Route, fileName: String): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "gpx")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, fileName)
        FileOutputStream(file).use { fos ->
            writer.write(route, fos)
        }
        file
    }

    suspend fun writeToUri(route: Route, uri: Uri) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri)?.use { os ->
            writer.write(route, os)
        }
    }
}
