package com.stralarp.app.data.gpx

import com.stralarp.app.data.model.Route
import java.io.OutputStream
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class GpxWriter {

    private val isoFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
        .withZone(ZoneOffset.UTC)

    fun write(route: Route, outputStream: OutputStream) {
        val writer = outputStream.bufferedWriter()
        writer.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        writer.appendLine(
            """<gpx version="1.1" creator="${escape(route.creator)}" """ +
                    """xmlns="http://www.topografix.com/GPX/1/1" """ +
                    """xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" """ +
                    """xsi:schemaLocation="http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd">"""
        )
        writer.appendLine("  <metadata>")
        writer.appendLine("    <name>${escape(route.name)}</name>")
        if (route.description.isNotBlank()) {
            writer.appendLine("    <desc>${escape(route.description)}</desc>")
        }
        writer.appendLine("  </metadata>")
        writer.appendLine("  <trk>")
        writer.appendLine("    <name>${escape(route.name)}</name>")
        writer.appendLine("    <type>${escape(route.activityType)}</type>")
        writer.appendLine("    <trkseg>")

        for (p in route.points) {
            writer.append("      <trkpt lat=\"\( {p.lat}\" lon=\" \){p.lon}\">")
            if (p.elevation != null) {
                writer.append("<ele>${"%.2f".format(p.elevation)}</ele>")
            }
            if (p.time != null) {
                writer.append("<time>${isoFormatter.format(p.time)}</time>")
            }
            writer.appendLine("</trkpt>")
        }

        writer.appendLine("    </trkseg>")
        writer.appendLine("  </trk>")
        writer.appendLine("</gpx>")
        writer.flush()
    }

    private fun escape(s: String): String =
        s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
}
