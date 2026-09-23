package com.yash.tracker.domain.diary

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Decides which diary day a moment belongs to. A 1 a.m. snack is part of the night before,
 * so anything earlier than the profile's day-start hour counts toward the previous date.
 */
object DiaryDate {

    private val FORMAT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun resolve(instant: Instant, zone: ZoneId, dayStartHour: Int): LocalDate {
        val local = LocalDateTime.ofInstant(instant, zone)
        return if (local.hour < dayStartHour) local.toLocalDate().minusDays(1) else local.toLocalDate()
    }

    fun format(date: LocalDate): String = date.format(FORMAT)

    fun resolveKey(instant: Instant, zone: ZoneId, dayStartHour: Int): String =
        format(resolve(instant, zone, dayStartHour))

    fun parse(key: String): LocalDate = LocalDate.parse(key, FORMAT)
}
