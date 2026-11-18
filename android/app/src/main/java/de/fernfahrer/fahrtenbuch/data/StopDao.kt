package de.fernfahrer.fahrtenbuch.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Standorte beim Arbeitsende, erreichte Meilensteine und Übernachtungsplätze. */
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

    // ---------- Übernachtungsplätze ----------

    @Query("SELECT * FROM overnight_places ORDER BY id")
    fun observePlaces(): Flow<List<OvernightPlaceEntity>>

    @Query("SELECT * FROM overnight_stays ORDER BY date, minute")
    fun observeStays(): Flow<List<OvernightStayEntity>>

    @Query("SELECT * FROM overnight_places ORDER BY id")
    suspend fun getAllPlaces(): List<OvernightPlaceEntity>

    @Query("SELECT * FROM overnight_stays ORDER BY date, minute")
    suspend fun getAllStays(): List<OvernightStayEntity>

    @Query("SELECT * FROM overnight_places WHERE id = :id")
    suspend fun getPlace(id: Long): OvernightPlaceEntity?

    @Query("SELECT * FROM overnight_stays WHERE placeId = :placeId ORDER BY date, minute")
    suspend fun getStaysOfPlace(placeId: Long): List<OvernightStayEntity>

    @Query("SELECT COUNT(*) FROM overnight_places")
    suspend fun countPlaces(): Int

    @Insert
    suspend fun insertPlace(place: OvernightPlaceEntity): Long

    @Insert
    suspend fun insertPlaces(places: List<OvernightPlaceEntity>)

    @Update
    suspend fun updatePlace(place: OvernightPlaceEntity)

    @Query("DELETE FROM overnight_places WHERE id = :id")
    suspend fun deletePlace(id: Long)

    @Insert
    suspend fun insertStay(stay: OvernightStayEntity): Long

    @Insert
    suspend fun insertStays(stays: List<OvernightStayEntity>)

    @Query("SELECT * FROM overnight_stays WHERE id = :id")
    suspend fun getStay(id: Long): OvernightStayEntity?

    @Query("DELETE FROM overnight_stays WHERE id = :id")
    suspend fun deleteStay(id: Long)

    @Query("DELETE FROM overnight_stays")
    suspend fun deleteAllStays()

    @Query("DELETE FROM overnight_places")
    suspend fun deleteAllPlaces()
}
