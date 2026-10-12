package com.cse.dashnav.data

import com.cse.dashnav.core.ExportDash
import com.cse.dashnav.core.FilteredTrack
import com.cse.dashnav.core.TrackFilter
import com.cse.dashnav.core.TrackPoint
import com.cse.dashnav.core.WorkDay
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZoneId

/** Totals cover finished dashes only; the UI adds the live dash so the list doesn't re-read points every fix. */
data class DaySummary(
    val date: LocalDate,
    val dashCount: Int,
    val finishedDistanceMeters: Double,
    val finishedActiveMs: Long,
    /** Start time of the dash still in progress on this day, if any. */
    val activeDashStartMs: Long?,
)

data class DashTrack(val dash: DashEntity, val track: FilteredTrack)

class DashRepository(private val dao: DashDao) {

    suspend fun getActiveDash(): DashEntity? = dao.getActiveDash()

    /** Starts a dash, or returns the one already in progress so there is never more than one. */
    suspend fun startDash(nowMs: Long = System.currentTimeMillis()): DashEntity {
        dao.getActiveDash()?.let { return it }
        val dash = DashEntity(startTimeMs = nowMs, zoneId = ZoneId.systemDefault().id)
        return dash.copy(id = dao.insertDash(dash))
    }

    suspend fun endDash(dashId: Long, nowMs: Long = System.currentTimeMillis()) {
        val dash = dao.getDash(dashId) ?: return
        val track = filteredTrack(dashId)
        dao.updateDash(
            dash.copy(
                endTimeMs = nowMs,
                cachedDistanceMeters = track.distanceMeters,
                cachedFilterVersion = TrackFilter.ALGORITHM_VERSION,
            ),
        )
    }

    suspend fun recordPoint(dashId: Long, point: TrackPoint, isMock: Boolean) {
        dao.insertPoint(
            TrackPointEntity(
                dashId = dashId,
                timeMs = point.timeMs,
                lat = point.lat,
                lon = point.lon,
                accuracyM = point.accuracyM,
                speedMps = point.speedMps,
                bearingDeg = point.bearingDeg,
                altitudeM = point.altitudeM,
                isMock = isMock,
            ),
        )
    }

    suspend fun rawPoints(dashId: Long): List<TrackPoint> = dao.getPoints(dashId).map { it.toCore() }

    suspend fun filteredTrack(dashId: Long): FilteredTrack = TrackFilter.run(rawPoints(dashId))

    /** Distance from the cache when it was computed by the current filter, otherwise recomputed and re-cached. */
    suspend fun distanceMeters(dash: DashEntity): Double {
        val cached = dash.cachedDistanceMeters
        if (cached != null && dash.cachedFilterVersion == TrackFilter.ALGORITHM_VERSION) return cached
        val meters = filteredTrack(dash.id).distanceMeters
        if (dash.endTimeMs != null) {
            dao.updateDash(dash.copy(cachedDistanceMeters = meters, cachedFilterVersion = TrackFilter.ALGORITHM_VERSION))
        }
        return meters
    }

    suspend fun setOdometer(dashId: Long, start: Double?, end: Double?) {
        val dash = dao.getDash(dashId) ?: return
        dao.updateDash(dash.copy(odometerStart = start, odometerEnd = end))
    }

    fun observeDashes(): Flow<List<DashEntity>> = dao.observeDashes()

    fun observeDay(date: LocalDate, cutoffHour: Int): Flow<List<DashEntity>> {
        // Widen the query window to cover dashes started in other time zones, then filter precisely.
        val b = WorkDay.bounds(date, ZoneId.systemDefault(), cutoffHour)
        return dao.observeDashesStartingBetween(b.first - DAY_MS, b.last + DAY_MS)
    }

    suspend fun summarize(dashes: List<DashEntity>, cutoffHour: Int): List<DaySummary> =
        dashes.groupBy { it.workDay(cutoffHour) }
            .map { (date, dayDashes) ->
                val finished = dayDashes.filter { it.endTimeMs != null }
                DaySummary(
                    date = date,
                    dashCount = dayDashes.size,
                    finishedDistanceMeters = finished.sumOf { distanceMeters(it) },
                    finishedActiveMs = finished.sumOf { it.endTimeMs!! - it.startTimeMs },
                    activeDashStartMs = dayDashes.firstOrNull { it.endTimeMs == null }?.startTimeMs,
                )
            }
            .sortedByDescending { it.date }

    suspend fun dayTracks(dashes: List<DashEntity>): List<DashTrack> =
        dashes.sortedBy { it.startTimeMs }.map { DashTrack(it, filteredTrack(it.id)) }

    suspend fun exportDashes(dashes: List<DashEntity>): List<ExportDash> =
        dashes.sortedBy { it.startTimeMs }.mapIndexed { i, d -> ExportDash("Dash ${i + 1}", rawPoints(d.id)) }

    companion object {
        private const val DAY_MS = 24 * 3_600_000L
    }
}

fun DashEntity.zone(): ZoneId = runCatching { ZoneId.of(zoneId) }.getOrDefault(ZoneId.systemDefault())

fun DashEntity.workDay(cutoffHour: Int): LocalDate = WorkDay.of(startTimeMs, zone(), cutoffHour)
