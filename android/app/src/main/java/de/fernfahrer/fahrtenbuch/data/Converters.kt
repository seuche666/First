package de.fernfahrer.fahrtenbuch.data

import androidx.room.TypeConverter
import de.fernfahrer.fahrtenbuch.domain.DayType
import java.time.LocalDate

class Converters {
    @TypeConverter
    fun dateToString(date: LocalDate?): String? = date?.toString()

    @TypeConverter
    fun stringToDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

    @TypeConverter
    fun dayTypeToString(type: DayType?): String? = type?.name

    @TypeConverter
    fun stringToDayType(value: String?): DayType? =
        value?.let { v -> DayType.entries.firstOrNull { it.name == v } }
}
