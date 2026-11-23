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

    suspend fun snapToRoad(waypoints: List<TrackPoint>): List<TrackPoint> = withContext(Dispatchers.IO) {
        if (waypoints.size < 2) return@withContext waypoints

        val coords = waypoints.joinToString(";") { "${it.lon},${it.lat}" }
        val url = "https://router.project-osrm.org/route/v1/foot/$coords?overview=full&geometries=geojson&steps=false"

        try {
            val request = Request.Builder().url(url).get().build()
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
                    result.add(TrackPoint(lat = coord.getDouble(1), lon = coord.getDouble(0)))
                }
                result
            }
        } catch (e: Exception) {
            waypoints
        }
    }

    suspend fun nearest(lat: Double, lon: Double): TrackPoint? = withContext(Dispatchers.IO) {
        val url = "https://router.project-osrm.org/nearest/v1/foot/$lon,$lat"
        try {
            val request = Request.Builder().url(url).get().build()
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
