package de.fernfahrer.fahrtenbuch.data

/** Ergebnis von [Repository.startWork]. */
sealed interface StartResult {
    data class Started(val shift: WorkShiftEntity, val dayCreated: Boolean) : StartResult
    data class AlreadyRunning(val shift: WorkShiftEntity) : StartResult
}

/** Ergebnis von [Repository.endTour]. */
sealed interface EndTourResult {
    /** Tour beendet; [endedShift] ist die dabei mit beendete Arbeitszeit (falls eine lief). */
    data class Ended(val endedShift: WorkShiftEntity?, val filledDays: Int) : EndTourResult
    data object NoActiveTour : EndTourResult
}
