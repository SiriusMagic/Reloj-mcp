package com.zirakusa.reloj.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.zirakusa.reloj.data.Alarm
import com.zirakusa.reloj.data.AlarmStore
import com.zirakusa.reloj.data.Cities
import com.zirakusa.reloj.data.City
import com.zirakusa.reloj.data.CountDown
import com.zirakusa.reloj.data.Holidays
import com.zirakusa.reloj.data.KeyValues
import com.zirakusa.reloj.data.Preset
import com.zirakusa.reloj.data.Schedule
import com.zirakusa.reloj.data.Settings
import com.zirakusa.reloj.data.Sound
import com.zirakusa.reloj.data.Stopwatch
import com.zirakusa.reloj.ui.theme.Accent
import com.zirakusa.reloj.ui.theme.ThemeMode
import java.time.LocalDate
import java.time.ZonedDateTime

/** The bottom bar. */
enum class Tab { ALARMS, WORLD, TIMER, STOPWATCH }

/** Pages over the tabs. */
enum class Page { MAIN, EDITOR, CITIES, TIMER_EDITOR, TIMER_SOUND, SETTINGS, MCP }

/** Pages inside «Agregar alarma». */
enum class EditorPage { MAIN, REPEAT, SOUND, VIBRATION, SNOOZE }

/**
 * The only owner of what the screens show: the alarms and the alarm being edited, the cities, the timer, the
 * stopwatch and the settings. [clock] gives «now» (fixed in tests); [onChanged] reschedules after alarms or the timer
 * change. The screens only read and call.
 */
class ClockState(values: KeyValues, private val clock: () -> ZonedDateTime = ZonedDateTime::now, private val onChanged: () -> Unit = {}) {
    private val settings = Settings(values)
    private val store = AlarmStore(values)

    var tab by mutableStateOf(Tab.ALARMS)
    var page by mutableStateOf(Page.MAIN)
    /** The phone shows 24 h times. */
    var h24 by mutableStateOf(false)
    /** Something that keeps alarms from ringing well (shown on «Alarma»); the activity checks it. */
    var issue by mutableStateOf<Issue?>(null)
    var now by mutableStateOf(clock()); private set
    val millis get() = now.toInstant().toEpochMilli()
    fun tick() { now = clock() }

    var alarms by mutableStateOf(store.list()); private set
    var draft by mutableStateOf<Alarm?>(null); private set
    var editorPage by mutableStateOf(EditorPage.MAIN)

    var skipHolidays by mutableStateOf(settings.skipHolidays); private set
    var rising by mutableStateOf(settings.rising); private set
    var ringMinutes by mutableIntStateOf(settings.ringMinutes); private set
    var timerSound by mutableStateOf(settings.timerSound); private set
    var stopwatchSound by mutableStateOf(settings.stopwatchSound); private set
    var analog by mutableStateOf(settings.analog); private set
    var cities by mutableStateOf(settings.cities.mapNotNull(Cities::byId)); private set
    var timer by mutableStateOf(settings.timer); private set
    var timerName by mutableStateOf(settings.timerName); private set
    var lastTimer by mutableLongStateOf(settings.lastTimer); private set
    var presets by mutableStateOf(settings.presets); private set
    var timerTone by mutableStateOf(settings.timerTone); private set
    var stopwatch by mutableStateOf(settings.stopwatch); private set
    var themeMode by mutableStateOf(settings.theme?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.BLACK); private set
    var accent by mutableStateOf(settings.accent?.let { runCatching { Accent.valueOf(it) }.getOrNull() } ?: Accent.NARANJA); private set

    val holiday: (LocalDate) -> Boolean get() = if (skipHolidays) Holidays::isEcuador else { _ -> false }
    fun nextOf(alarm: Alarm) = Schedule.next(alarm, now, holiday)

    /** After a ring, a tool or the receiver changed what is saved. */
    fun reload() { alarms = store.list(); timer = settings.timer; timerName = settings.timerName }

