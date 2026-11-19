package de.fernfahrer.fahrtenbuch

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.fernfahrer.fahrtenbuch.data.AppDatabase
import de.fernfahrer.fahrtenbuch.data.Backup
import de.fernfahrer.fahrtenbuch.data.BackupData
import de.fernfahrer.fahrtenbuch.data.DayEntity
import de.fernfahrer.fahrtenbuch.data.ExpenseRateEntity
import de.fernfahrer.fahrtenbuch.data.ExpenseRepository
import de.fernfahrer.fahrtenbuch.data.ExpenseYear
import de.fernfahrer.fahrtenbuch.data.ExpenseYearEntity
import de.fernfahrer.fahrtenbuch.data.Repository
import de.fernfahrer.fahrtenbuch.data.TourEntity
import de.fernfahrer.fahrtenbuch.domain.Classifier
import de.fernfahrer.fahrtenbuch.domain.CountingRules
import de.fernfahrer.fahrtenbuch.domain.Euro
import de.fernfahrer.fahrtenbuch.domain.ExpenseRate
import de.fernfahrer.fahrtenbuch.domain.ExpenseTables
import de.fernfahrer.fahrtenbuch.domain.MissingRate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExpenseTableTest {
    private lateinit var db: AppDatabase
    private lateinit var expenses: ExpenseRepository
    private lateinit var repo: Repository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        expenses = ExpenseRepository(db)
        repo = Repository(db, MutableStateFlow(CountingRules()))
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun euroBetraegeMitKomma() {
        assertEquals(1400L, Euro.parse("14,00"))
        assertEquals(1400L, Euro.parse("14"))
        assertEquals(1450L, Euro.parse("14,5"))
        assertEquals(1405L, Euro.parse("14.05"))
        assertEquals(2800L, Euro.parse(" 28,00 € "))
        assertNull(Euro.parse(""))
        assertFalse(Euro.isValid("14,005"))
        assertFalse(Euro.isValid("abc"))
        assertFalse(Euro.isValid("-3"))
        assertEquals("14,00", Euro.format(1400))
        assertEquals("0,05", Euro.format(5))
        assertEquals("–", Euro.label(null))
        assertEquals("32,00 €", Euro.label(3200))
    }

    @Test
    fun allesStartetLeer() = runBlocking {
        assertTrue(expenses.getAll().isEmpty())
        assertTrue(expenses.ensureYear(2026))
        val y = expenses.getYear(2026)!!
        assertNull(y.overnightCents)
        assertTrue(expenses.getRates(2026).isEmpty())
        assertFalse("zweiter Aufruf ändert nichts", expenses.ensureYear(2026))
    }

    @Test
    fun jahreswechselKopiertVorjahrUndJahreBleibenUnabhaengig() = runBlocking {
        expenses.setOvernight(2026, 800)
        expenses.saveRate(ExpenseRateEntity(2026, "DE", 2800, 1400))
        expenses.saveRate(ExpenseRateEntity(2026, "NL", 3200, null))

        // Jahreswechsel: 2027 wird beim ersten Öffnen aus 2026 kopiert
        assertTrue(expenses.ensureYear(2027))
        assertEquals(800L, expenses.getYear(2027)!!.overnightCents)
        assertEquals(
            listOf(ExpenseRateEntity(2027, "DE", 2800, 1400), ExpenseRateEntity(2027, "NL", 3200, null)),
            expenses.getRates(2027),
        )

        // Änderungen in 2027 wirken nicht auf 2026 …
        expenses.saveRate(ExpenseRateEntity(2027, "NL", 3300, 2200))
        expenses.deleteRate(2027, "DE")
        expenses.setOvernight(2027, 900)
        assertEquals(800L, expenses.getYear(2026)!!.overnightCents)
        assertEquals(listOf(ExpenseRateEntity(2026, "DE", 2800, 1400), ExpenseRateEntity(2026, "NL", 3200, null)), expenses.getRates(2026))
        // … und Änderungen in 2026 nicht auf 2027
        expenses.saveRate(ExpenseRateEntity(2026, "AT", 2700, 1400))
        assertEquals(listOf(ExpenseRateEntity(2027, "NL", 3300, 2200)), expenses.getRates(2027))
        assertFalse(expenses.ensureYear(2027))
        assertEquals(900L, expenses.getYear(2027)!!.overnightCents)

        // Übersprungenes Jahr: 2029 kopiert vom jüngsten früheren Jahr (2027)
        assertTrue(expenses.ensureYear(2029))
        assertEquals(listOf(ExpenseRateEntity(2029, "NL", 3300, 2200)), expenses.getRates(2029))
        // Früheres Jahr ohne Vorgänger startet leer
        assertTrue(expenses.ensureYear(2025))
        assertTrue(expenses.getRates(2025).isEmpty())
        assertNull(expenses.getYear(2025)!!.overnightCents)
    }

    @Test
    fun auswaehlbareJahreUndKopierQuelle() {
        val today = LocalDate.parse("2026-11-19")
        assertEquals(listOf(2024, 2026, 2027), ExpenseTables.selectableYears(emptyList(), listOf(2024, 2026), today))
        assertEquals(2026, ExpenseTables.sourceYear(2027, listOf(2025, 2026)))
        assertNull(ExpenseTables.sourceYear(2025, listOf(2026)))
    }

    @Test
    fun hinweisFuerLaenderOhneBetrag() {
        val mo = LocalDate.parse("2026-11-16")
        val days = listOf(
            DayEntity(1, mo, 300, 1440, "DE", null, null, tourId = 1),
            DayEntity(2, mo.plusDays(1), 0, 1440, "DE", null, null, tourId = 1).withCountries(listOf("DE", "NL")),
            DayEntity(3, mo.plusDays(2), 0, 1440, "BE", null, null, tourId = 1).withCountries(listOf("BE", "FR")),
            DayEntity(4, mo.plusDays(3), 0, 900, "DE", null, null, tourId = 1),
            DayEntity(5, LocalDate.parse("2025-05-05"), 0, 1440, "PL", null, null),
        )
        val classified = Classifier.classifyAll(days, listOf(TourEntity(1, ended = true)))
        val rates = listOf(
            ExpenseRate("DE", null, 1400), // nur halbe Tage in DE → Betrag vorhanden
            ExpenseRate("NL", null, 2100), // voller Tag in NL, aber kein Betrag dafür
        )
        val missing = ExpenseTables.missingRates(classified, 2026, rates)
        assertEquals(listOf(MissingRate("FR", 1, 0), MissingRate("NL", 1, 0)), missing)
        // Belgien taucht nicht auf: an dem Tag zählt Frankreich (zuletzt besucht)
        assertTrue(missing.none { it.countryCode == "BE" })
        // Polen (2025) zählt nicht für 2026
        assertTrue(ExpenseTables.missingRates(classified, 2026, emptyList()).none { it.countryCode == "PL" })
        assertEquals(listOf(MissingRate("PL", 0, 1)), ExpenseTables.missingRates(classified, 2025, emptyList())) // eintägig = halber Tag
    }

    @Test
    fun sicherungMitSpesentabelleUndName() = runBlocking {
        expenses.setOvernight(2026, 800)
        expenses.saveRate(ExpenseRateEntity(2026, "DE", 2800, 1400))
        expenses.saveRate(ExpenseRateEntity(2026, "NL", 3200, null))
        expenses.ensureYear(2027)
        expenses.saveRate(ExpenseRateEntity(2027, "NL", 3300, 2200))

        val exported = repo.exportData().copy(driverName = "Max Muster")
        val json = Backup.toJson(exported)
        assertEquals(Backup.VERSION, org.json.JSONObject(json).getInt("version"))
        assertEquals(7, Backup.VERSION)
        val restored = Backup.fromJson(json)
        assertEquals(exported, restored)
        assertEquals("Max Muster", restored.driverName)

        // Auf einem anderen Handy wiederherstellen
        val other = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val otherExpenses = ExpenseRepository(other)
            otherExpenses.saveRate(ExpenseRateEntity(2020, "AT", 1, 1)) // wird ersetzt
            Repository(other, MutableStateFlow(CountingRules())).replaceAll(restored)
            assertEquals(expenses.getAll(), otherExpenses.getAll())
            assertEquals(listOf(2026, 2027), otherExpenses.getAll().map { it.year.year })
            assertEquals(800L, otherExpenses.getYear(2027)!!.overnightCents)
        } finally {
            other.close()
        }
    }

    /** Ältere Sicherung (Format 6) ohne Spesentabelle: vorhandene Tabellen bleiben erhalten. */
    @Test
    fun alteSicherungOhneSpesentabelleBehaeltVorhandene() = runBlocking {
        expenses.saveRate(ExpenseRateEntity(2026, "DE", 2800, 1400))
        val v6 = org.json.JSONObject(Backup.toJson(BackupData(emptyList(), emptyList(), driverName = "Max"))).apply {
            put("version", 6)
            remove("expenseTables")
        }.toString()
        val restored = Backup.fromJson(v6)
        assertNull(restored.expenseYears)
        repo.replaceAll(restored)
        assertEquals(listOf(ExpenseRateEntity(2026, "DE", 2800, 1400)), expenses.getRates(2026))
    }

    @Test(expected = de.fernfahrer.fahrtenbuch.data.BackupFormatException::class)
    fun beschaedigteSpesentabelleWirdAbgelehnt() {
        val json = org.json.JSONObject(Backup.toJson(BackupData(emptyList(), emptyList(), expenseYears = listOf(ExpenseYear(ExpenseYearEntity(2026), emptyList()))))).apply {
            getJSONArray("expenseTables").getJSONObject(0).put(
                "rates", org.json.JSONArray().put(org.json.JSONObject().put("countryCode", "NL").put("fullDayCents", -5)),
            )
        }.toString()
        Backup.fromJson(json)
    }
}
