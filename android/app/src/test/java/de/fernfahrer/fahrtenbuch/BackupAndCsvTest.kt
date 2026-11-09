package de.fernfahrer.fahrtenbuch

import de.fernfahrer.fahrtenbuch.data.Backup
import de.fernfahrer.fahrtenbuch.data.BackupData
import de.fernfahrer.fahrtenbuch.data.BackupFormatException
import de.fernfahrer.fahrtenbuch.data.DayEntity
import de.fernfahrer.fahrtenbuch.data.TourEntity
import de.fernfahrer.fahrtenbuch.domain.Classifier
import de.fernfahrer.fahrtenbuch.domain.DayType
import de.fernfahrer.fahrtenbuch.domain.Stats
import de.fernfahrer.fahrtenbuch.export.CsvExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BackupAndCsvTest {
    private val data = BackupData(
        tours = listOf(TourEntity(3, ended = true)),
        days = listOf(
            DayEntity(1, LocalDate.parse("2026-05-04"), 300, 1440, "FR", 100, 700, "Lyon; \"Kühl\"", tourId = 3),
            DayEntity(2, LocalDate.parse("2026-05-05"), 0, 900, "DE", null, null, "", tourId = 3, dayTypeOverride = DayType.FULL),
        ),
        driverName = "Max Muster",
    )

    @Test
    fun sicherungHinUndZurueck() {
        val restored = Backup.fromJson(Backup.toJson(data))
        assertEquals(data, restored)
    }

    @Test(expected = BackupFormatException::class)
    fun fremdeDateiWirdAbgelehnt() {
        Backup.fromJson("{\"app\":\"etwas-anderes\"}")
    }

    @Test
    fun csvMitSemikolonUndAnfuehrungszeichen() {
        val classified = Classifier.classifyAll(data.days, data.tours)
        val csv = CsvExporter.build(2026, classified, Stats.summarize(classified), "Max")
        assertTrue(csv.startsWith("﻿"))
        assertTrue(csv.contains("04.05.2026;Mo;Frankreich;FR;05:00;24:00;19:00;Halber Tag;;ja;100;700;600;\"Lyon; \"\"Kühl\"\"\""))
        assertTrue(csv.contains("05.05.2026;Di;Deutschland;DE;00:00;15:00;15:00;Voller Tag;ja;ja;;;;"))
    }
}
