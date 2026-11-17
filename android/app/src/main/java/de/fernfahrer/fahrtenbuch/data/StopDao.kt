package de.fernfahrer.fahrtenbuch.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Standorte beim Arbeitsende und erreichte Meilensteine. */
@Dao
interface StopDao {
    @Query("SELECT * FROM stop_locations ORDER BY date, minute")
    fun observeStops(): Flow<List<StopLocationEntity>>

    @Query("SELECT * FROM stop_locations ORDER BY date, minute")
    suspend fun getAllStops(): List<StopLocationEntity>

    @Insert
    suspend fun insertStop(stop: StopLocationEntity): Long

    @Insert
    suspend fun insertStops(stops: List<StopLocationEntity>)

    @Query("DELETE FROM stop_locations")
    suspend fun deleteAllStops()

    @Query("SELECT * FROM milestones ORDER BY achievedOn, id")
    fun observeMilestones(): Flow<List<MilestoneEntity>>

    @Query("SELECT * FROM milestones ORDER BY achievedOn, id")
    suspend fun getAllMilestones(): List<MilestoneEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMilestones(list: List<MilestoneEntity>)

    @Query("DELETE FROM milestones")
    suspend fun deleteAllMilestones()
}
