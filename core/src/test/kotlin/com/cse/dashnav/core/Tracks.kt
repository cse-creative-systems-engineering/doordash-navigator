package com.cse.dashnav.core

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Synthetic GPS tracks for tests. */
object Tracks {
    const val START_LAT = 40.0
    const val START_LON = -75.0
    private const val METERS_PER_DEG_LAT = 111_195.08

    fun northOffsetLat(meters: Double) = meters / METERS_PER_DEG_LAT

    fun metersPerDegLon(lat: Double) = METERS_PER_DEG_LAT * cos(Math.toRadians(lat))

    /** Driving due north at a constant speed, one fix every [intervalMs]. */
    fun driveNorth(
        startMs: Long,
        durationMs: Long,
        speedMps: Double,
        intervalMs: Long = 3_000,
        startLat: Double = START_LAT,
        lon: Double = START_LON,
        accuracyM: Float = 5f,
    ): List<TrackPoint> = (0..durationMs step intervalMs).map { t ->
        TrackPoint(
            timeMs = startMs + t,
            lat = startLat + northOffsetLat(speedMps * t / 1000.0),
            lon = lon,
            accuracyM = accuracyM,
            speedMps = speedMps.toFloat(),
        )
    }

    /** A parked phone: each fix lands somewhere inside its accuracy circle around the true spot. */
    fun parked(
        startMs: Long,
        durationMs: Long,
        lat: Double = START_LAT,
        lon: Double = START_LON,
        accuracyM: Float = 10f,
        intervalMs: Long = 3_000,
        seed: Int = 42,
    ): List<TrackPoint> {
        val rnd = Random(seed)
        return (0..durationMs step intervalMs).map { t ->
            val r = accuracyM * sqrt(rnd.nextDouble())
            val theta = rnd.nextDouble() * 2 * Math.PI
            TrackPoint(
                timeMs = startMs + t,
                lat = lat + northOffsetLat(r * sin(theta)),
                lon = lon + r * cos(theta) / metersPerDegLon(lat),
                accuracyM = accuracyM,
                speedMps = rnd.nextDouble(0.0, 1.0).toFloat(),
            )
        }
    }
}
