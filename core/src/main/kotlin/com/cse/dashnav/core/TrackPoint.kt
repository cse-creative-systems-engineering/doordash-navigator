package com.cse.dashnav.core

/** One raw GPS fix exactly as the phone reported it. Never modified after recording. */
data class TrackPoint(
    val timeMs: Long,
    val lat: Double,
    val lon: Double,
    /** Horizontal accuracy radius in meters (68% confidence). */
    val accuracyM: Float,
    val speedMps: Float? = null,
    val bearingDeg: Float? = null,
    val altitudeM: Double? = null,
)
