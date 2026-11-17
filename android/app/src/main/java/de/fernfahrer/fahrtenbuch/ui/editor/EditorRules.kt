package de.fernfahrer.fahrtenbuch.ui.editor

import java.time.LocalDate
import java.time.LocalDateTime

/** Prüfungen der Eingaben im Eintrag-Bildschirm (Tag und Arbeitszeit); gibt eine Fehlermeldung oder null zurück. */
object EditorRules {
    fun validate(s: EditorState): String? {
        if (s.endMinute <= s.startMinute) return "Das Ende muss nach dem Beginn liegen."
        val start = s.kmStart.toIntOrNull()
        val end = s.kmEnd.toIntOrNull()
        if (start != null && end != null && end < start) {
            return "Der Kilometerstand abends ist kleiner als morgens."
        }
        if (s.countryCode.isBlank()) return "Bitte ein Land wählen."
        return null
    }

    fun validateShift(d: ShiftDraft): String? {
        if (!d.running && !d.end.isAfter(d.start)) return "Das Arbeitsende muss nach dem Arbeitsbeginn liegen."
        if (d.running && d.start.isAfter(LocalDateTime.now())) return "Der Arbeitsbeginn liegt in der Zukunft."
        val ks = d.kmStart.toIntOrNull()
        val ke = d.kmEnd.toIntOrNull()
        if (!d.running && ks != null && ke != null && ke < ks) return "Der km-Stand am Ende ist kleiner als zu Beginn."
        return null
    }

    /** Ende um 00:00 eines Folgetags wird als 24:00 des Vortags gespeichert. */
    fun normalizeEnd(d: ShiftDraft): Pair<LocalDate, Int> =
        if (d.endMinute == 0 && d.endDate.isAfter(d.startDate)) d.endDate.minusDays(1) to MIDNIGHT else d.endDate to d.endMinute
}
