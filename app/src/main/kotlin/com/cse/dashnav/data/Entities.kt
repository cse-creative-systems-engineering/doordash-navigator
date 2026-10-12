package com.cse.dashnav.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.cse.dashnav.core.TrackPoint

@Entity(tableName = "dashes", indices = [Index("startTimeMs")])
data class DashEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTimeMs: Long,
    /** Null while the dash is in progress. */
    val endTimeMs: Long? = null,
    /** Time zone the dash was driven in, so work days stay correct after travel. */
    val zoneId: String,
    val odometerStart: Double? = null,
    val odometerEnd: Double? = null,
    /** Derived from the raw points; trusted only when [cachedFilterVersion] matches the current filter. */
    val cachedDistanceMeters: Double? = null,
    val cachedFilterVersion: Int? = null,
)

@Entity(
    tableName = "track_points",
    foreignKeys = [
        ForeignKey(
            entity = DashEntity::class,
            parentColumns = ["id"],
            childColumns = ["dashId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["dashId", "timeMs"])],
)
data class TrackPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dashId: Long,
    val timeMs: Long,
    val lat: Double,
    val lon: Double,
    val accuracyM: Float,
    val speedMps: Float?,
    val bearingDeg: Float?,
    val altitudeM: Double?,
    val isMock: Boolean,
) {
    fun toCore() = TrackPoint(timeMs, lat, lon, accuracyM, speedMps, bearingDeg, altitudeM)
}
