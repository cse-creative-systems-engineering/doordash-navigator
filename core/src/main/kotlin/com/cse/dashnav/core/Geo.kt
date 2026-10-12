package com.cse.dashnav.core

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

object Geo {
    const val METERS_PER_MILE = 1609.344
    const val MPS_TO_MPH = 2.2369362920544

    /** Mean Earth radius (IUGG). Haversine error vs. the ellipsoid is well under 0.5%. */
    private const val EARTH_RADIUS_M = 6_371_008.8

    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_M * asin(min(1.0, sqrt(a)))
    }

    fun distanceMeters(a: TrackPoint, b: TrackPoint): Double = distanceMeters(a.lat, a.lon, b.lat, b.lon)

    fun metersToMiles(meters: Double): Double = meters / METERS_PER_MILE
}
