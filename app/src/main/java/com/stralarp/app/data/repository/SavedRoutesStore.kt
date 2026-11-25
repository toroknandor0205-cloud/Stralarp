package com.stralarp.app.data.repository

import android.content.Context
import com.stralarp.app.data.gpx.GpxParser
import com.stralarp.app.data.gpx.GpxWriter
import com.stralarp.app.data.model.Route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID

data class SavedRouteMeta(
    val id: String,
    val name: String,
    val distanceKm: Double,
    val elevationGain: Double,
    val pointCount: Int,
    val activityType: String,
    val savedAt: Long,
    val fileName: String
)

class SavedRoutesStore(private val context: Context) {
    private val dir: File by lazy {
        File(context.filesDir, "saved_routes").also { if (!it.exists()) it.mkdirs() }
    }
    private val indexFile get() = File(dir, "index.json")
    private val writer = GpxWriter()
    private val parser = GpxParser()

    suspend fun list(): List<SavedRouteMeta> = withContext(Dispatchers.IO) {
        readIndex().sortedByDescending { it.savedAt }
    }

    suspend fun save(route: Route): SavedRouteMeta = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val fileName = "$id.gpx"
        val file = File(dir, fileName)
        FileOutputStream(file).use { writer.write(route, it) }
        val meta = SavedRouteMeta(
            id = id,
            name = route.name.ifBlank { "Route" },
            distanceKm = route.totalDistanceKm,
            elevationGain = route.elevationGain,
            pointCount = route.points.size,
            activityType = route.activityType,
            savedAt = System.currentTimeMillis(),
            fileName = fileName
        )
        val all = readIndex().toMutableList()
        all.add(meta)
        writeIndex(all)
        meta
    }

    suspend fun load(id: String): Route? = withContext(Dispatchers.IO) {
        val meta = readIndex().find { it.id == id } ?: return@withContext null
        val file = File(dir, meta.fileName)
        if (!file.exists()) return@withContext null
        FileInputStream(file).use { parser.parse(it) }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val all = readIndex().toMutableList()
        val meta = all.find { it.id == id } ?: return@withContext
        File(dir, meta.fileName).delete()
        all.removeAll { it.id == id }
        writeIndex(all)
    }

    private fun readIndex(): List<SavedRouteMeta> {
        if (!indexFile.exists()) return emptyList()
        return try {
            val arr = JSONArray(indexFile.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                SavedRouteMeta(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    distanceKm = o.getDouble("distanceKm"),
                    elevationGain = o.optDouble("elevationGain", 0.0),
                    pointCount = o.getInt("pointCount"),
                    activityType = o.optString("activityType", "running"),
                    savedAt = o.getLong("savedAt"),
                    fileName = o.getString("fileName")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun writeIndex(list: List<SavedRouteMeta>) {
        val arr = JSONArray()
        list.forEach { m ->
            arr.put(JSONObject().apply {
                put("id", m.id)
                put("name", m.name)
                put("distanceKm", m.distanceKm)
                put("elevationGain", m.elevationGain)
                put("pointCount", m.pointCount)
                put("activityType", m.activityType)
                put("savedAt", m.savedAt)
                put("fileName", m.fileName)
            })
        }
        indexFile.writeText(arr.toString())
    }
}
