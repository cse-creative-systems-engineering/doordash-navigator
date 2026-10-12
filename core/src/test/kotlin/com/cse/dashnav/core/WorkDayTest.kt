package com.cse.dashnav.core

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class WorkDayTest {
    private val zone = ZoneId.of("America/New_York")

    private fun at(text: String) = LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `late night dash stays on the evening it started`() {
        assertEquals(LocalDate.of(2026, 10, 13), WorkDay.of(at("2026-10-13T22:00"), zone))
        assertEquals(LocalDate.of(2026, 10, 13), WorkDay.of(at("2026-10-14T00:30"), zone))
        assertEquals(LocalDate.of(2026, 10, 13), WorkDay.of(at("2026-10-14T03:59"), zone))
    }

    @Test
    fun `new work day starts at 4 AM`() {
        assertEquals(LocalDate.of(2026, 10, 14), WorkDay.of(at("2026-10-14T04:00"), zone))
        assertEquals(LocalDate.of(2026, 10, 14), WorkDay.of(at("2026-10-14T11:00"), zone))
    }

    @Test
    fun `cutoff is configurable`() {
        assertEquals(LocalDate.of(2026, 10, 13), WorkDay.of(at("2026-10-14T04:30"), zone, cutoffHour = 5))
    }

    @Test
    fun `boundary holds across daylight saving changes`() {
        // Clocks spring forward at 2 AM on 2026-03-08.
        assertEquals(LocalDate.of(2026, 3, 7), WorkDay.of(at("2026-03-08T03:30"), zone))
        assertEquals(LocalDate.of(2026, 3, 8), WorkDay.of(at("2026-03-08T04:00"), zone))
        val shortDay = WorkDay.bounds(LocalDate.of(2026, 3, 7), zone)
        assertEquals(23 * 3_600_000L, shortDay.last + 1 - shortDay.first)
    }

    @Test
    fun `bounds match classification`() {
        val day = LocalDate.of(2026, 10, 13)
        val b = WorkDay.bounds(day, zone)
        assertEquals(day, WorkDay.of(b.first, zone))
        assertEquals(day, WorkDay.of(b.last, zone))
        assertEquals(day.plusDays(1), WorkDay.of(b.last + 1, zone))
    }
}
