package com.cse.dashnav.core

/** Where the replay is at one moment of playback. */
data class ReplayState(
    val realTimeMs: Long,
    val lat: Double,
    val lon: Double,
    /** Counted distance for the whole work day so far. */
    val dayMeters: Double,
    val dashIndex: Int,
    val speedMps: Double,
    val isIdle: Boolean,
)

/** One position on the replay route, at [playMs] into playback. */
data class ReplayFrame(
    val realTimeMs: Long,
    val lat: Double,
    val lon: Double,
    val dayMeters: Double,
    val dashIndex: Int,
    val playMs: Long,
)

/**
 * Maps playback time to positions for a whole work day of dashes.
 *
 * Frames come from the filtered track: anchors where the car moved, plus parked samples pinned to
 * the anchor position so the marker sits still while parked instead of sliding toward the next
 * move. With [skipIdle], the time between dashes and long parked stretches inside a dash are
 * squeezed down to [idleKeepMs] of playback.
 */
class ReplayTimeline(
    dashes: List<FilteredTrack>,
    skipIdle: Boolean = true,
    idleKeepMs: Long = 2_000,
) {
    val frames: List<ReplayFrame>

    /** Playback time at which each dash starts, for jumping between dashes. */
    val dashStartPlayMs: List<Long>

    val durationMs: Long
    val isEmpty: Boolean get() = frames.isEmpty()

    init {
        val built = mutableListOf<ReplayFrame>()
        val starts = mutableListOf<Long>()
        var dayOffset = 0.0
        dashes.forEachIndexed { dashIndex, track ->
            var anchor: TrackPoint? = null
            for (cp in track.points) {
                if (cp.isAnchor) anchor = cp.point
                val a = anchor ?: continue
                if (cp.status == PointStatus.REJECTED_ACCURACY || cp.status == PointStatus.REJECTED_JUMP) continue

                val prev = built.lastOrNull()
                val playMs = if (prev == null) {
                    0L
                } else {
                    val dt = cp.point.timeMs - prev.realTimeMs
                    val newDash = dashIndex != prev.dashIndex
                    val parked = a.lat == prev.lat && a.lon == prev.lon
                    prev.playMs + if (skipIdle && (newDash || parked) && dt > idleKeepMs) idleKeepMs else dt
                }
                if (prev == null || prev.dashIndex != dashIndex) starts += playMs
                built += ReplayFrame(cp.point.timeMs, a.lat, a.lon, dayOffset + cp.cumulativeMeters, dashIndex, playMs)
            }
            dayOffset += track.distanceMeters
        }
        frames = built
        dashStartPlayMs = starts
        durationMs = built.lastOrNull()?.playMs ?: 0L
    }

    fun stateAt(playMs: Long): ReplayState? {
        if (frames.isEmpty()) return null
        val t = playMs.coerceIn(0, durationMs)
        val i = indexAtOrBefore(t)
        val a = frames[i]
        val b = frames.getOrNull(i + 1)
        if (b == null || b.playMs == a.playMs) {
            return ReplayState(a.realTimeMs, a.lat, a.lon, a.dayMeters, a.dashIndex, 0.0, isIdle = true)
        }

        val f = (t - a.playMs).toDouble() / (b.playMs - a.playMs)
        val realDt = b.realTimeMs - a.realTimeMs
        val realTime = a.realTimeMs + (realDt * f).toLong()
        if (b.dashIndex != a.dashIndex) {
            // Between dashes: we don't know the route, so hold at the end of the previous dash.
            return ReplayState(realTime, a.lat, a.lon, a.dayMeters, a.dashIndex, 0.0, isIdle = true)
        }
        val segMeters = b.dayMeters - a.dayMeters
        val speed = if (realDt > 0) segMeters / (realDt / 1000.0) else 0.0
        return ReplayState(
            realTimeMs = realTime,
            lat = a.lat + (b.lat - a.lat) * f,
            lon = a.lon + (b.lon - a.lon) * f,
            dayMeters = a.dayMeters + segMeters * f,
            dashIndex = a.dashIndex,
            speedMps = speed,
            isIdle = segMeters == 0.0,
        )
    }

    /** Number of frames reached by [playMs], for drawing the driven portion of the route. */
    fun framesReached(playMs: Long): Int = if (frames.isEmpty()) 0 else indexAtOrBefore(playMs) + 1

    private fun indexAtOrBefore(playMs: Long): Int {
        var lo = 0
        var hi = frames.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (frames[mid].playMs <= playMs) lo = mid else hi = mid - 1
        }
        return lo
    }
}
