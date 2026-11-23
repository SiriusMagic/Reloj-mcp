package com.zirakusa.reloj.data

import java.time.DayOfWeek

/**
 * Orders from other apps and assistants, with Android's standard «poner alarma» / «poner temporizador» (FAMILIA.md §10).
 * Pure; unit tested.
 */
object Orders {
    /** Android counts days 1 = Sunday … 7 = Saturday (java.util.Calendar). */
    fun day(calendarDay: Int): DayOfWeek? = calendarDay.takeIf { it in 1..7 }?.let { DayOfWeek.of((it + 5) % 7 + 1) }

    /**
     * The alarm to save for an order: the same time, label and days as one already there turns that one on instead of
     * making a copy. Null when the time is not valid.
     */
    fun alarm(hour: Int, minute: Int, label: String?, days: List<Int>?, existing: List<Alarm>): Alarm? {
        if (hour !in 0..23 || minute !in 0..59) return null
        val set = days.orEmpty().mapNotNull(::day).toSet()
        val name = label.orEmpty().trim().take(Alarm.LABEL_MAX)
        existing.firstOrNull { it.hour == hour && it.minute == minute && it.label == name && it.days == set && it.date == null }?.let {
            return it.copy(enabled = true, snoozedUntil = 0, snoozed = 0)
        }
        return Alarm(hour = hour, minute = minute, label = name, days = set)
    }

    /** A timer's length in ms from the order's seconds (1 s – 24 h), or null. */
    fun timer(seconds: Int): Long? = seconds.takeIf { it in 1..24 * 3600 }?.let { it * 1000L }
}
