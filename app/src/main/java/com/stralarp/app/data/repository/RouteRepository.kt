package com.stralarp.app.data.repository

import android.content.Context
import android.net.Uri
import com.stralarp.app.data.gpx.GpxParser
import com.stralarp.app.data.gpx.GpxWriter
import com.stralarp.app.data.model.Route
import com.stralarp.app.data.model.SimulationParams
import com.stralarp.app.data.model.TrackPoint
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

    suspend fun snapWaypoints(waypoints: List<TrackPoint>): List<TrackPoint> {
        return osrm.snapToRoad(waypoints)
    }

    suspend fun nearestPoint(lat: Double, lon: Double): TrackPoint? {
        return osrm.nearest(lat, lon)
    }

    suspend fun routeSegment(from: TrackPoint, to: TrackPoint): List<TrackPoint> {
        return osrm.routeSegment(from, to)
    }

    suspend fun enrichElevation(points: List<TrackPoint>): List<TrackPoint> {
        return elevation.enrichWithElevation(points)
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
