package com.cse.dashnav.ui

import com.cse.dashnav.core.Geo
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Format {
    private val clockFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
    private val clockSecondsFormat = DateTimeFormatter.ofPattern("h:mm:ss a", Locale.US)
    private val dayFormat = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)

    fun miles(meters: Double): String = String.format(Locale.US, "%.1f mi", Geo.metersToMiles(meters))

    fun mph(mps: Double): String = String.format(Locale.US, "%.0f mph", mps * Geo.MPS_TO_MPH)

    fun duration(ms: Long): String {
        val totalMinutes = ms / 60_000
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }

    fun clock(epochMs: Long, zone: ZoneId, seconds: Boolean = false): String =
        Instant.ofEpochMilli(epochMs).atZone(zone).format(if (seconds) clockSecondsFormat else clockFormat)

    fun day(date: LocalDate): String = date.format(dayFormat)
}
