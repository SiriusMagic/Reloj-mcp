package com.zirakusa.reloj

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.text.format.DateFormat
import com.zirakusa.reloj.data.Alarm
import com.zirakusa.reloj.data.AlarmStore
import com.zirakusa.reloj.data.CountDown
import com.zirakusa.reloj.data.Format
import com.zirakusa.reloj.data.Holidays
import com.zirakusa.reloj.data.Schedule
import com.zirakusa.reloj.data.Settings
import com.zirakusa.reloj.data.SharedValues
import com.zirakusa.reloj.data.Stopwatch
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The lines of Reloj's card in Inicio: the next alarm, the running timer and stopwatch. Pure; texts come in. */
object SummaryLines {
    class Texts(val next: String, val timer: String, val paused: String, val stopwatch: String)

    fun lines(alarms: List<Alarm>, timer: CountDown, stopwatch: Stopwatch, now: ZonedDateTime, holiday: (LocalDate) -> Boolean,
        t: Texts, time: (ZonedDateTime) -> String): List<String> {
        val millis = now.toInstant().toEpochMilli()
        val next = Schedule.soonest(alarms, now, holiday)?.let { (a, at) -> t.next.format(time(at)) + if (a.label.isBlank()) "" else " · ${a.label}" }
        val countdown = if (timer.idle) null else (if (timer.running) t.timer else t.paused).format(Format.countdown(timer.left(millis)))
        val watch = if (stopwatch.running) t.stopwatch.format(Format.stopwatch(stopwatch.elapsed(millis)).substringBeforeLast('.')) else null
        return listOfNotNull(next, countdown, watch)
    }
}

/**
 * The family summary (see Inicio's FAMILIA.md): what Inicio shows in its «Hoy» card for Reloj. Only apps signed by the
 * same developer may ask (Android's signature permission and the caller's signature checked here).
 */
class SummaryProvider : ContentProvider() {
    override fun onCreate() = true
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method != "summary") return null
        val context = context ?: return null
        val caller = callingPackage ?: return null
        if (context.packageManager.checkSignatures(context.packageName, caller) != PackageManager.SIGNATURE_MATCH) return null
        val values = SharedValues(context)
        val settings = Settings(values)
        val now = ZonedDateTime.now()
        val h24 = DateFormat.is24HourFormat(context)
        val locale: Locale = context.resources.configuration.locales[0]
        val texts = SummaryLines.Texts(context.getString(R.string.summary_next), context.getString(R.string.summary_timer),
            context.getString(R.string.summary_timer_paused), context.getString(R.string.summary_stopwatch))
        val lines = SummaryLines.lines(AlarmStore(values).list(), settings.timer, settings.stopwatch, now,
            if (settings.skipHolidays) Holidays::isEcuador else { _ -> false }, texts) { at ->
            val day = if (at.toLocalDate() == now.toLocalDate()) "" else DateTimeFormatter.ofPattern("EEE ", locale).format(at).replace(".", "")
            day + if (h24) "%02d:%02d".format(at.hour, at.minute)
                else "%d:%02d %s".format(Format.hour12(at.hour), at.minute, context.getString(if (at.hour < 12) R.string.am else R.string.pm))
        }
        return Bundle().apply {
            putString("title", context.getString(R.string.app_name))
            putStringArrayList("lines", ArrayList(lines))
            putString("empty", context.getString(R.string.summary_empty))
        }
    }
    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0
}
