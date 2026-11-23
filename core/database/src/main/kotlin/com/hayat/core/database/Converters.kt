package com.hayat.core.database

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Instants as epoch millis (UTC); local dates/times as ISO-8601 text. See docs/database.md. */
class Converters {
    @TypeConverter fun instantToLong(v: Instant?): Long? = v?.toEpochMilli()
    @TypeConverter fun longToInstant(v: Long?): Instant? = v?.let(Instant::ofEpochMilli)
    @TypeConverter fun dateToString(v: LocalDate?): String? = v?.toString()
    @TypeConverter fun stringToDate(v: String?): LocalDate? = v?.let(LocalDate::parse)
    @TypeConverter fun timeToString(v: LocalTime?): String? = v?.withNano(0)?.toString()
    @TypeConverter fun stringToTime(v: String?): LocalTime? = v?.let(LocalTime::parse)
    @TypeConverter fun dateTimeToString(v: LocalDateTime?): String? = v?.withNano(0)?.toString()
    @TypeConverter fun stringToDateTime(v: String?): LocalDateTime? = v?.let(LocalDateTime::parse)
}
