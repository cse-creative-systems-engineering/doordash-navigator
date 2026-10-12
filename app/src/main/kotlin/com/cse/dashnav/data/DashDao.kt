package com.cse.dashnav.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DashDao {
    @Insert
    suspend fun insertDash(dash: DashEntity): Long

    @Update
    suspend fun updateDash(dash: DashEntity)

    @Query("SELECT * FROM dashes WHERE id = :id")
    suspend fun getDash(id: Long): DashEntity?

    @Query("SELECT * FROM dashes WHERE endTimeMs IS NULL ORDER BY startTimeMs DESC LIMIT 1")
    suspend fun getActiveDash(): DashEntity?

    @Query("SELECT * FROM dashes ORDER BY startTimeMs DESC")
    fun observeDashes(): Flow<List<DashEntity>>

    @Query("SELECT * FROM dashes WHERE startTimeMs >= :fromMs AND startTimeMs < :toMs ORDER BY startTimeMs")
    fun observeDashesStartingBetween(fromMs: Long, toMs: Long): Flow<List<DashEntity>>

    @Insert
    suspend fun insertPoint(point: TrackPointEntity)

    @Query("SELECT * FROM track_points WHERE dashId = :dashId ORDER BY timeMs")
    suspend fun getPoints(dashId: Long): List<TrackPointEntity>
}
