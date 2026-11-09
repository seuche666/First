package de.fernfahrer.fahrtenbuch.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface FahrtenbuchDao {
    @Query("SELECT * FROM days ORDER BY date DESC")
    fun observeDays(): Flow<List<DayEntity>>

    @Query("SELECT * FROM tours")
    fun observeTours(): Flow<List<TourEntity>>

    /** Zuletzt genutzte Länder, neuestes zuerst. */
    @Query("SELECT countryCode FROM days GROUP BY countryCode ORDER BY MAX(date) DESC LIMIT :limit")
    fun observeRecentCountries(limit: Int): Flow<List<String>>

    @Query("SELECT * FROM days WHERE id = :id")
    suspend fun getDay(id: Long): DayEntity?

    @Query("SELECT * FROM days WHERE date = :date")
    suspend fun getDayByDate(date: LocalDate): DayEntity?

    @Query("SELECT * FROM days WHERE date < :date ORDER BY date DESC LIMIT 1")
    suspend fun getDayBefore(date: LocalDate): DayEntity?

    @Query("SELECT * FROM days WHERE tourId IS NULL ORDER BY date DESC LIMIT 1")
    suspend fun getLatestSingleDay(): DayEntity?

    @Query("SELECT * FROM days WHERE tourId = :tourId ORDER BY date")
    suspend fun getDaysOfTour(tourId: Long): List<DayEntity>

    @Query("SELECT * FROM tours WHERE id = :id")
    suspend fun getTour(id: Long): TourEntity?

    @Query("SELECT * FROM tours WHERE ended = 0 ORDER BY id DESC LIMIT 1")
    suspend fun getActiveTour(): TourEntity?

    @Query("SELECT * FROM days ORDER BY date")
    suspend fun getAllDays(): List<DayEntity>

    @Query("SELECT * FROM tours ORDER BY id")
    suspend fun getAllTours(): List<TourEntity>

    @Insert
    suspend fun insertDay(day: DayEntity): Long

    @Insert
    suspend fun insertDays(days: List<DayEntity>)

    @Update
    suspend fun updateDay(day: DayEntity)

    @Delete
    suspend fun deleteDay(day: DayEntity)

    @Insert
    suspend fun insertTour(tour: TourEntity): Long

    @Insert
    suspend fun insertTours(tours: List<TourEntity>)

    @Update
    suspend fun updateTour(tour: TourEntity)

    @Query("DELETE FROM tours WHERE id = :id")
    suspend fun deleteTour(id: Long)

    @Query("DELETE FROM days")
    suspend fun deleteAllDays()

    @Query("DELETE FROM tours")
    suspend fun deleteAllTours()
}
