package com.cse.dashnav.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * A work day runs from [DEFAULT_CUTOFF_HOUR] (4 AM) local time to the same hour the next morning,
 * so a dash that runs past midnight stays on the evening it belongs to. A dash belongs to the work
 * day its start time falls in and is never split.
 */
object WorkDay {
    const val DEFAULT_CUTOFF_HOUR = 4

    fun of(epochMs: Long, zone: ZoneId, cutoffHour: Int = DEFAULT_CUTOFF_HOUR): LocalDate =
        // Local-time arithmetic, so DST changes don't move the boundary by an hour.
        Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDateTime()
            .minusHours(cutoffHour.toLong())
            .toLocalDate()

    /** Start (inclusive) and end (exclusive) of [date]'s work day, in epoch millis. */
    fun bounds(date: LocalDate, zone: ZoneId, cutoffHour: Int = DEFAULT_CUTOFF_HOUR): LongRange {
        val start = date.atTime(cutoffHour, 0).atZone(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atTime(cutoffHour, 0).atZone(zone).toInstant().toEpochMilli()
        return start until end
    }
}
