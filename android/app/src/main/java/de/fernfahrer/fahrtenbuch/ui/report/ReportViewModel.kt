package de.fernfahrer.fahrtenbuch.ui.report

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.fernfahrer.fahrtenbuch.FahrtenbuchApp
import de.fernfahrer.fahrtenbuch.data.Repository
import de.fernfahrer.fahrtenbuch.domain.TravelReport
import de.fernfahrer.fahrtenbuch.export.Share
import de.fernfahrer.fahrtenbuch.export.TravelReportPdf
import de.fernfahrer.fahrtenbuch.export.TravelReportSource
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Vorschau und Erstellen der monatlichen Reisekostenabrechnung. */
@OptIn(ExperimentalCoroutinesApi::class)
class ReportViewModel(
    private val repo: Repository,
    private val source: TravelReportSource,
    name: StateFlow<String>,
    /** Ändert sich bei jeder Änderung an Tagen, Arbeitszeiten oder Spesentabellen. */
    changes: Flow<Any>,
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {
    private val _month = MutableStateFlow(YearMonth.from(today()))
    val month: StateFlow<YearMonth> = _month.asStateFlow()

    /** Neueste wählbare Monat: der laufende. */
    val latestMonth: YearMonth get() = YearMonth.from(today())

    fun setMonth(m: YearMonth) {
        _month.value = if (m.isAfter(latestMonth)) latestMonth else m
    }

    /** Aktuelle Abrechnung des gewählten Monats; null solange sie berechnet wird. */
    val report: StateFlow<TravelReport?> = combine(_month, name, changes) { m, n, _ -> m to n }
        .mapLatest { (m, n) -> withContext(Dispatchers.Default) { source.build(m, n) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    /** Eigenen Text „Reiseanlass und Reiseweg“ speichern; null/leer = wieder automatisch. */
    fun setText(date: LocalDate, text: String?) {
        viewModelScope.launch { repo.setTravelText(date, text) }
    }

    /** Erstellt die PDF und öffnet das Teilen-Menü (E-Mail, WhatsApp, Speichern …). */
    fun share(context: Context) {
        val r = report.value ?: return
        if (!check(r)) return
        val appContext = context.applicationContext
        viewModelScope.launch {
            _busy.value = true
            try {
                val file = withContext(Dispatchers.IO) {
                    val dir = Share.exportDir(appContext)
                    dir.listFiles()?.forEach(File::delete)
                    File(dir, r.fileName).also { TravelReportPdf.write(it, r) }
                }
                Share.shareFile(appContext, file, "application/pdf", "Reisekostenabrechnung teilen")
            } catch (e: Exception) {
                _messages.tryEmit("PDF konnte nicht erstellt werden: ${e.localizedMessage ?: e.javaClass.simpleName}")
            } finally {
                _busy.value = false
            }
        }
    }

    /** Schreibt die PDF an einen vom Nutzer gewählten Speicherort. */
    fun save(context: Context, uri: Uri) {
        val r = report.value ?: return
        if (!check(r)) return
        val resolver = context.applicationContext.contentResolver
        viewModelScope.launch {
            _busy.value = true
            try {
                withContext(Dispatchers.IO) {
                    resolver.openOutputStream(uri)?.use { TravelReportPdf.write(it, r) }
                        ?: error("Datei konnte nicht geöffnet werden")
                }
                _messages.tryEmit("Reisekostenabrechnung gespeichert")
            } catch (e: Exception) {
                _messages.tryEmit("Speichern fehlgeschlagen: ${e.localizedMessage ?: e.javaClass.simpleName}")
            } finally {
                _busy.value = false
            }
        }
    }

    /** Keine PDF mit fehlenden Beträgen oder ohne Tage. */
    private fun check(r: TravelReport): Boolean = when {
        r.rows.isEmpty() -> false.also { _messages.tryEmit("In diesem Monat gibt es keine Tage für die Abrechnung.") }
        !r.complete -> false.also { _messages.tryEmit("Erst die fehlenden Beträge in der Spesentabelle eintragen.") }
        else -> true
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as FahrtenbuchApp
                ReportViewModel(
                    repo = app.repository,
                    source = app.travelReports,
                    name = app.settings.driverName,
                    changes = combine(
                        app.repository.classifiedDays, app.repository.shifts, app.expenses.book,
                        app.repository.overnightPlaces,
                    ) { a, b, c, d -> listOf(a, b, c, d) },
                )
            }
        }
    }
}
