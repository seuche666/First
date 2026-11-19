package de.fernfahrer.fahrtenbuch

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.fernfahrer.fahrtenbuch.data.AppDatabase
import de.fernfahrer.fahrtenbuch.data.DayEntity
import de.fernfahrer.fahrtenbuch.data.Repository
import de.fernfahrer.fahrtenbuch.data.Settings
import de.fernfahrer.fahrtenbuch.data.WorkSettings
import de.fernfahrer.fahrtenbuch.domain.Classifier
import de.fernfahrer.fahrtenbuch.domain.DayType
import de.fernfahrer.fahrtenbuch.domain.Nights
import de.fernfahrer.fahrtenbuch.export.TravelReportSource
import de.fernfahrer.fahrtenbuch.location.LocationProvider
import de.fernfahrer.fahrtenbuch.work.ReminderScheduler
import de.fernfahrer.fahrtenbuch.work.WorkController
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Übernachtung im LKW über die echten Abläufe „Arbeit beenden“ und „Tour beenden“:
 * Die Nacht hängt nur an „Übernachtung im LKW“, nicht am Kartenpunkt-Haken.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OvernightRuleTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: AppDatabase
    private lateinit var repo: Repository
    private lateinit var reminders: ReminderScheduler
    private lateinit var controller: WorkController
    private val mon = LocalDate.of(2026, 11, 9)
    private val tue = mon.plusDays(1)
    private var now: LocalDateTime = mon.atTime(6, 0)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val settings = Settings(context)
        settings.setWorkSettings(WorkSettings(reminderEnabled = false, reminderHours = 15, gpsEnabled = false))
        reminders = ReminderScheduler(context, settings)
        repo = Repository(db, settings.rules)
        controller = WorkController(repo, settings, reminders, LocationProvider(context), clock = { now })
    }

    @After
    fun tearDown() {
        reminders.cancel()
        db.close()
    }

    private suspend fun day(date: LocalDate) = repo.classifiedDaysNow().single { it.day.date == date }

    @Test
    fun nachtZaehltOhneKartenpunktUndEntfaelltBeiTourBeenden() = runBlocking {
        // Montag: Tour beginnt, Arbeit 06:00–18:00, Haken „Übernachtungsplatz auf der Karte“ NICHT gesetzt
        repo.saveDay(DayEntity(date = mon, startMinute = 360, endMinute = 1440, countryCode = "DE"), startTour = true, endTour = false, fillGaps = false)
        controller.startWork(mon.atTime(6, 0))
        now = mon.atTime(18, 0)
        controller.endWork(now, km = null, savePlace = false)
        assertTrue("kein Kartenpunkt gespeichert", repo.getOvernightPlaces().isEmpty())
        val monday = day(mon)
        assertTrue("Abfahrtstag: Übernachtung im LKW", monday.overnight)
        assertEquals(setOf(mon), Nights.dates(repo.classifiedDaysNow()))

        // Dienstag: Arbeit 06:00, dann „Tour beenden“ am selben Tag → Ankunftstag ohne Nacht
        now = tue.atTime(6, 0)
        controller.startWork(now)
        assertTrue("mitten in der laufenden Tour", day(tue).overnight)
        now = tue.atTime(16, 0)
        controller.endTour(now, km = null)
        val tuesday = day(tue)
        assertFalse("Ankunftstag ohne Übernachtung, ohne Haken zu entfernen", tuesday.overnight)
        assertEquals(DayType.HALF, tuesday.type)
        assertEquals(setOf(mon), Nights.dates(repo.classifiedDaysNow()))

        // In der Reisekostenabrechnung genauso
        val company = Classifier.classifyAll(repo.getAllDays(), repo.getAllTours(), TravelReportSource.COMPANY_RULES)
        assertEquals(setOf(mon), Nights.dates(company))
    }
}
