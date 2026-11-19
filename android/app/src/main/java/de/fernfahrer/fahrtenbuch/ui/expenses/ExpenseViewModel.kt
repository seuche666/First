package de.fernfahrer.fahrtenbuch.ui.expenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.fernfahrer.fahrtenbuch.FahrtenbuchApp
import de.fernfahrer.fahrtenbuch.data.ExpenseRateEntity
import de.fernfahrer.fahrtenbuch.data.ExpenseRepository
import de.fernfahrer.fahrtenbuch.data.ExpenseYearEntity
import de.fernfahrer.fahrtenbuch.domain.ClassifiedDay
import de.fernfahrer.fahrtenbuch.domain.Countries
import de.fernfahrer.fahrtenbuch.domain.ExpenseTables
import de.fernfahrer.fahrtenbuch.domain.MissingRate
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Anzeige der Spesentabelle eines Jahres. */
data class ExpenseTableState(
    val year: Int,
    val years: List<Int>,
    /** null = für dieses Jahr gibt es noch keine Tabelle. */
    val table: ExpenseYearEntity?,
    /** Länder, alphabetisch nach deutschem Namen. */
    val rates: List<ExpenseRateEntity>,
    /** Aus diesem Jahr würde eine neue Tabelle kopiert (null = sie startet leer). */
    val copySource: Int?,
    /** Länder mit erfassten Tagen, aber ohne Betrag. */
    val missing: List<MissingRate>,
    /** Übernachtungen im LKW in diesem Jahr. */
    val overnights: Int,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ExpenseViewModel(
    private val expenses: ExpenseRepository,
    days: Flow<List<ClassifiedDay>>,
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {
    private val selectedYear = MutableStateFlow(today().year)
    val year: StateFlow<Int> = selectedYear.asStateFlow()

    fun selectYear(year: Int) {
        selectedYear.value = year
    }

    val state: StateFlow<ExpenseTableState?> = selectedYear.flatMapLatest { y ->
        combine(expenses.years, expenses.rates(y), days) { tables, rates, allDays ->
            val existing = tables.map { it.year }
            val table = tables.firstOrNull { it.year == y }
            ExpenseTableState(
                year = y,
                years = ExpenseTables.selectableYears(existing, allDays.map { it.day.date.year }, today()),
                table = table,
                rates = rates.sortedBy { Countries.name(it.countryCode) },
                copySource = ExpenseTables.sourceYear(y, existing),
                missing = if (table == null) emptyList() else ExpenseTables.missingRates(allDays, y, rates.map { it.toRate() }),
                overnights = ExpenseTables.overnights(allDays, y),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Tabelle für das gewählte Jahr anlegen (Kopie des Vorjahres bzw. leer). */
    fun createTable() {
        val y = selectedYear.value
        viewModelScope.launch { expenses.ensureYear(y) }
    }

    fun setOvernight(cents: Long?) {
        val y = selectedYear.value
        viewModelScope.launch { expenses.setOvernight(y, cents) }
    }

    fun saveRate(code: String, fullDayCents: Long?, partialDayCents: Long?) {
        val y = selectedYear.value
        viewModelScope.launch { expenses.saveRate(ExpenseRateEntity(y, code, fullDayCents, partialDayCents)) }
    }

    fun deleteRate(code: String) {
        val y = selectedYear.value
        viewModelScope.launch { expenses.deleteRate(y, code) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as FahrtenbuchApp
                ExpenseViewModel(app.expenses, app.repository.classifiedDays)
            }
        }
    }
}
