package de.fernfahrer.fahrtenbuch.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Spesentabellen (eine pro Jahr). */
@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expense_years ORDER BY year")
    fun observeYears(): Flow<List<ExpenseYearEntity>>

    @Query("SELECT * FROM expense_years ORDER BY year")
    suspend fun getAllYears(): List<ExpenseYearEntity>

    @Query("SELECT * FROM expense_years WHERE year = :year")
    suspend fun getYear(year: Int): ExpenseYearEntity?

    @Query("SELECT * FROM expense_rates WHERE year = :year ORDER BY countryCode")
    fun observeRates(year: Int): Flow<List<ExpenseRateEntity>>

    @Query("SELECT * FROM expense_rates WHERE year = :year ORDER BY countryCode")
    suspend fun getRates(year: Int): List<ExpenseRateEntity>

    @Query("SELECT * FROM expense_rates ORDER BY year, countryCode")
    fun observeAllRates(): Flow<List<ExpenseRateEntity>>

    @Query("SELECT * FROM expense_rates ORDER BY year, countryCode")
    suspend fun getAllRates(): List<ExpenseRateEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertYear(year: ExpenseYearEntity): Long

    @Upsert
    suspend fun upsertYear(year: ExpenseYearEntity)

    @Insert
    suspend fun insertYears(years: List<ExpenseYearEntity>)

    @Upsert
    suspend fun upsertRate(rate: ExpenseRateEntity)

    @Insert
    suspend fun insertRates(rates: List<ExpenseRateEntity>)

    @Query("DELETE FROM expense_rates WHERE year = :year AND countryCode = :code")
    suspend fun deleteRate(year: Int, code: String)

    @Query("DELETE FROM expense_rates")
    suspend fun deleteAllRates()

    @Query("DELETE FROM expense_years")
    suspend fun deleteAllYears()
}
