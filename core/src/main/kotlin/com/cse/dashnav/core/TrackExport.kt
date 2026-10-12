package com.cse.dashnav.core

import java.time.Instant

/** A dash's raw points with a label, for export. */
data class ExportDash(val name: String, val points: List<TrackPoint>)

/** Raw-point exports, so real drives can be inspected in other tools or replayed through the filter. */
object TrackExport {
    fun gpx(dashes: List<ExportDash>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        append("""<gpx version="1.1" creator="DashNav" xmlns="http://www.topografix.com/GPX/1/1">""").append('\n')
        for (dash in dashes) {
            append("  <trk><name>").append(escapeXml(dash.name)).append("</name><trkseg>\n")
            for (p in dash.points) {
                append("    <trkpt lat=\"").append(p.lat).append("\" lon=\"").append(p.lon).append("\">")
                p.altitudeM?.let { append("<ele>").append(it).append("</ele>") }
                append("<time>").append(Instant.ofEpochMilli(p.timeMs)).append("</time>")
                append("</trkpt>\n")
            }
            append("  </trkseg></trk>\n")
        }
        append("</gpx>\n")
    }

    fun csv(dashes: List<ExportDash>): String = buildString {
        append("dash,time_utc,epoch_ms,lat,lon,accuracy_m,speed_mps,bearing_deg,altitude_m\n")
        for (dash in dashes) {
            for (p in dash.points) {
                append(csvField(dash.name)).append(',')
                append(Instant.ofEpochMilli(p.timeMs)).append(',')
                append(p.timeMs).append(',')
                append(p.lat).append(',')
                append(p.lon).append(',')
                append(p.accuracyM).append(',')
                append(p.speedMps ?: "").append(',')
                append(p.bearingDeg ?: "").append(',')
                append(p.altitudeM ?: "").append('\n')
            }
        }
    }

    private fun escapeXml(s: String) = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun csvField(s: String) =
        if (s.any { it == ',' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
}
