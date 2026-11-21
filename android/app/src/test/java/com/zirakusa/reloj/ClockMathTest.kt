package com.zirakusa.reloj

import com.zirakusa.reloj.data.Alarm
import com.zirakusa.reloj.data.AlarmStore
import com.zirakusa.reloj.data.Cities
import com.zirakusa.reloj.data.CountDown
import com.zirakusa.reloj.data.Format
import com.zirakusa.reloj.data.Holidays
import com.zirakusa.reloj.data.MapValues
import com.zirakusa.reloj.data.Preset
import com.zirakusa.reloj.data.Repeat
import com.zirakusa.reloj.data.Schedule
import com.zirakusa.reloj.data.Settings
import com.zirakusa.reloj.data.Sound
import com.zirakusa.reloj.data.SoundFiles
import com.zirakusa.reloj.data.SoundKind
import com.zirakusa.reloj.data.Stopwatch
import com.zirakusa.reloj.data.Until
import com.zirakusa.reloj.data.Vibration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** The pure rules: next ring, «sonará en», repeat line, holidays, store, sounds, timer, stopwatch, cities, settings. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ClockMathTest {
    private val zone = ZoneId.of("America/Guayaquil")
    /** Saturday 21 November 2026, 6:36:40. */
    private val now = ZonedDateTime.of(2026, 11, 21, 6, 36, 40, 0, zone)
    private val weekdays = setOf(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY)
    private fun at(d: Int, h: Int, m: Int, month: Int = 11) = ZonedDateTime.of(2026, month, d, h, m, 0, 0, zone)

    @Test fun nextRing() {
        assertEquals(at(23, 6, 2), Schedule.next(Alarm(hour = 6, minute = 2, days = weekdays), now))          // Saturday → Monday
        assertEquals(at(22, 6, 23), Schedule.next(Alarm(hour = 6, minute = 23, days = Schedule.week(MONDAY).toSet()), now))
        assertEquals(at(21, 13, 30), Schedule.next(Alarm(hour = 13, minute = 30), now))                       // once, later today
        assertEquals(at(22, 6, 30), Schedule.next(Alarm(hour = 6, minute = 30), now))                         // once, tomorrow
        assertEquals(at(21, 6, 37), Schedule.next(Alarm(hour = 6, minute = 37), now))
        assertNull(Schedule.next(Alarm(hour = 6, minute = 30, enabled = false), now))
        assertEquals(at(22, 6, 37), Schedule.next(Alarm(hour = 6, minute = 37, date = LocalDate.of(2026, 11, 22)), now))
        assertNull(Schedule.next(Alarm(hour = 6, minute = 0, date = LocalDate.of(2026, 11, 21)), now))      // already passed
        val snoozed = Alarm(hour = 6, minute = 30, snoozedUntil = now.plusMinutes(5).toInstant().toEpochMilli(), snoozed = 1)
        assertEquals(now.plusMinutes(5), Schedule.next(snoozed, now))
        // Holidays: Monday 2 Nov and Tuesday 3 Nov 2026 are skipped by a weekday alarm; a one-off still rings.
        val sunday = ZonedDateTime.of(2026, 11, 1, 20, 0, 0, 0, zone)
        assertEquals(at(4, 6, 2), Schedule.next(Alarm(hour = 6, minute = 2, days = weekdays), sunday, Holidays::isEcuador))
        assertEquals(at(2, 6, 2), Schedule.next(Alarm(hour = 6, minute = 2), sunday, Holidays::isEcuador))
        val (first, time) = Schedule.soonest(listOf(Alarm(1, 6, 2, days = weekdays), Alarm(2, 13, 30)), now)!!
        assertEquals(2L, first.id); assertEquals(at(21, 13, 30), time)
    }

    @Test fun ringsIn() {
        assertEquals(Until(0, 23, 47), Schedule.until(now, at(22, 6, 23)))
        assertEquals(Until(1, 0, 0), Schedule.until(now, at(23, 6, 2)))
        assertEquals(Until(0, 6, 54), Schedule.until(now, at(21, 13, 30)))
        assertEquals(Until(0, 0, 1), Schedule.until(now, at(21, 6, 37)))
    }

    @Test fun repeatLine() {
        assertEquals(Repeat.Once, Schedule.repeat(Alarm()))
        assertEquals(Repeat.Weekdays, Schedule.repeat(Alarm(days = weekdays)))
        assertEquals(Repeat.Weekends, Schedule.repeat(Alarm(days = setOf(SATURDAY, SUNDAY))))
        assertEquals(Repeat.Daily, Schedule.repeat(Alarm(days = Schedule.week(SUNDAY).toSet())))
        assertEquals(Repeat.Days(listOf(SUNDAY, MONDAY, FRIDAY)), Schedule.repeat(Alarm(days = setOf(FRIDAY, MONDAY, SUNDAY)), SUNDAY))
        assertEquals(Repeat.Days(listOf(MONDAY, FRIDAY, SUNDAY)), Schedule.repeat(Alarm(days = setOf(FRIDAY, MONDAY, SUNDAY))))
        assertEquals(Repeat.OnDate(LocalDate.of(2026, 11, 22)), Schedule.repeat(Alarm(date = LocalDate.of(2026, 11, 22), days = weekdays)))
        assertEquals(listOf(SUNDAY, MONDAY), Schedule.week(SUNDAY).take(2))
    }

    @Test fun ecuadorHolidays() {
        assertEquals(LocalDate.of(2026, 4, 5), Holidays.easter(2026))
        assertEquals(LocalDate.of(2025, 4, 20), Holidays.easter(2025))
        val h = Holidays.ecuador(2026)
        listOf("2026-01-01", "2026-02-16", "2026-02-17", "2026-04-03", "2026-05-01", "2026-05-24", "2026-08-10", "2026-10-09",
            "2026-11-02", "2026-11-03", "2026-12-25").forEach { assertTrue(it, LocalDate.parse(it) in h) }
        assertFalse(Holidays.isEcuador(LocalDate.of(2026, 11, 21)))
    }

    @Test fun storeKeepsAlarms() {
        val kv = MapValues()
        val store = AlarmStore(kv)
        val a = store.save(Alarm(hour = 13, minute = 30, days = weekdays, label = "Almuerzo", sound = Sound(SoundKind.CUSTOM, "file:///x/tono.mp3", "tono"),
            vibration = Vibration.WALTZ, snoozeMinutes = 5, snoozeTimes = 1, offAfterRing = true))
        val b = store.save(Alarm(hour = 6, minute = 2, date = LocalDate.of(2026, 12, 1)))
        assertEquals(listOf(2L, 1L), store.list().map { it.id })                  // ordered by time
        assertEquals(a, AlarmStore(kv).get(1)); assertEquals(b, store.get(2))
        store.save(a.copy(enabled = false))
        assertFalse(store.get(1)!!.enabled)
        assertTrue(store.delete(1)); assertFalse(store.delete(1))
        assertEquals(3L, store.save(Alarm()).id)
        kv.put("alarms", "not json"); assertEquals(emptyList<Alarm>(), store.list())
        assertEquals(Sound.DEFAULT, Sound.parse(org.json.JSONObject().put("kind", "custom")))    // custom without a file
        assertEquals(false, Ringing.stopped(Alarm(days = emptySet())).enabled)
        assertEquals(true, Ringing.stopped(Alarm(days = weekdays, snoozed = 2, snoozedUntil = 5)).let { it.enabled && it.snoozed == 0 && it.snoozedUntil == 0L })
        assertEquals(false, Ringing.stopped(Alarm(days = weekdays, offAfterRing = true)).enabled)
        assertTrue(Alarm(snoozeTimes = 3, snoozed = 2).canSnooze); assertFalse(Alarm(snoozeTimes = 3, snoozed = 3).canSnooze)
    }

    @Test fun soundNames() {
        assertEquals("Mi tono.mp3", SoundFiles.clean("Mi tono.MP3"))
        assertEquals("pista.ogg", SoundFiles.clean("../../etc/pista.ogg"))
        assertEquals("a_b", SoundFiles.clean("a*b"))
        assertEquals("sonido", SoundFiles.clean("..."))
        assertEquals("tono (2).mp3", SoundFiles.unique("tono.mp3") { it in setOf("tono.mp3", "tono (1).mp3") })
    }

    @Test fun timerAndStopwatch() {
        val t = CountDown.start(300_000, 1_000)
        assertEquals(150_000, t.left(151_000)); assertEquals(0.5f, t.progress(151_000), 1e-6f)
        val p = t.pause(151_000)
        assertEquals(150_000, p.left(999_999)); assertEquals(210_000, p.plus(60_000).left(0))
        assertEquals(150_000, p.resume(200_000).left(200_000))
        assertEquals(0, t.left(999_999)); assertTrue(CountDown.start(0, 1).idle)
        assertEquals(p, CountDown.parse(p.json().toString())); assertEquals(CountDown(), CountDown.parse("x"))
        assertEquals("05:00", Format.countdown(300_000)); assertEquals("00:01", Format.countdown(1)); assertEquals("1:00:05", Format.countdown(3_605_000))

        val w = Stopwatch().start(0).lap(10_000).lap(25_000).pause(30_000)
        assertEquals(30_000, w.elapsed(99_999))
        assertEquals(listOf(Triple(2, 15_000L, 25_000L), Triple(1, 10_000L, 10_000L)), w.splits())
        assertEquals(31_000, w.start(50_000).elapsed(51_000))
        assertEquals(w, Stopwatch.parse(w.json().toString()))
        assertEquals("00:12.34", Format.stopwatch(12_345)); assertEquals("1:02:03.45", Format.stopwatch(3_723_450))
    }

    @Test fun citiesAndOffsets() {
        assertEquals("GMT +5:30", Format.gmt(19_800)); assertEquals("GMT -7:00", Format.gmt(-25_200)); assertEquals("GMT +0:00", Format.gmt(0))
        assertEquals(12, Format.hour12(0)); assertEquals(1, Format.hour12(13))
        assertEquals("Bogotá", Cities.search("bogota").first().name)
        assertTrue(Cities.search("ecuador") { if (it == "EC") "Ecuador" else it }.map { it.name }.containsAll(listOf("Quito", "Guayaquil")))
        assertEquals('A', Cities.letter(Cities.all.first { it.name == "Ámsterdam" }))
        assertEquals("Tokio", Cities.find("tokio")!!.name)
        assertEquals("Europe/Madrid", Cities.find("Europe/Madrid")!!.zone)
        assertEquals("Asia/Kathmandu", Cities.find("Asia/Kathmandu")!!.zone)
        assertNull(Cities.find("Atlantis"))
        assertEquals(20_700, Cities.find("Katmandú")!!.offset(Instant.parse("2026-11-21T00:00:00Z")))
        Cities.all.forEach { ZoneId.of(it.zone) }                                // every zone exists
        assertEquals(Cities.all.size, Cities.all.map { it.id }.toSet().size)
    }

    @Test fun settingsDefaultsAndValues() {
        val s = Settings(MapValues())
        assertFalse(s.skipHolidays); assertTrue(s.rising); assertEquals(10, s.ringMinutes); assertTrue(s.analog)
        assertEquals(Preset.DEFAULTS, s.presets); assertEquals(Sound.DEFAULT, s.timerTone)
        s.ringMinutes = 7; assertEquals(10, s.ringMinutes)
        s.cities = listOf("Quito|America/Guayaquil", "Madrid|Europe/Madrid"); assertEquals(2, s.cities.size)
        s.presets = listOf(Preset("Té", 180_000)); assertEquals(listOf(Preset("Té", 180_000)), s.presets)
        s.timerTone = Sound.SILENT; assertEquals(Sound.SILENT, s.timerTone)
    }
}
