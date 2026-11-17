package de.fernfahrer.fahrtenbuch

import de.fernfahrer.fahrtenbuch.ui.editor.EditorState
import de.fernfahrer.fahrtenbuch.ui.editor.EditorViewModel
import de.fernfahrer.fahrtenbuch.ui.editor.MIDNIGHT
import de.fernfahrer.fahrtenbuch.ui.editor.ShiftDraft
import de.fernfahrer.fahrtenbuch.work.ReminderScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class EditorRulesTest {
    private val so = LocalDate.parse("2026-11-15")

    @Test
    fun schichtUeberMitternachtIstGueltig() {
        val d = ShiftDraft(startDate = so, startMinute = 23 * 60 + 30, endDate = so.plusDays(1), endMinute = 600)
        assertNull(EditorViewModel.validateShift(d))
    }

    @Test
    fun endeVorBeginnUndZuKleineKmWerdenAbgelehnt() {
        val d = ShiftDraft(startDate = so, startMinute = 600, endDate = so, endMinute = 540)
        assertEquals("Das Arbeitsende muss nach dem Arbeitsbeginn liegen.", EditorViewModel.validateShift(d))
        val km = ShiftDraft(startDate = so, startMinute = 600, endDate = so, endMinute = 700, kmStart = "1000", kmEnd = "900")
        assertEquals("Der km-Stand am Ende ist kleiner als zu Beginn.", EditorViewModel.validateShift(km))
    }

    @Test
    fun endeUmNullUhrGiltAls24UhrDesVortags() {
        val d = ShiftDraft(startDate = so, startMinute = 20 * 60, endDate = so.plusDays(1), endMinute = 0)
        assertEquals(so to MIDNIGHT, EditorViewModel.normalizeEnd(d))
    }

    @Test
    fun tagesZeitenWerdenGeprueft() {
        val base = EditorState(loading = false, startMinute = 0, endMinute = 1440)
        assertNull(EditorViewModel.validate(base))
        assertEquals("Das Ende muss nach dem Beginn liegen.", EditorViewModel.validate(base.copy(startMinute = 600, endMinute = 500)))
    }

    @Test
    fun erinnerungNach15StundenFruehestensInEinerMinute() {
        val start = LocalDateTime.parse("2026-11-15T06:00")
        assertEquals(LocalDateTime.parse("2026-11-15T21:00"), ReminderScheduler.firstReminder(start, 15, start.plusHours(1)))
        val spaet = start.plusHours(16)
        assertEquals(spaet.plusMinutes(1), ReminderScheduler.firstReminder(start, 15, spaet))
    }
}
