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
                        result.add(
                            if (!ele.isNaN()) p.copy(elevation = ele) else p
                        )
                    }
                }
            } catch (e: Exception) {
                result.addAll(batch)
            }
        }

        interpolateMissingElevations(result)
    }

    private fun interpolateMissingElevations(points: List<TrackPoint>): List<TrackPoint> {
        if (points.size < 2) return points
        val out = points.toMutableList()
        var lastKnown: Double? = null

        for (i in out.indices) {
            val ele = out[i].elevation
            if (ele != null) {
                lastKnown = ele
            } else if (lastKnown != null) {
                var nextKnown: Double? = null
                var nextIdx = -1
                for (j in i + 1 until out.size) {
                    if (out[j].elevation != null) {
                        nextKnown = out[j].elevation
                        nextIdx = j
                        break
                    }
                }
                if (nextKnown != null) {
                    val fraction = (i - (i - 1).coerceAtLeast(0)).toDouble() /
                            (nextIdx - (i - 1).coerceAtLeast(0)).toDouble()
                    val interpolated = lastKnown + (nextKnown - lastKnown) * fraction
                    out[i] = out[i].copy(elevation = interpolated)
                } else {
                    out[i] = out[i].copy(elevation = lastKnown)
                }
            }
        }
        return out
    }
}
