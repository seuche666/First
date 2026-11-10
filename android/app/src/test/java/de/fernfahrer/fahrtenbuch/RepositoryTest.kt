package de.fernfahrer.fahrtenbuch

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.fernfahrer.fahrtenbuch.data.AppDatabase
import de.fernfahrer.fahrtenbuch.data.DayEntity
import de.fernfahrer.fahrtenbuch.data.Repository
import de.fernfahrer.fahrtenbuch.data.StampResult
import de.fernfahrer.fahrtenbuch.domain.CountingRules
import de.fernfahrer.fahrtenbuch.domain.DayType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: Repository
    private val rules = MutableStateFlow(CountingRules())
    private val today = LocalDate.parse("2026-11-10")

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = Repository(db, rules)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun startTour(date: LocalDate) = repo.saveDay(
        DayEntity(date = date, startMinute = 300, endMinute = 1440, countryCode = "AT", kmStart = 1000, kmEnd = 1500,
            workStartMinute = 300),
        startTour = true, endTour = false, fillGaps = false,
    )

    @Test
    fun arbeitBeginnenUndBeendenOhneTagZuBearbeiten() = runBlocking {
        startTour(today.minusDays(3))

        // Heute gibt es noch keinen Eintrag: wird als Tourtag angelegt, fehlende Tage ergänzt.
        val start = repo.stampWorkTime(today, 6 * 60 + 10, isStart = true)
        assertEquals(StampResult.Saved(370, filledDays = 2), start)
        var heute = repo.getDayByDate(today)!!
        assertEquals(370, heute.workStartMinute)
        assertEquals(null, heute.workEndMinute)
        assertEquals(0, heute.startMinute)
        assertEquals(1440, heute.endMinute)
        assertEquals("AT", heute.countryCode)
        assertNotNull(heute.tourId)
        assertNotNull(repo.getDayByDate(today.minusDays(1)))
        assertNotNull(repo.getDayByDate(today.minusDays(2)))

        // Arbeitsende ändert nur das Arbeitsende.
        val end = repo.stampWorkTime(today, 17 * 60 + 45, isStart = false)
        assertEquals(StampResult.Saved(1065, filledDays = 0), end)
        heute = repo.getDayByDate(today)!!
        assertEquals(370, heute.workStartMinute)
        assertEquals(1065, heute.workEndMinute)
        assertEquals(1440, heute.endMinute)

        // Steuer-Einordnung bleibt: Tag mitten in der Tour = voller Tag
        val c = repo.classifiedDays.first().first { it.day.date == today }
        assertEquals(DayType.FULL, c.type)
    }

    @Test
    fun ohneTourKeineArbeitszeitPerKnopf() = runBlocking {
        assertEquals(StampResult.NoActiveTour, repo.stampWorkTime(today, 400, isStart = true))
        assertEquals(null, repo.getDayByDate(today))
    }

    @Test
    fun firmenregelWirktSofortAufDieEinordnung() = runBlocking {
        repo.saveDay(
            DayEntity(date = today, startMinute = 8 * 60, endMinute = 12 * 60, countryCode = "DE"),
            startTour = false, endTour = false, fillGaps = false,
        )
        assertEquals(DayType.NONE, repo.classifiedDays.first().single().type)
        rules.value = CountingRules(shortSingleDayAsHalf = true)
        val c = repo.classifiedDays.first().single()
        assertEquals(DayType.HALF, c.type)
        assertTrue(c.byCompanyRule)
    }
}
