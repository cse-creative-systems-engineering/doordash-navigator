package com.cse.dashnav.core

import com.cse.dashnav.core.Tracks.driveNorth
import com.cse.dashnav.core.Tracks.parked
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ReplayTimelineTest {
    private val hour = 3_600_000L

    private val dash1 = TrackFilter.run(driveNorth(0, 600_000, 13.4112))
    private val dash2 = TrackFilter.run(driveNorth(3 * hour, 600_000, 13.4112, startLat = 40.1))

    @Test
    fun `gap between dashes is skipped`() {
        val timeline = ReplayTimeline(listOf(dash1, dash2))
        assertTrue(timeline.durationMs < 1_300_000, "duration ${timeline.durationMs}")
        assertEquals(2, timeline.dashStartPlayMs.size)
    }

    @Test
    fun `real time playback keeps the gap`() {
        val timeline = ReplayTimeline(listOf(dash1, dash2), skipIdle = false)
        assertEquals(3 * hour + 600_000, timeline.durationMs)
    }

    @Test
    fun `day distance accumulates across dashes`() {
        val timeline = ReplayTimeline(listOf(dash1, dash2))
        val end = assertNotNull(timeline.stateAt(timeline.durationMs))
        assertEquals(dash1.distanceMeters + dash2.distanceMeters, end.dayMeters, 0.5)
        assertEquals(1, end.dashIndex)
    }

    @Test
    fun `marker interpolates between fixes`() {
        val timeline = ReplayTimeline(listOf(dash1))
        val s = assertNotNull(timeline.stateAt(301_500))
        assertEquals(13.4112 * 301.5, s.dayMeters, 1.0)
        assertEquals(13.4112, s.speedMps, 0.01)
        assertEquals(301_500L, s.realTimeMs)
    }

    @Test
    fun `marker holds still while parked`() {
        val drive = driveNorth(0, 60_000, 13.4112)
        val stop = drive.last()
        val wait = parked(63_000, 600_000, lat = stop.lat, lon = stop.lon)
        val timeline = ReplayTimeline(listOf(TrackFilter.run(drive + wait)), skipIdle = false)
        val a = assertNotNull(timeline.stateAt(200_000))
        val b = assertNotNull(timeline.stateAt(500_000))
        assertEquals(a.lat, b.lat, 0.0)
        assertEquals(a.lon, b.lon, 0.0)
        assertTrue(b.isIdle)
    }

    @Test
    fun `empty day has no state`() {
        assertEquals(null, ReplayTimeline(emptyList()).stateAt(0))
    }
}
