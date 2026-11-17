package de.fernfahrer.fahrtenbuch

import de.fernfahrer.fahrtenbuch.data.WorkShiftEntity
import de.fernfahrer.fahrtenbuch.domain.WorkSplitter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class WorkSplitterTest {
    private val sonntag = LocalDate.parse("2026-11-15")
    private val montag = sonntag.plusDays(1)
    private val dienstag = sonntag.plusDays(2)

    private fun shift(
        start: LocalDate, startMin: Int, end: LocalDate?, endMin: Int?,
        running: Boolean = false, kmStart: Int? = null, kmEnd: Int? = null,
    ) = WorkShiftEntity(1, start, startMin, end, endMin, running, kmStart, kmEnd)

    @Test
    fun sonntag2330BisMontag1000WirdAnMitternachtGeteilt() {
        val p = WorkSplitter.split(shift(sonntag, 23 * 60 + 30, montag, 600), LocalDateTime.now())
        assertEquals(2, p.size)
        assertEquals(sonntag, p[0].date)
        assertEquals("23:30–24:00", p[0].label)
        assertEquals(30, p[0].minutes)
        assertTrue(p[0].toNextDay)
        assertEquals(montag, p[1].date)
        assertEquals("00:00–10:00", p[1].label)
        assertEquals(600, p[1].minutes)
        assertTrue(p[1].fromPreviousDay)
    }

    @Test
    fun schichtUeberZweiMitternaechte() {
        val p = WorkSplitter.split(shift(sonntag, 22 * 60, dienstag, 6 * 60), LocalDateTime.now())
        assertEquals(listOf(sonntag, montag, dienstag), p.map { it.date })
        assertEquals(listOf(120, 1440, 360), p.map { it.minutes })
        assertEquals("00:00–24:00", p[1].label)
    }

    @Test
    fun endeGenauUmMitternachtErzeugtKeinenFolgetag() {
        val p = WorkSplitter.split(shift(sonntag, 20 * 60, sonntag, 1440), LocalDateTime.now())
        assertEquals(1, p.size)
        assertEquals("20:00–24:00", p[0].label)
        assertFalse(p[0].toNextDay)
    }

    @Test
    fun laufendeSchichtBisJetzt() {
        val now = montag.atTime(3, 15)
        val p = WorkSplitter.split(shift(sonntag, 23 * 60, null, null, running = true), now)
        assertEquals(2, p.size)
        assertEquals(60, p[0].minutes)
        assertEquals(195, p[1].minutes)
        assertTrue(p[1].running)
        assertEquals("ab 00:00 (läuft)", p[1].label)
    }

    @Test
    fun unvollstaendigeAlteEintraege() {
        val nurBeginn = WorkSplitter.split(shift(sonntag, 6 * 60, null, null), LocalDateTime.now()).single()
        assertEquals("ab 06:00", nurBeginn.label)
        assertNull(nurBeginn.minutes)
        val nurEnde = WorkSplitter.split(WorkShiftEntity(2, sonntag, null, sonntag, 17 * 60), LocalDateTime.now()).single()
        assertEquals("bis 17:00", nurEnde.label)
    }

    @Test
    fun kmWerdenAnteiligVerteiltUndSummeStimmt() {
        val shares = WorkSplitter.distributeKm(shift(sonntag, 23 * 60 + 30, montag, 600, kmStart = 1000, kmEnd = 1600))!!
        assertEquals(2, shares.size)
        // 30 von 630 Minuten am Sonntag → 600 * 30 / 630 = 28,6 → 29 km
        assertEquals(1000, shares[0].kmAtStart)
        assertEquals(1029, shares[0].kmAtEnd)
        assertTrue(shares[0].endEstimated)
        assertEquals(1029, shares[1].kmAtStart)
        assertEquals(1600, shares[1].kmAtEnd)
        assertTrue(shares[1].startEstimated)
        assertEquals(600, shares.sumOf { it.kmAtEnd - it.kmAtStart })
    }

    @Test
    fun kmUeberZweiMitternaechte() {
        val shares = WorkSplitter.distributeKm(shift(sonntag, 22 * 60, dienstag, 6 * 60, kmStart = 2000, kmEnd = 2800))!!
        assertEquals(listOf(2000 to 2050, 2050 to 2650, 2650 to 2800), shares.map { it.kmAtStart to it.kmAtEnd })
        assertEquals(800, shares.sumOf { it.kmAtEnd - it.kmAtStart })
    }

    @Test
    fun ohneKmKeineVerteilung() {
        assertNull(WorkSplitter.distributeKm(shift(sonntag, 600, sonntag, 900, kmStart = 1000)))
        assertNull(WorkSplitter.distributeKm(shift(sonntag, 600, sonntag, 900, kmStart = 1000, kmEnd = 900)))
    }
}
