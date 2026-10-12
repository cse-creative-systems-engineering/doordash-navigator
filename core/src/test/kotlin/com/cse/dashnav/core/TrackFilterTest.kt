package com.cse.dashnav.core

import com.cse.dashnav.core.Tracks.START_LAT
import com.cse.dashnav.core.Tracks.START_LON
import com.cse.dashnav.core.Tracks.driveNorth
import com.cse.dashnav.core.Tracks.northOffsetLat
import com.cse.dashnav.core.Tracks.parked
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrackFilterTest {
    @Test
    fun `straight drive measures true distance`() {
        // 30 mph for 10 minutes = 8046.72 m
        val track = TrackFilter.run(driveNorth(0, 600_000, 13.4112))
        assertEquals(8046.72, track.distanceMeters, 8046.72 * 0.002)
        assertTrue(track.gaps.isEmpty())
    }

    @Test
    fun `parked phone adds no distance`() {
        val track = TrackFilter.run(parked(0, 30 * 60_000))
        assertEquals(0.0, track.distanceMeters, 0.001)
    }

    @Test
    fun `parked between two drives only counts the drives`() {
        val leg1 = driveNorth(0, 300_000, 13.4112)
        val stop = leg1.last()
        val wait = parked(stop.timeMs + 3_000, 15 * 60_000, lat = stop.lat, lon = stop.lon, accuracyM = 15f)
        val leg2 = driveNorth(wait.last().timeMs + 3_000, 300_000, 13.4112, startLat = stop.lat)
        val track = TrackFilter.run(leg1 + wait + leg2)
        assertEquals(2 * 4023.36, track.distanceMeters, 2 * 4023.36 * 0.01)
    }

    @Test
    fun `slow crawl without a speed reading still adds up`() {
        val crawl = driveNorth(0, 200_000, 1.0).map { it.copy(speedMps = null) }
        val track = TrackFilter.run(crawl)
        assertTrue(track.distanceMeters in 180.0..200.5, "got ${track.distanceMeters}")
    }

    @Test
    fun `inaccurate fixes are ignored`() {
        val drive = driveNorth(0, 120_000, 13.4112)
        val noisy = drive.mapIndexed { i, p ->
            if (i % 5 == 2) p.copy(lon = p.lon + 0.01, accuracyM = 200f) else p
        }
        val track = TrackFilter.run(noisy)
        assertEquals(TrackFilter.run(drive).distanceMeters, track.distanceMeters, 1.0)
        assertTrue(track.points.count { it.status == PointStatus.REJECTED_ACCURACY } > 0)
    }

    @Test
    fun `single GPS jump is rejected`() {
        val drive = driveNorth(0, 120_000, 13.4112).toMutableList()
        drive[20] = drive[20].copy(lat = drive[20].lat + northOffsetLat(2_000.0))
        val track = TrackFilter.run(drive)
        assertEquals(PointStatus.REJECTED_JUMP, track.points[20].status)
        assertEquals(13.4112 * 120, track.distanceMeters, 5.0)
    }

    @Test
    fun `bad first fix is replaced once the real track is consistent`() {
        val bad = TrackPoint(0, START_LAT + northOffsetLat(5_000.0), START_LON, 8f, 0f)
        val drive = driveNorth(3_000, 120_000, 13.4112)
        val track = TrackFilter.run(listOf(bad) + drive)
        // The first two real fixes are rejected while the filter waits for agreement, then it re-anchors.
        val expected = 13.4112 * (120 - 3 * 2)
        assertEquals(expected, track.distanceMeters, 5.0)
        assertEquals(1, track.gaps.size)
    }

    @Test
    fun `gap in data is recorded and bridged with a straight line`() {
        val leg1 = driveNorth(0, 60_000, 13.4112)
        val resumeLat = leg1.last().lat + northOffsetLat(3_000.0)
        val leg2 = driveNorth(leg1.last().timeMs + 300_000, 60_000, 13.4112, startLat = resumeLat)
        val track = TrackFilter.run(leg1 + leg2)
        assertEquals(1, track.gaps.size)
        assertEquals(3_000.0, track.gaps[0].bridgedMeters, 1.0)
        assertEquals(2 * 13.4112 * 60 + 3_000, track.distanceMeters, 5.0)
    }

    @Test
    fun `live and batch filtering agree`() {
        val points = driveNorth(0, 120_000, 13.4112) + parked(123_000, 60_000)
        val live = TrackFilter()
        points.forEach(live::add)
        assertEquals(TrackFilter.run(points).distanceMeters, live.distanceMeters, 0.0)
    }
}
