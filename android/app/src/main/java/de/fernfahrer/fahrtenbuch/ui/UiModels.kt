package de.fernfahrer.fahrtenbuch.ui

import java.time.LocalDate
import java.time.LocalDateTime

/** Informationen zur laufenden Tour für den Startbildschirm. */
data class ActiveTour(
    val tourId: Long,
    val startDate: LocalDate,
    /** Wievielter Tag der Tour heute ist. */
    val dayNumber: Int,
    val lastCountry: String,
    val todayEntered: Boolean,
)

/** Abfrage des km-Stands beim Beenden. */
data class KmPrompt(
    val kind: Kind,
    /** Zeitpunkt des Tippens auf „Arbeit beenden“/„Tour beenden“ – gilt als Ende. */
    val at: LocalDateTime,
    /** Vorschlag: letzter bekannter km-Stand. */
    val suggestion: Int?,
    /** km-Stand bei Arbeitsbeginn bzw. am Tagesbeginn; der neue Wert darf nicht kleiner sein. */
    val minimum: Int?,
    /** Bei „Tour beenden“: läuft noch eine Arbeitszeit, die mit beendet wird? */
    val endsRunningWork: Boolean = false,
) {
    enum class Kind { END_WORK, END_TOUR }
}
