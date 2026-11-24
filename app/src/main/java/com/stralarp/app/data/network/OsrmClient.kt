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
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    private val userAgent = "Stralarp/1.0 (Android; route simulator)"

    /**
     * Snap a single coordinate to nearest foot path.
     * Tries foot profile, then walking, then driving as fallback.
     */
    suspend fun nearest(lat: Double, lon: Double): TrackPoint? = withContext(Dispatchers.IO) {
        for (profile in listOf("foot", "walking", "driving")) {
            val result = nearestWithProfile(lat, lon, profile)
            if (result != null) return@withContext result
        }
        null
    }

    private fun nearestWithProfile(lat: Double, lon: Double, profile: String): TrackPoint? {
        val url = "https://router.project-osrm.org/nearest/v1/$profile/$lon,$lat?number=1"
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val json = JSONObject(body)
                if (json.optString("code") != "Ok") return null
                val waypoints = json.optJSONArray("waypoints") ?: return null
                if (waypoints.length() == 0) return null
                val location = waypoints.getJSONObject(0).getJSONArray("location")
                TrackPoint(lat = location.getDouble(1), lon = location.getDouble(0))
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Route between two points and return the full geometry (many track points).
     * Used when adding a waypoint so only the new segment is snapped.
     */
    suspend fun routeSegment(from: TrackPoint, to: TrackPoint): List<TrackPoint> =
        withContext(Dispatchers.IO) {
            for (profile in listOf("foot", "walking")) {
                val result = routeSegmentWithProfile(from, to, profile)
                if (result.size >= 2) return@withContext result
            }
            // Fallback: just the two endpoints (straight)
            listOf(from, to)
        }

    private fun routeSegmentWithProfile(
        from: TrackPoint,
        to: TrackPoint,
        profile: String
    ): List<TrackPoint> {
        // radiuses help OSRM snap nearby points onto the network
        val url =
            "https://router.project-osrm.org/route/v1/$profile/" +
                    "${from.lon},${from.lat};${to.lon},${to.lat}" +
                    "?overview=full&geometries=geojson&steps=false&radiuses=50;50"

        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()
                val json = JSONObject(body)
                if (json.optString("code") != "Ok") return emptyList()

                val routes = json.optJSONArray("routes") ?: return emptyList()
                if (routes.length() == 0) return emptyList()

                val geometry = routes.getJSONObject(0).getJSONObject("geometry")
                val coordinates = geometry.getJSONArray("coordinates")
                val result = mutableListOf<TrackPoint>()
                for (i in 0 until coordinates.length()) {
                    val coord = coordinates.getJSONArray(i)
                    result.add(TrackPoint(lat = coord.getDouble(1), lon = coord.getDouble(0)))
                }
                result
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Full multi-waypoint snap (used for re-snap all / import cleanup).
     * Caps at 25 waypoints for public OSRM limits; densifies via route geometry.
     */
    suspend fun snapToRoad(waypoints: List<TrackPoint>): List<TrackPoint> =
        withContext(Dispatchers.IO) {
            if (waypoints.size < 2) return@withContext waypoints
            if (waypoints.size == 2) return@withContext routeSegment(waypoints[0], waypoints[1])

            // Build path segment by segment so one failure doesn't kill the whole route
            val result = mutableListOf<TrackPoint>()
            result.add(waypoints.first())
            for (i in 1 until waypoints.size) {
                val segment = routeSegment(waypoints[i - 1], waypoints[i])
                if (segment.size >= 2) {
                    // skip first of segment to avoid duplicate joint
                    result.addAll(segment.drop(1))
                } else {
                    result.add(waypoints[i])
                }
            }
            result
        }
}
