package de.fernfahrer.fahrtenbuch.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.fernfahrer.fahrtenbuch.FahrtenbuchApp
import de.fernfahrer.fahrtenbuch.data.Backup
import de.fernfahrer.fahrtenbuch.data.BackupData
import de.fernfahrer.fahrtenbuch.data.BackupFormatException
import de.fernfahrer.fahrtenbuch.data.Repository
import de.fernfahrer.fahrtenbuch.data.Settings
import de.fernfahrer.fahrtenbuch.domain.ClassifiedDay
import de.fernfahrer.fahrtenbuch.domain.Period
import de.fernfahrer.fahrtenbuch.domain.PeriodType
import de.fernfahrer.fahrtenbuch.domain.Stats
import de.fernfahrer.fahrtenbuch.export.CsvExporter
import de.fernfahrer.fahrtenbuch.export.PdfExporter
import de.fernfahrer.fahrtenbuch.export.Share
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Informationen zur laufenden Tour für den Startbildschirm. */
data class ActiveTour(
    val tourId: Long,
    val startDate: LocalDate,
    /** Wievielter Tag der Tour heute ist. */
    val dayNumber: Int,
    val lastCountry: String,
    val todayEntered: Boolean,
)

enum class ExportFormat { PDF, CSV }

class MainViewModel(
    private val repo: Repository,
    private val settings: Settings,
) : ViewModel() {

    /** Alle Tage, neueste zuerst; null solange noch geladen wird. */
    val days: StateFlow<List<ClassifiedDay>?> =
        repo.classifiedDays.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val activeTour: StateFlow<ActiveTour?> = combine(repo.classifiedDays, repo.tours) { days, tours ->
        val tour = tours.filter { !it.ended }.maxByOrNull { it.id } ?: return@combine null
        val tourDays = days.filter { it.day.tourId == tour.id }.map { it.day }
        val start = tourDays.minOfOrNull { it.date } ?: return@combine null
        val today = LocalDate.now()
        ActiveTour(
            tourId = tour.id,
            startDate = start,
            dayNumber = ChronoUnit.DAYS.between(start, today).toInt() + 1,
            lastCountry = tourDays.maxBy { it.date }.countryCode,
            todayEntered = tourDays.any { it.date == today },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val recentCountries: StateFlow<List<String>> =
        repo.recentCountries().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val driverName: StateFlow<String> = settings.driverName

    fun setDriverName(name: String) = settings.setDriverName(name)

    /** Zeitraum der Auswertung (bleibt beim Wechsel der Tabs erhalten). */
    private val _period = MutableStateFlow(Period(PeriodType.YEAR, LocalDate.now()))
    val period: StateFlow<Period> = _period.asStateFlow()
    fun setPeriod(p: Period) {
        _period.value = p
    }

    /** Jahre, für die ein Export möglich ist (mit Einträgen, plus das aktuelle Jahr). */
    val exportYears: StateFlow<List<Int>> = repo.classifiedDays.map { days ->
        (days.map { it.day.date.year } + LocalDate.now().year).distinct().sortedDescending()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), listOf(LocalDate.now().year))

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    fun showMessage(text: String) {
        _messages.tryEmit(text)
    }

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    // ---------- Export ----------

    fun exportYear(context: Context, year: Int, format: ExportFormat) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            _busy.value = true
            try {
                val all = days.value ?: emptyList()
                val period = Period(PeriodType.YEAR, LocalDate.of(year, 1, 1))
                val inYear = all.filter { period.contains(it.day.date) }
                val summary = Stats.summarize(inYear)
                val driver = settings.driverName.value
                val file = withContext(Dispatchers.IO) {
                    val dir = Share.exportDir(appContext)
                    dir.listFiles()?.forEach(File::delete)
                    when (format) {
                        ExportFormat.PDF -> File(dir, "Fahrtenbuch_$year.pdf").also {
                            PdfExporter.write(it, year, inYear, summary, driver)
                        }
                        ExportFormat.CSV -> File(dir, "Fahrtenbuch_$year.csv").also {
                            it.writeText(CsvExporter.build(year, inYear, summary, driver), Charsets.UTF_8)
                        }
                    }
                }
                val mime = if (format == ExportFormat.PDF) "application/pdf" else "text/csv"
                Share.shareFile(appContext, file, mime, "Jahresübersicht $year teilen")
            } catch (e: Exception) {
                showMessage("Export fehlgeschlagen: ${e.localizedMessage ?: e.javaClass.simpleName}")
            } finally {
                _busy.value = false
            }
        }
    }

    // ---------- Datensicherung ----------

    fun writeBackup(context: Context, uri: Uri) {
        val resolver = context.applicationContext.contentResolver
        viewModelScope.launch {
            try {
                val data = repo.exportData().copy(driverName = settings.driverName.value)
                withContext(Dispatchers.IO) {
                    resolver.openOutputStream(uri, "wt")?.use { it.write(Backup.toJson(data).toByteArray()) }
                        ?: error("Datei konnte nicht geöffnet werden")
                }
                showMessage("Datensicherung gespeichert (${data.days.size} Tage)")
            } catch (e: Exception) {
                showMessage("Sicherung fehlgeschlagen: ${e.localizedMessage ?: e.javaClass.simpleName}")
            }
        }
    }

    /** Eingelesene Sicherung, die noch bestätigt werden muss. */
    private val _pendingRestore = MutableStateFlow<BackupData?>(null)
    val pendingRestore: StateFlow<BackupData?> = _pendingRestore.asStateFlow()

    fun readBackup(context: Context, uri: Uri) {
        val resolver = context.applicationContext.contentResolver
        viewModelScope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    resolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                        ?: error("Datei konnte nicht geöffnet werden")
                }
                _pendingRestore.value = Backup.fromJson(text)
            } catch (e: BackupFormatException) {
                showMessage(e.message ?: "Ungültige Datei")
            } catch (e: Exception) {
                showMessage("Datei konnte nicht gelesen werden.")
            }
        }
    }

    fun confirmRestore() {
        val data = _pendingRestore.value ?: return
        _pendingRestore.value = null
        viewModelScope.launch {
            try {
                repo.replaceAll(data)
                if (data.driverName.isNotBlank()) settings.setDriverName(data.driverName)
                showMessage("Datensicherung geladen: ${data.days.size} Tage")
            } catch (e: Exception) {
                showMessage("Wiederherstellen fehlgeschlagen: ${e.localizedMessage ?: e.javaClass.simpleName}")
            }
        }
    }

    fun cancelRestore() {
        _pendingRestore.value = null
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as FahrtenbuchApp
                MainViewModel(app.repository, app.settings)
            }
        }
    }
}