    // «Alarma»
    fun newAlarm() { draft = Alarm(hour = now.hour, minute = now.minute); editorPage = EditorPage.MAIN; page = Page.EDITOR }
    fun edit(alarm: Alarm) { draft = alarm; editorPage = EditorPage.MAIN; page = Page.EDITOR }
    fun change(f: (Alarm) -> Alarm) { draft = draft?.let(f) }
    fun closeEditor() { draft = null; page = Page.MAIN }
    fun saveDraft(): Alarm? {
        val d = draft ?: return null
        val date = d.date?.takeIf { day -> day.atTime(d.hour, d.minute).atZone(now.zone).isAfter(now) }
        val saved = store.save(d.copy(enabled = true, date = date, days = if (date != null) emptySet() else d.days, snoozedUntil = 0, snoozed = 0))
        closeEditor(); changed()
        return saved
    }
    fun toggle(alarm: Alarm, on: Boolean) { store.save(alarm.copy(enabled = on, snoozedUntil = 0, snoozed = 0)); changed() }
    fun delete(id: Long) { store.delete(id); if (draft?.id == id) closeEditor(); changed() }
    private fun changed() { alarms = store.list(); onChanged() }

    // «Reloj mundial»
    fun addCity(city: City) { if (cities.none { it.id == city.id }) cities = cities + city; settings.cities = cities.map { it.id }; page = Page.MAIN }
    fun removeCity(city: City) { cities = cities.filterNot { it.id == city.id }; settings.cities = cities.map { it.id } }
    fun toggleAnalog() { analog = !analog; settings.analog = analog }

    /** «Poner alarma» from another app or an assistant: saved (or the same one turned on) and shown in «Alarmas». */
    fun alarmFromOrder(hour: Int, minute: Int, label: String?, days: List<Int>?): Alarm? {
        val alarm = com.zirakusa.reloj.data.Orders.alarm(hour, minute, label, days, store.list()) ?: return null
        val saved = store.save(alarm.copy(enabled = true))
        changed(); tab = Tab.ALARMS; page = Page.MAIN
        return saved
    }

    /** «Poner temporizador» from another app or an assistant: it starts at once, shown in «Temporizador». */
    fun timerFromOrder(seconds: Int, label: String?): Boolean {
        val ms = com.zirakusa.reloj.data.Orders.timer(seconds) ?: return false
        startTimer(ms, label.orEmpty().take(40)); tab = Tab.TIMER; page = Page.MAIN
        return true
    }

    // «Temporizador»
    fun startTimer(ms: Long, name: String = "") {
        if (ms <= 0) return
        timer = CountDown.start(ms, millis); timerName = name; lastTimer = ms
        settings.timer = timer; settings.timerName = name; settings.lastTimer = ms; onChanged()
    }
    fun pauseTimer() = putTimer(timer.pause(millis))
    fun resumeTimer() = putTimer(timer.resume(millis))
    fun plusMinute() = putTimer(timer.plus(60_000))
    fun resetTimer() { timerName = ""; settings.timerName = ""; putTimer(CountDown()) }
    private fun putTimer(next: CountDown) { timer = next; settings.timer = next; onChanged() }
    fun changeLastTimer(ms: Long) { lastTimer = ms; settings.lastTimer = ms }
    fun addPreset(name: String, ms: Long) {
        if (ms <= 0) return
        presets = presets + Preset(name.trim().take(Preset.NAME_MAX), ms); settings.presets = presets; page = Page.MAIN
    }
    fun removePreset(preset: Preset) { presets = presets - preset; settings.presets = presets }
    fun changeTimerTone(sound: Sound) { timerTone = sound; settings.timerTone = sound }

    // «Cronómetro»
    fun startStopwatch() = putStopwatch(stopwatch.start(millis))
    fun pauseStopwatch() = putStopwatch(stopwatch.pause(millis))
    fun lap() = putStopwatch(stopwatch.lap(millis))
    fun resetStopwatch() = putStopwatch(Stopwatch())
    private fun putStopwatch(next: Stopwatch) { stopwatch = next; settings.stopwatch = next }

    // «Ajustes»
    fun changeSkipHolidays(on: Boolean) { skipHolidays = on; settings.skipHolidays = on; onChanged() }
    fun changeRising(on: Boolean) { rising = on; settings.rising = on }
    fun changeRingMinutes(m: Int) { ringMinutes = m; settings.ringMinutes = m }
    fun changeTimerSound(on: Boolean) { timerSound = on; settings.timerSound = on }
    fun changeStopwatchSound(on: Boolean) { stopwatchSound = on; settings.stopwatchSound = on }
    fun changeTheme(mode: ThemeMode) { themeMode = mode; settings.theme = mode.name }
    fun changeAccent(next: Accent) { accent = next; settings.accent = next.name }
}
