package com.zirakusa.reloj.data

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/** «La alarma sonará en…»: whole days (only when a day or more away), else hours and minutes. */
data class Until(val days: Long, val hours: Long, val minutes: Long)

/** How the repeat line reads: «Una vez», «Todos los días», «lun a vie», «Fines de semana», a few days, or a date. */
sealed interface Repeat {
    data object Once : Repeat
    data object Daily : Repeat
    data object Weekdays : Repeat
    data object Weekends : Repeat
    /** Days in the order the week starts on the phone (Monday or Sunday). */
    data class Days(val days: List<DayOfWeek>) : Repeat
    data class OnDate(val date: LocalDate) : Repeat
}

/** When alarms ring. Pure: the clock, the zone and the holidays are given. */
object Schedule {
    private val WEEKDAYS = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
    private val WEEKEND = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

    /**
     * The next time [alarm] rings after [now], or null (off, or its date has passed). A snooze wins. Repeating alarms
     * skip the days [holiday] says (Ajustes › Días festivos); a one-off alarm always rings.
     */
    fun next(alarm: Alarm, now: ZonedDateTime, holiday: (LocalDate) -> Boolean = { false }): ZonedDateTime? {
        if (!alarm.enabled) return null
        if (alarm.snoozedUntil > now.toInstant().toEpochMilli())
            return Instant.ofEpochMilli(alarm.snoozedUntil).atZone(now.zone)
        fun at(day: LocalDate) = day.atTime(alarm.hour, alarm.minute).atZone(now.zone)
        alarm.date?.let { d -> return at(d).takeIf { it.isAfter(now) } }
        val today = now.toLocalDate()
        if (alarm.days.isEmpty()) return at(today).takeIf { it.isAfter(now) } ?: at(today.plusDays(1))
        for (i in 0L..400L) {
            val day = today.plusDays(i)
            if (day.dayOfWeek in alarm.days && !holiday(day) && at(day).isAfter(now)) return at(day)
        }
        return null
    }

    /** The first alarm to ring and when. */
    fun soonest(alarms: List<Alarm>, now: ZonedDateTime, holiday: (LocalDate) -> Boolean = { false }): Pair<Alarm, ZonedDateTime>? =
        alarms.mapNotNull { a -> next(a, now, holiday)?.let { a to it } }.minByOrNull { it.second }

    /** From [from] to [to], counted in whole minutes of the clock (6:36:40 → 6:23 next day = 23 h 47 min). */
    fun until(from: ZonedDateTime, to: ZonedDateTime): Until {
        val minutes = Duration.between(from.truncatedTo(ChronoUnit.MINUTES), to.truncatedTo(ChronoUnit.MINUTES)).toMinutes().coerceAtLeast(0)
        val days = minutes / 1440
        return if (days > 0) Until(days, 0, 0) else Until(0, minutes / 60, minutes % 60)
    }

    fun repeat(alarm: Alarm, firstDay: DayOfWeek = DayOfWeek.MONDAY): Repeat {
        alarm.date?.let { return Repeat.OnDate(it) }
        val d = alarm.days
        return when {
            d.isEmpty() -> Repeat.Once
            d.size == 7 -> Repeat.Daily
            d == WEEKDAYS -> Repeat.Weekdays
            d == WEEKEND -> Repeat.Weekends
            else -> Repeat.Days(d.sortedBy { (it.value - firstDay.value + 7) % 7 })
        }
    }

    /** The week in the phone's order, for «Repetir». */
    fun week(firstDay: DayOfWeek): List<DayOfWeek> = (0L until 7L).map { firstDay.plus(it) }
}

/**
 * Ecuador's national holidays (Ajustes › Días festivos): the dates of the law, with Carnaval (Monday and Tuesday
 * before Ash Wednesday) and Viernes Santo from Easter. Days the government moves by decree are not followed.
 */
object Holidays {
    fun ecuador(year: Int): Set<LocalDate> {
        val easter = easter(year)
        return setOf(
            LocalDate.of(year, 1, 1), easter.minusDays(48), easter.minusDays(47), easter.minusDays(2),
            LocalDate.of(year, 5, 1), LocalDate.of(year, 5, 24), LocalDate.of(year, 8, 10), LocalDate.of(year, 10, 9),
            LocalDate.of(year, 11, 2), LocalDate.of(year, 11, 3), LocalDate.of(year, 12, 25),
        )
    }

    private val cache = HashMap<Int, Set<LocalDate>>()
    @Synchronized fun isEcuador(day: LocalDate) = day in cache.getOrPut(day.year) { ecuador(day.year) }

    /** Easter Sunday (Gregorian, anonymous algorithm). */
    fun easter(year: Int): LocalDate {
        val a = year % 19; val b = year / 100; val c = year % 100
        val d = b / 4; val e = b % 4; val f = (b + 8) / 25; val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30; val i = c / 4; val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7; val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31; val day = (h + l - 7 * m + 114) % 31 + 1
        return LocalDate.of(year, month, day)
    }
}
