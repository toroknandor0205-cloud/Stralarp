package com.stralarp.app.data.network

import com.stralarp.app.data.model.TrackPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ElevationClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    suspend fun enrichWithElevation(points: List<TrackPoint>): List<TrackPoint> = withContext(Dispatchers.IO) {
        if (points.isEmpty()) return@withContext points

        val batchSize = 90
        val result = mutableListOf<TrackPoint>()

        for (i in points.indices step batchSize) {
            val batch = points.subList(i, minOf(i + batchSize, points.size))
            val lats = batch.joinToString(",") { it.lat.toString() }
            val lons = batch.joinToString(",") { it.lon.toString() }
            val url = "https://api.open-meteo.com/v1/elevation?latitude=$lats&longitude=$lons"

            try {
                val request = Request.Builder().url(url).get().build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        result.addAll(batch)
                        return@use
                    }
                    val body = response.body?.string() ?: run {
                        result.addAll(batch)
                        return@use
                    }
                    val json = JSONObject(body)
                    val elevations = json.getJSONArray("elevation")
                    for (j in 0 until elevations.length()) {
                        val ele = elevations.optDouble(j, Double.NaN)
                        val p = batch[j]
                        result.add(if (!ele.isNaN()) p.copy(elevation = ele) else p)
                    }
                }
            } catch (e: Exception) {
                result.addAll(batch)
            }
        }
        result
    }
}
