package de.fernfahrer.fahrtenbuch.ui.editor

import de.fernfahrer.fahrtenbuch.domain.DayType
import de.fernfahrer.fahrtenbuch.domain.Stats
import de.fernfahrer.fahrtenbuch.domain.WorkPortion
import de.fernfahrer.fahrtenbuch.domain.WorkSplitter
import java.time.LocalDate
import java.time.LocalDateTime

/** Wie der Bildschirm geöffnet wurde. (Tour beenden geht über den km-Dialog auf dem Startbildschirm.) */
enum class EditorMode { NORMAL, TOUR_START, TOUR_DAY }

const val MIDNIGHT = 24 * 60

data class EditorState(
    val loading: Boolean = true,
    val mode: EditorMode = EditorMode.NORMAL,
    val id: Long = 0,
    val date: LocalDate = LocalDate.now(),
    val startMinute: Int = 6 * 60,
    val endMinute: Int = 18 * 60,
    val countryCode: String = Stats.HOME_COUNTRY,
    /** Land von Hand gewählt (GPS ändert es dann nicht mehr). */
    val countryManual: Boolean = false,
    val kmStart: String = "",
    val kmEnd: String = "",
    /** km-Stände anteilig geschätzt (Schicht über Mitternacht), solange nicht geändert. */
    val kmEstimated: Boolean = false,
    val note: String = "",
    val tourId: Long? = null,
    val tourStart: LocalDate? = null,
    val override: DayType? = null,
    /** Übernachtung im LKW von Hand gesetzt; null = automatisch. */
    val overnightOverride: Boolean? = null,
    val overnightAuto: Boolean = false,
    val autoType: DayType = DayType.NONE,
    val autoReason: String = "",
    /** Automatische Einordnung beruht auf der Firmenregel. */
    val autoCompanyRule: Boolean = false,
    val missingDates: List<LocalDate> = emptyList(),
    val fillGaps: Boolean = true,
    /** Arbeitszeit an diesem Tag (Anteile der Schichten). */
    val work: List<WorkPortion> = emptyList(),
    val error: String? = null,
    /** Nach Speichern/Löschen: Meldung für den Startbildschirm; Bildschirm schließt sich. */
    val finishedMessage: String? = null,
    val finished: Boolean = false,
) {
    val isNew: Boolean get() = id == 0L
    val absenceMinutes: Int get() = (endMinute - startMinute).coerceAtLeast(0)
    val distanceKm: Int?
        get() {
            val s = kmStart.toIntOrNull() ?: return null
            val e = kmEnd.toIntOrNull() ?: return null
            return if (e >= s) e - s else null
        }
    val effectiveType: DayType get() = override ?: autoType
    val overnight: Boolean get() = overnightOverride ?: overnightAuto
    val workMinutes: Int get() = WorkSplitter.minutes(work)
    val isTourDay: Boolean get() = tourId != null || mode == EditorMode.TOUR_START

    val title: String
        get() = when (mode) {
            EditorMode.TOUR_START -> "Tour starten"
            EditorMode.TOUR_DAY -> if (isNew) "Tour-Tag erfassen" else "Tour-Tag bearbeiten"
            EditorMode.NORMAL -> if (isNew) "Tag erfassen" else "Tag bearbeiten"
        }
}

/** Bearbeitung einer Arbeitsschicht im Dialog. */
data class ShiftDraft(
    val id: Long = 0,
    val startDate: LocalDate,
    val startMinute: Int,
    val endDate: LocalDate,
    val endMinute: Int,
    val running: Boolean = false,
    val kmStart: String = "",
    val kmEnd: String = "",
    val error: String? = null,
) {
    val isNew: Boolean get() = id == 0L
    val start: LocalDateTime get() = startDate.atStartOfDay().plusMinutes(startMinute.toLong())
    val end: LocalDateTime get() = endDate.atStartOfDay().plusMinutes(endMinute.toLong())
}
