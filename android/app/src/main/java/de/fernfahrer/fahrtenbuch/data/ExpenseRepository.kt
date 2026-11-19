package de.fernfahrer.fahrtenbuch.data

import androidx.room.withTransaction
import de.fernfahrer.fahrtenbuch.domain.ExpenseBook
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Speichern der Spesentabellen (eine pro Jahr). */
class ExpenseRepository(private val db: AppDatabase) {
    private val dao = db.expenseDao()

    val years: Flow<List<ExpenseYearEntity>> = dao.observeYears()
    fun rates(year: Int): Flow<List<ExpenseRateEntity>> = dao.observeRates(year)

    suspend fun getYear(year: Int): ExpenseYearEntity? = dao.getYear(year)
    suspend fun getRates(year: Int): List<ExpenseRateEntity> = dao.getRates(year)

    /**
     * Legt die Tabelle für [year] an, falls es sie noch nicht gibt: als Kopie des jüngsten früheren
     * Jahres (Pauschale und alle Länder), sonst leer. Eine bestehende Tabelle bleibt unverändert.
     * Gibt true zurück, wenn eine Tabelle neu angelegt wurde.
     */
    suspend fun ensureYear(year: Int): Boolean = db.withTransaction {
        if (dao.getYear(year) != null) return@withTransaction false
        val source = dao.getAllYears().filter { it.year < year }.maxByOrNull { it.year }
        dao.insertYear(ExpenseYearEntity(year, source?.overnightCents))
        if (source != null) dao.insertRates(dao.getRates(source.year).map { it.copy(year = year) })
        true
    }

    suspend fun setOvernight(year: Int, cents: Long?) = db.withTransaction {
        ensureYear(year)
        dao.upsertYear(ExpenseYearEntity(year, cents))
    }

    suspend fun saveRate(rate: ExpenseRateEntity) = db.withTransaction {
        ensureYear(rate.year)
        dao.upsertRate(rate)
    }

    suspend fun deleteRate(year: Int, code: String) = dao.deleteRate(year, code)

    /** Alle Tabellen für die Euro-Berechnung (ändert sich bei jeder Eingabe). */
    val book: Flow<ExpenseBook> = combine(dao.observeYears(), dao.observeAllRates()) { years, rates ->
        val byYear = rates.groupBy { it.year }
        ExpenseBook(years.map { ExpenseYear(it, byYear[it.year].orEmpty()).toTable() })
    }

    suspend fun getBook(): ExpenseBook = ExpenseBook(getAll().map { it.toTable() })

    /** Alle Tabellen (für die Datensicherung). */
    suspend fun getAll(): List<ExpenseYear> {
        val rates = dao.getAllRates().groupBy { it.year }
        return dao.getAllYears().map { ExpenseYear(it, rates[it.year].orEmpty()) }
    }
}
