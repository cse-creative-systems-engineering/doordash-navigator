package com.cse.dashnav.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrackExportTest {
    private val points = Tracks.driveNorth(0, 9_000, 10.0)

    @Test
    fun `gpx has one trkpt per raw point`() {
        val gpx = TrackExport.gpx(listOf(ExportDash("Dash <1>", points)))
        assertEquals(points.size, Regex("<trkpt ").findAll(gpx).count())
        assertTrue("Dash &lt;1&gt;" in gpx)
        assertTrue("<time>1970-01-01T00:00:00Z</time>" in gpx)
    }

    @Test
    fun `csv has a header and one row per point`() {
        val csv = TrackExport.csv(listOf(ExportDash("Dash, 1", points)))
        val lines = csv.trimEnd().lines()
        assertEquals(points.size + 1, lines.size)
        assertTrue(lines[1].startsWith("\"Dash, 1\","))
    }
}
