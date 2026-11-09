package de.fernfahrer.fahrtenbuch.data

import androidx.room.withTransaction
import de.fernfahrer.fahrtenbuch.domain.ClassifiedDay
import de.fernfahrer.fahrtenbuch.domain.Classifier
import de.fernfahrer.fahrtenbuch.domain.Stats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate

class Repository(private val db: AppDatabase) {
    private val dao = db.dao()

    /** Alle Tage mit Einordnung, neueste zuerst. */
    val classifiedDays: Flow<List<ClassifiedDay>> =
        combine(dao.observeDays(), dao.observeTours()) { days, tours -> Classifier.classifyAll(days, tours) }

    val tours: Flow<List<TourEntity>> = dao.observeTours()

    fun recentCountries(limit: Int = 6): Flow<List<String>> = dao.observeRecentCountries(limit)

    suspend fun getDay(id: Long) = dao.getDay(id)
    suspend fun getDayByDate(date: LocalDate) = dao.getDayByDate(date)
    suspend fun getDayBefore(date: LocalDate) = dao.getDayBefore(date)
    suspend fun getLatestSingleDay() = dao.getLatestSingleDay()
    suspend fun getActiveTour() = dao.getActiveTour()
    suspend fun getTour(id: Long) = dao.getTour(id)
    suspend fun getDaysOfTour(tourId: Long) = dao.getDaysOfTour(tourId)

    /**
     * Speichert einen Tag.
     *
     * @param startTour legt eine neue Tour an und ordnet den Tag ihr zu.
     * @param endTour beendet die Tour des Tages.
     * @param fillGaps ergänzt fehlende Tage der Tour vor diesem Tag als volle Tage.
     */
    suspend fun saveDay(day: DayEntity, startTour: Boolean, endTour: Boolean, fillGaps: Boolean): Long =
        db.withTransaction {
            var tourId = day.tourId
            if (startTour && tourId == null) tourId = dao.insertTour(TourEntity())
            val toSave = day.copy(tourId = tourId)
            val id = if (toSave.id == 0L) {
                dao.insertDay(toSave)
            } else {
                dao.updateDay(toSave)
                toSave.id
            }
            if (tourId != null) {
                if (fillGaps) fillTourGaps(tourId, toSave.date)
                if (endTour) dao.getTour(tourId)?.let { dao.updateTour(it.copy(ended = true)) }
            }
            id
        }

    /** Tage zwischen Tourbeginn und [upTo] (exklusiv), für die es noch keinen Eintrag gibt. */
    suspend fun missingTourDates(tourId: Long, upTo: LocalDate): List<LocalDate> {
        val first = dao.getDaysOfTour(tourId).minOfOrNull { it.date } ?: return emptyList()
        val missing = mutableListOf<LocalDate>()
        var d = first.plusDays(1)
        while (d.isBefore(upTo)) {
            if (dao.getDayByDate(d) == null) missing += d
            d = d.plusDays(1)
        }
        return missing
    }

    private suspend fun fillTourGaps(tourId: Long, upTo: LocalDate) {
        for (date in missingTourDates(tourId, upTo)) {
            val previous = dao.getDayBefore(date)
            dao.insertDay(
                DayEntity(
                    date = date,
                    startMinute = 0,
                    endMinute = 24 * 60,
                    countryCode = previous?.countryCode ?: Stats.HOME_COUNTRY,
                    note = "Automatisch ergänzt",
                    tourId = tourId,
                ),
            )
        }
    }

    /** Löscht einen Tag. Eine Tour ohne Tage wird ebenfalls gelöscht. */
    suspend fun deleteDay(day: DayEntity) = db.withTransaction {
        dao.deleteDay(day)
        day.tourId?.let { tourId ->
            if (dao.getDaysOfTour(tourId).isEmpty()) dao.deleteTour(tourId)
        }
    }

    suspend fun exportData(): BackupData = BackupData(dao.getAllTours(), dao.getAllDays())

    suspend fun replaceAll(data: BackupData) = db.withTransaction {
        dao.deleteAllDays()
        dao.deleteAllTours()
        dao.insertTours(data.tours)
        dao.insertDays(data.days)
    }
}
