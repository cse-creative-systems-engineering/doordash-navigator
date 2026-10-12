package com.cse.dashnav.core

import kotlin.math.max

data class FilterConfig(
    /** Fixes with a worse accuracy radius than this are ignored. */
    val maxAccuracyM: Float = 30f,
    /** Implied speeds above this (~100 mph) are treated as GPS jumps. */
    val maxSpeedMps: Double = 45.0,
    /** Movement inside this radius of the last anchor is treated as parked jitter. */
    val minJitterRadiusM: Double = 10.0,
    /** A reported speed at or above this (~6.7 mph) counts as moving even within the jitter radius. */
    val movingSpeedMps: Float = 3.0f,
    /** Smallest step counted when the reported speed says we're moving. */
    val minMovingStepM: Double = 3.0,
    /** Silence between raw fixes longer than this is recorded as a data gap. */
    val gapThresholdMs: Long = 120_000,
    /** After this many consecutive, mutually consistent jumps, assume the anchor was wrong and re-anchor. */
    val reacquireAfterJumps: Int = 3,
)

enum class PointStatus {
    /** Counted: the point moved us away from the previous anchor. */
    MOVING,

    /** Kept, but added no distance (parked jitter, first fix, or re-acquisition). */
    STATIONARY,
    REJECTED_ACCURACY,
    REJECTED_JUMP,
}

data class ClassifiedPoint(
    val point: TrackPoint,
    val status: PointStatus,
    /** True when this point became the reference position that later distance is measured from. */
    val isAnchor: Boolean,
    /** Total counted distance for the dash up to and including this point. */
    val cumulativeMeters: Double,
)

/** A stretch with no GPS data. [bridgedMeters] is the straight-line distance counted across it. */
data class Gap(val startMs: Long, val endMs: Long, val bridgedMeters: Double)

data class FilteredTrack(
    val points: List<ClassifiedPoint>,
    val distanceMeters: Double,
    val gaps: List<Gap>,
) {
    val miles: Double get() = Geo.metersToMiles(distanceMeters)
    val anchors: List<ClassifiedPoint> get() = points.filter { it.isAnchor }
    val startMs: Long? get() = points.firstOrNull()?.point?.timeMs
    val endMs: Long? get() = points.lastOrNull()?.point?.timeMs
}

/**
 * Turns raw GPS fixes into counted distance. Feed points in time order with [add].
 *
 * Distance is measured between "anchors": a new fix only becomes the anchor (and adds distance)
 * once it is clearly outside the GPS noise around the previous anchor. This keeps a parked phone
 * from accumulating phantom miles while slow crawling still adds up once it leaves the radius.
 *
 * The same class runs live in the tracking service and offline when recomputing a stored dash,
 * so bump [ALGORITHM_VERSION] whenever its behavior changes to invalidate cached distances.
 */
class TrackFilter(private val config: FilterConfig = FilterConfig()) {
    var distanceMeters = 0.0
        private set

    private val _gaps = mutableListOf<Gap>()
    val gaps: List<Gap> get() = _gaps

    private var anchor: TrackPoint? = null
    private var lastRawTimeMs: Long? = null
    private var lastJump: TrackPoint? = null
    private var consecutiveJumps = 0

    fun add(p: TrackPoint): ClassifiedPoint {
        val prevRawTimeMs = lastRawTimeMs
        lastRawTimeMs = p.timeMs
        val (status, becameAnchor) = classify(p, prevRawTimeMs)
        return ClassifiedPoint(p, status, becameAnchor, distanceMeters)
    }

    private fun classify(p: TrackPoint, prevRawTimeMs: Long?): Pair<PointStatus, Boolean> {
        if (p.accuracyM > config.maxAccuracyM) return PointStatus.REJECTED_ACCURACY to false

        val a = anchor
        if (a == null) {
            anchor = p
            return PointStatus.STATIONARY to true
        }

        val dtMs = p.timeMs - a.timeMs
        if (dtMs <= 0) return PointStatus.REJECTED_JUMP to false

        val moved = Geo.distanceMeters(a, p)
        if (moved / (dtMs / 1000.0) > config.maxSpeedMps) return handleJump(a, p)
        consecutiveJumps = 0
        lastJump = null

        // Two fixes of a parked phone can each be off by their accuracy radius in opposite directions.
        val jitterRadius = max(config.minJitterRadiusM, (a.accuracyM + p.accuracyM).toDouble())
        val reportedMoving = (p.speedMps ?: 0f) >= config.movingSpeedMps
        val isMove = moved > jitterRadius || (reportedMoving && moved >= config.minMovingStepM)

        if (prevRawTimeMs != null && p.timeMs - prevRawTimeMs > config.gapThresholdMs) {
            _gaps += Gap(prevRawTimeMs, p.timeMs, if (isMove) moved else 0.0)
        }
        if (!isMove) return PointStatus.STATIONARY to false

        distanceMeters += moved
        anchor = p
        return PointStatus.MOVING to true
    }

    private fun handleJump(a: TrackPoint, p: TrackPoint): Pair<PointStatus, Boolean> {
        val prev = lastJump
        val consistentWithPrev = prev != null && p.timeMs > prev.timeMs &&
            Geo.distanceMeters(prev, p) / ((p.timeMs - prev.timeMs) / 1000.0) <= config.maxSpeedMps
        consecutiveJumps = if (consistentWithPrev) consecutiveJumps + 1 else 1
        lastJump = p
        if (consecutiveJumps < config.reacquireAfterJumps) return PointStatus.REJECTED_JUMP to false

        // Several fixes agree with each other but not with the anchor: the anchor was the outlier,
        // or we genuinely lost track. Restart from here without inventing distance.
        _gaps += Gap(a.timeMs, p.timeMs, 0.0)
        anchor = p
        consecutiveJumps = 0
        lastJump = null
        return PointStatus.STATIONARY to true
    }

    companion object {
        const val ALGORITHM_VERSION = 1

        fun run(points: List<TrackPoint>, config: FilterConfig = FilterConfig()): FilteredTrack {
            val filter = TrackFilter(config)
            val classified = points.sortedBy { it.timeMs }.map(filter::add)
            return FilteredTrack(classified, filter.distanceMeters, filter.gaps.toList())
        }
    }
}
