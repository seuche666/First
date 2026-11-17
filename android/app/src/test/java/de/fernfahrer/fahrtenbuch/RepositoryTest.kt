package de.fernfahrer.fahrtenbuch

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.fernfahrer.fahrtenbuch.data.AppDatabase
import de.fernfahrer.fahrtenbuch.data.DayEntity
import de.fernfahrer.fahrtenbuch.data.EndTourResult
import de.fernfahrer.fahrtenbuch.data.Repository
import de.fernfahrer.fahrtenbuch.data.StartResult
import de.fernfahrer.fahrtenbuch.domain.CountingRules
import de.fernfahrer.fahrtenbuch.domain.DayType
import de.fernfahrer.fahrtenbuch.domain.Stats
import de.fernfahrer.fahrtenbuch.domain.WorkSplitter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: Repository
    private val rules = MutableStateFlow(CountingRules())

    private val samstag = LocalDate.parse("2026-11-14")
    private val sonntag = LocalDate.parse("2026-11-15")
    private val montag = LocalDate.parse("2026-11-16")
    private val dienstag = LocalDate.parse("2026-11-17")

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = Repository(db, rules)
    }

    @After
    fun tearDown() = db.close()

    /** Vorher bekannter Tag mit km-Stand abends (letzter bekannter km-Stand). */
    private suspend fun vortag(date: LocalDate, kmEnd: Int, country: String = "DE") = repo.saveDay(
        DayEntity(date = date, startMinute = 360, endMinute = 1080, countryCode = country, kmStart = kmEnd - 300, kmEnd = kmEnd),
        startTour = false, endTour = false, fillGaps = false,
    )

    private suspend fun kmSumme(vararg dates: LocalDate) = dates.sumOf { repo.getDayByDate(it)!!.distanceKm ?: 0 }

    @Test
    fun schichtSonntag2330BisMontag1000() = runBlocking {
        vortag(samstag, kmEnd = 1000, country = "AT")

        val start = repo.startWork(sonntag.atTime(23, 30))
        assertTrue(start is StartResult.Started)
        val shift = (start as StartResult.Started).shift
        assertEquals(1000, shift.kmStart) // Start-km automatisch vom letzten bekannten km-Stand

        // Kein Tippen um Mitternacht nötig: Arbeit beenden legt den Montag selbst an und teilt auf.
        val ended = repo.endWork(montag.atTime(10, 0), km = 1600)!!
        assertNull(repo.getRunningShift())
        assertEquals(montag, ended.endDate)
        assertEquals(600, ended.endMinute)

        val so = repo.getDayByDate(sonntag)!!
        val mo = repo.getDayByDate(montag)!!
        // Abwesenheit als Vorschlag aus der Arbeitszeit
        assertEquals(23 * 60 + 30, so.startMinute)
        assertEquals(1440, so.endMinute)
        assertEquals(0, mo.startMinute)
        assertEquals(600, mo.endMinute)
        // Land wie am Vortag, nicht von Hand gewählt (GPS darf verbessern)
        assertEquals("AT", mo.countryCode)
        assertFalse(mo.countryManual)

        // Arbeitszeit geteilt: Sonntag 23:30–24:00, Montag 00:00–10:00
        val work = WorkSplitter.byDate(repo.getAllShifts(), LocalDateTime.now())
        assertEquals("23:30–24:00", WorkSplitter.label(work[sonntag]))
        assertEquals("00:00–10:00", WorkSplitter.label(work[montag]))

        // km: Folgetag hat km, Summe genau 600 – nicht doppelt, nicht gar nicht
        assertEquals(1000, so.kmStart)
        assertEquals(1029, so.kmEnd)
        assertEquals(1029, mo.kmStart)
        assertEquals(1600, mo.kmEnd)
        assertTrue(mo.kmInterpolated)
        assertEquals(600, kmSumme(sonntag, montag))
        val all = repo.classifiedDays.first()
        val summary = Stats.summarize(all, null, work)
        assertEquals(300 + 600, summary.km) // Samstag 300 + Schicht 600
        assertEquals(630, WorkSplitter.minutes(work[sonntag]) + WorkSplitter.minutes(work[montag]))

        // Der eingegebene Wert ist der Start-km für den nächsten Arbeitsbeginn
        assertEquals(1600, repo.lastKnownKm())
    }

    @Test
    fun appOeffnenNachMitternachtLegtNeuenTagAn() = runBlocking {
        vortag(samstag, kmEnd = 1000)
        repo.startWork(sonntag.atTime(23, 30))
        val created = repo.syncRunningWork(montag.atTime(6, 0))
        assertEquals(listOf(montag), created)
        val mo = repo.getDayByDate(montag)!!
        assertEquals(0, mo.startMinute)
        assertNull(mo.kmStart) // km erst beim Arbeitsende (anteilig)
        assertNotNull(repo.getRunningShift())
    }

    @Test
    fun schichtUeberZweiMitternaechte() = runBlocking {
        vortag(samstag, kmEnd = 2000)
        repo.startWork(sonntag.atTime(22, 0))
        repo.endWork(dienstag.atTime(6, 0), km = 2800)

        val work = WorkSplitter.byDate(repo.getAllShifts(), LocalDateTime.now())
        assertEquals("22:00–24:00", WorkSplitter.label(work[sonntag]))
        assertEquals("00:00–24:00", WorkSplitter.label(work[montag]))
        assertEquals("00:00–06:00", WorkSplitter.label(work[dienstag]))

        val so = repo.getDayByDate(sonntag)!!
        val mo = repo.getDayByDate(montag)!!
        val di = repo.getDayByDate(dienstag)!!
        assertEquals(2000 to 2050, so.kmStart to so.kmEnd)
        assertEquals(2050 to 2650, mo.kmStart to mo.kmEnd)
        assertEquals(2650 to 2800, di.kmStart to di.kmEnd)
        assertEquals(800, kmSumme(sonntag, montag, dienstag))
        assertEquals(0 to 1440, mo.startMinute to mo.endMinute)
        assertEquals(0 to 360, di.startMinute to di.endMinute)
    }

    @Test
    fun zweiSchichtenAmSelbenTagWerdenNichtDoppeltGezaehlt() = runBlocking {
        vortag(samstag, kmEnd = 1000)
        repo.startWork(sonntag.atTime(23, 30))
        repo.endWork(montag.atTime(10, 0), km = 1600)
        // Nächste Nachtschicht Montag 22:00 bis Dienstag 08:00
        val second = (repo.startWork(montag.atTime(22, 0)) as StartResult.Started).shift
        assertEquals(1600, second.kmStart)
        repo.endWork(dienstag.atTime(8, 0), km = 2100)

        val work = WorkSplitter.byDate(repo.getAllShifts(), LocalDateTime.now())
        assertEquals("00:00–10:00, 22:00–24:00", WorkSplitter.label(work[montag]))
        assertEquals(1029, repo.getDayByDate(montag)!!.kmStart)
        assertEquals(1100, kmSumme(sonntag, montag, dienstag)) // 2100 - 1000
    }

    @Test
    fun kmNachtraeglichKorrigieren() = runBlocking {
        vortag(samstag, kmEnd = 1000)
        repo.startWork(sonntag.atTime(23, 30))
        val ended = repo.endWork(montag.atTime(10, 0), km = 1600)!!
        repo.saveShift(ended.copy(kmEnd = 1500))
        assertEquals(1024, repo.getDayByDate(sonntag)!!.kmEnd)
        assertEquals(1024, repo.getDayByDate(montag)!!.kmStart)
        assertEquals(500, kmSumme(sonntag, montag))
    }

    @Test
    fun spaeterOhneKmBeendetTrotzdem() = runBlocking {
        vortag(samstag, kmEnd = 1000)
        repo.startWork(sonntag.atTime(8, 0))
        val ended = repo.endWork(sonntag.atTime(17, 0), km = null)!!
        assertFalse(ended.running)
        assertNull(ended.kmEnd)
        assertNull(repo.getDayByDate(sonntag)!!.kmEnd)
        assertEquals(17 * 60, repo.getDayByDate(sonntag)!!.endMinute)
    }

    @Test
    fun tourBeendenWaehrendArbeitLaeuft() = runBlocking {
        // Tour startet Freitag
        val freitag = sonntag.minusDays(2)
        repo.saveDay(
            DayEntity(date = freitag, startMinute = 300, endMinute = 1440, countryCode = "DE", kmStart = 5000),
            startTour = true, endTour = false, fillGaps = false,
        )
        repo.startWork(freitag.atTime(5, 0))
        repo.syncRunningWork(samstag.atTime(12, 0))
        assertEquals(0 to 1440, repo.getDayByDate(samstag)!!.let { it.startMinute to it.endMinute })

        val result = repo.endTour(sonntag.atTime(14, 0), km = 6200)
        assertTrue(result is EndTourResult.Ended)
        val ended = (result as EndTourResult.Ended).endedShift!!
        assertEquals(6200, ended.kmEnd) // nur einmal nach km gefragt – gilt für Arbeit und Tour
        assertNull(repo.getRunningShift())
        assertNull(repo.getActiveTour())

        val so = repo.getDayByDate(sonntag)!!
        assertEquals(14 * 60, so.endMinute)
        assertEquals(6200, so.kmEnd)
        assertEquals(1200, kmSumme(freitag, samstag, sonntag))

        val days = repo.classifiedDays.first().associateBy { it.day.date }
        assertEquals(DayType.HALF, days[freitag]!!.type)
        assertEquals(DayType.FULL, days[samstag]!!.type)
        assertEquals(DayType.HALF, days[sonntag]!!.type)
        // Übernachtung im LKW: Freitag- und Samstagnacht, nicht am Rückreisetag
        assertTrue(days[freitag]!!.overnight)
        assertTrue(days[samstag]!!.overnight)
        assertFalse(days[sonntag]!!.overnight)
    }

    @Test
    fun gpsLandNurOhneHandauswahl() = runBlocking {
        vortag(samstag, kmEnd = 1000)
        repo.startWork(sonntag.atTime(6, 0))
        assertTrue(repo.applyDetectedCountry(sonntag, "AT"))
        assertEquals("AT", repo.getDayByDate(sonntag)!!.countryCode)
        // Von Hand gewählt: GPS ändert nichts
        repo.saveDay(repo.getDayByDate(sonntag)!!.copy(countryCode = "DE", countryManual = true), false, false, false)
        assertFalse(repo.applyDetectedCountry(sonntag, "FR"))
        assertEquals("DE", repo.getDayByDate(sonntag)!!.countryCode)
    }

    @Test
    fun nurEineLaufendeArbeitszeit() = runBlocking {
        repo.startWork(sonntag.atTime(6, 0))
        assertTrue(repo.startWork(sonntag.atTime(7, 0)) is StartResult.AlreadyRunning)
        assertEquals(1, repo.getAllShifts().size)
    }

    @Test
    fun firmenregelWirktSofortAufDieEinordnung() = runBlocking {
        repo.saveDay(
            DayEntity(date = sonntag, startMinute = 8 * 60, endMinute = 12 * 60, countryCode = "DE"),
            startTour = false, endTour = false, fillGaps = false,
        )
        assertEquals(DayType.NONE, repo.classifiedDays.first().single().type)
        rules.value = CountingRules(shortSingleDayAsHalf = true)
        val c = repo.classifiedDays.first().single()
        assertEquals(DayType.HALF, c.type)
        assertTrue(c.byCompanyRule)
    }
}
