package com.zirakusa.reloj.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.zirakusa.reloj.R
import com.zirakusa.reloj.data.Alarm
import com.zirakusa.reloj.data.Format
import com.zirakusa.reloj.data.Repeat
import com.zirakusa.reloj.data.Schedule
import com.zirakusa.reloj.data.Until
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/** The phone's language. */
@Composable fun locale(): Locale = LocalConfiguration.current.locales[0]

/** The first day of the week where the phone is (Monday in Ecuador and Spain, Sunday in the US). */
@Composable fun firstDay(): DayOfWeek = WeekFields.of(locale()).firstDayOfWeek

fun dayShort(day: DayOfWeek, locale: Locale) = day.getDisplayName(TextStyle.SHORT, locale).trimEnd('.')
fun dayFull(day: DayOfWeek, locale: Locale) = day.getDisplayName(TextStyle.FULL, locale)

/** «6:02» and «a. m.» (or «06:02» and nothing with 24 h). */
@Composable fun timeParts(hour: Int, minute: Int, h24: Boolean): Pair<String, String?> =
    if (h24) "%02d:%02d".format(hour, minute) to null
    else "%d:%02d".format(Format.hour12(hour), minute) to stringResource(if (hour < 12) R.string.am else R.string.pm)

@Composable fun repeatText(alarm: Alarm): String {
    val l = locale()
    return when (val r = Schedule.repeat(alarm, firstDay())) {
        Repeat.Once -> stringResource(R.string.once)
        Repeat.Daily -> stringResource(R.string.every_day)
        Repeat.Weekdays -> stringResource(R.string.weekdays)
        Repeat.Weekends -> stringResource(R.string.weekends)
        is Repeat.Days -> r.days.joinToString(", ") { dayShort(it, l) }
        is Repeat.OnDate -> dateText(r.date, l)
    }
}

fun dateText(date: LocalDate, locale: Locale): String = DateTimeFormatter.ofPattern("d MMM yyyy", locale).format(date).replace(".", "")

/** «La alarma sonará en 23 horas y 47 minutos». */
@Composable fun untilText(u: Until): String {
    val hours = pluralStringResource(R.plurals.hours, u.hours.toInt(), u.hours)
    val minutes = pluralStringResource(R.plurals.minutes, u.minutes.toInt(), u.minutes)
    val span = when {
        u.days > 0 -> pluralStringResource(R.plurals.days, u.days.toInt(), u.days)
        u.hours > 0 && u.minutes > 0 -> stringResource(R.string.hours_and_minutes, hours, minutes)
        u.hours > 0 -> hours
        u.minutes > 0 -> minutes
        else -> stringResource(R.string.less_than_minute)
    }
    return stringResource(R.string.rings_in, span)
}

/** «-5 h», «+1 h 30 min», or «Misma hora». */
@Composable fun differenceText(minutes: Int): String {
    if (minutes == 0) return stringResource(R.string.same_time)
    val sign = if (minutes < 0) "-" else "+"
    val a = kotlin.math.abs(minutes)
    return if (a % 60 == 0) "$sign${a / 60} h" else if (a < 60) "$sign${a} min" else "$sign${a / 60} h ${a % 60} min"
}
