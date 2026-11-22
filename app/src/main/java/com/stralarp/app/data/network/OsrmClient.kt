package com.stralarp.app.data.network

import com.stralarp.app.data.model.TrackPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class OsrmClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Snaps a sequence of waypoints to the nearest foot path using OSRM.
     */
    suspend fun snapToRoad(waypoints: List<TrackPoint>): List<TrackPoint> = withContext(Dispatchers.IO) {
        if (waypoints.size < 2) return@withContext waypoints

        val coords = waypoints.joinToString(";") { "\( {it.lon}, \){it.lat}" }
        val url = "https://router.project-osrm.org/route/v1/foot/$coords" +
                "?overview=full&geometries=geojson&steps=false"

        val request = Request.Builder().url(url).get().build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext waypoints

                val body = response.body?.string() ?: return@withContext waypoints
                val json = JSONObject(body)
                if (json.optString("code") != "Ok") return@withContext waypoints

                val routes = json.getJSONArray("routes")
                if (routes.length() == 0) return@withContext waypoints

                val geometry = routes.getJSONObject(0).getJSONObject("geometry")
                val coordinates = geometry.getJSONArray("coordinates")

                val result = mutableListOf<TrackPoint>()
                for (i in 0 until coordinates.length()) {
                    val coord = coordinates.getJSONArray(i)
                    val lon = coord.getDouble(0)
                    val lat = coord.getDouble(1)
                    result.add(TrackPoint(lat = lat, lon = lon))
                }
                result
            }
        } catch (e: Exception) {
            waypoints
        }
    }

    /**
     * Nearest point on road network for a single coordinate.
     */
    suspend fun nearest(lat: Double, lon: Double): TrackPoint? = withContext(Dispatchers.IO) {
        val url = "https://router.project-osrm.org/nearest/v1/foot/$lon,$lat"
        val request = Request.Builder().url(url).get().build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                if (json.optString("code") != "Ok") return@withContext null
                val waypoints = json.getJSONArray("waypoints")
                if (waypoints.length() == 0) return@withContext null
                val location = waypoints.getJSONObject(0).getJSONArray("location")
                TrackPoint(lat = location.getDouble(1), lon = location.getDouble(0))
            }
        } catch (e: Exception) {
            null
        }
    }
}
