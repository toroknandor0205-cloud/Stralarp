package com.stralarp.app.data.gpx

import android.util.Xml
import com.stralarp.app.data.model.Route
import com.stralarp.app.data.model.TrackPoint
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.time.Instant
import java.time.format.DateTimeFormatter

class GpxParser {

    fun parse(inputStream: InputStream): Route {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(inputStream, null)

        var name = "Imported Route"
        var description = ""
        var activityType = "running"
        var creator = "Unknown"
        val points = mutableListOf<TrackPoint>()

        var currentLat: Double? = null
        var currentLon: Double? = null
        var currentEle: Double? = null
        var currentTime: Instant? = null
        var currentHr: Int? = null
        var currentCad: Int? = null
        var inTrkpt = false

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name?.lowercase()) {
                        "gpx" -> {
                            creator = parser.getAttributeValue(null, "creator") ?: creator
                        }
                        "name" -> {
                            if (!inTrkpt) name = parser.nextText()
                        }
                        "desc", "description" -> {
                            if (!inTrkpt) description = parser.nextText()
                        }
                        "type" -> {
                            if (!inTrkpt) activityType = parser.nextText()
                        }
                        "trkpt", "rtept" -> {
                            inTrkpt = true
                            currentLat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull()
                            currentLon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull()
                            currentEle = null
                            currentTime = null
                            currentHr = null
                            currentCad = null
                        }
                        "ele" -> if (inTrkpt) currentEle = parser.nextText().toDoubleOrNull()
                        "time" -> if (inTrkpt) {
                            val raw = parser.nextText()
                            currentTime = try {
                                Instant.parse(raw)
                            } catch (e: Exception) {
                                try {
                                    DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(raw, Instant::from)
                                } catch (e2: Exception) {
                                    null
                                }
                            }
                        }
                        "hr" -> if (inTrkpt) currentHr = parser.nextText().toIntOrNull()
                        "cad" -> if (inTrkpt) currentCad = parser.nextText().toIntOrNull()
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name.equals("trkpt", true) || parser.name.equals("rtept", true)) {
                        if (currentLat != null && currentLon != null) {
                            points.add(
                                TrackPoint(
                                    lat = currentLat!!,
                                    lon = currentLon!!,
                                    elevation = currentEle,
                                    time = currentTime,
                                    heartRate = currentHr,
                                    cadence = currentCad
                                )
                            )
                        }
                        inTrkpt = false
                    }
                }
            }
            eventType = parser.next()
        }

        return Route(
            name = name,
            description = description,
            activityType = activityType,
            creator = creator,
            points = points,
            startTime = points.firstOrNull()?.time
        )
    }
}
