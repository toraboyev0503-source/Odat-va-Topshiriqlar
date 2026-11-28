package com.memora.app.util

import com.memora.app.prefs.AppLanguage
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

data class TimeRange(val start: Long?, val end: Long?)

object TimeUtils {
    fun locale(language: AppLanguage): Locale = Locale.forLanguageTag(language.tag)

    fun dateTime(epochMillis: Long, language: AppLanguage): String {
        val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(locale(language))
        return Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .format(formatter)
    }

    fun monthName(year: Int, month: Int, language: AppLanguage): String {
        val date = LocalDate.of(year, month, 1)
        val formatter = DateTimeFormatter.ofPattern("LLLL yyyy", locale(language))
        return date.format(formatter)
    }

    fun yearRange(year: Int): TimeRange {
        val zone = ZoneId.systemDefault()
        val start = LocalDate.of(year, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = LocalDate.of(year + 1, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        return TimeRange(start, end)
    }

    fun monthRange(year: Int, month: Int): TimeRange {
        val zone = ZoneId.systemDefault()
        val startDate = LocalDate.of(year, month, 1)
        val endDate = startDate.plusMonths(1)
        return TimeRange(
            startDate.atStartOfDay(zone).toInstant().toEpochMilli(),
            endDate.atStartOfDay(zone).toInstant().toEpochMilli()
        )
    }


    fun dayRange(date: LocalDate): TimeRange {
        val zone = ZoneId.systemDefault()
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return TimeRange(start, end)
    }

    fun currentDayRange(): TimeRange = dayRange(LocalDate.now())

    fun currentMonthRange(): TimeRange {
        val now = LocalDate.now()
        return monthRange(now.year, now.monthValue)
    }

    fun currentYearRange(): TimeRange = yearRange(LocalDate.now().year)

    fun previousMonthRange(): Triple<Int, Int, TimeRange> {
        val date = LocalDate.now().withDayOfMonth(1).minusMonths(1)
        return Triple(date.year, date.monthValue, monthRange(date.year, date.monthValue))
    }
}
