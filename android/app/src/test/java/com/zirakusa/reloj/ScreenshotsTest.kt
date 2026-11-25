package com.zirakusa.reloj

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.zirakusa.reloj.data.Alarm
import com.zirakusa.reloj.data.AlarmStore
import com.zirakusa.reloj.data.Cities
import com.zirakusa.reloj.data.MapValues
import com.zirakusa.reloj.data.Sound
import com.zirakusa.reloj.data.SoundKind
import com.zirakusa.reloj.ui.AppActions
import com.zirakusa.reloj.ui.ClockState
import com.zirakusa.reloj.ui.EditorPage
import com.zirakusa.reloj.ui.Issue
import com.zirakusa.reloj.ui.Page
import com.zirakusa.reloj.ui.RelojApp
import com.zirakusa.reloj.ui.SoundSource
import com.zirakusa.reloj.ui.Tab
import com.zirakusa.reloj.ui.screens.RingScreen
import com.zirakusa.reloj.ui.theme.Accent
import com.zirakusa.reloj.ui.theme.ClockTheme
import com.zirakusa.reloj.ui.theme.ThemeMode
import com.zirakusa.reloj.data.Vibration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "es-w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotsTest {
    @get:Rule val compose = createComposeRule()
    private val zone = ZoneId.of("America/Guayaquil")
    private var now = ZonedDateTime.of(2026, 11, 21, 6, 36, 40, 0, zone)
    private val values = MapValues()
    private val state by lazy { ClockState(values, clock = { now }) }
    private var picked = 0
    private val tones = object : SoundSource {
        override fun system() = listOf("Amanecer", "Campanas", "Cristal").map { Sound(SoundKind.SYSTEM, "content://tones/$it", it) }
        override fun custom() = listOf(Sound(SoundKind.CUSTOM, "file:///sounds/Mi canción.mp3", "Mi canción"))
        override fun defaultName() = "Amanecer"
    }
    private val actions = AppActions(sounds = tones, addSound = { picked++ })

    private fun shot(name: String) { compose.waitForIdle(); onView(isRoot()).captureRoboImage("build/screenshots/reloj-$name.png") }
    private fun show() = compose.setContent { ClockTheme(state.themeMode, state.accent) { RelojApp(state, actions, ticking = false) } }

    private fun seed() {
        val store = AlarmStore(values)
        val weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
        store.save(Alarm(hour = 6, minute = 2, days = weekdays))
        store.save(Alarm(hour = 6, minute = 23, days = DayOfWeek.entries.toSet()))
        store.save(Alarm(hour = 6, minute = 30, enabled = false))
        store.save(Alarm(hour = 13, minute = 30, days = DayOfWeek.entries.toSet(), label = "Almuerzo"))
        state.reload()
    }

    @Test fun alarmsAndEditor() {
        seed(); show()
        compose.onNodeWithText("lun a vie. La alarma sonará en 1 día").assertExists()
        compose.onNodeWithText("Todos los días. La alarma sonará en 23 horas y 47 minutos").assertExists()
        compose.onNodeWithText("Almuerzo. Todos los días. La alarma sonará en 6 horas y 54 minutos").assertExists()
        shot("alarms")
        state.issue = Issue.NOTIFICATIONS
        shot("alarms-issue")
        state.issue = null

        compose.onNodeWithContentDescription("Agregar").performClick()
        compose.onNodeWithText("Agregar alarma").assertExists()
        compose.onNodeWithText("Una vez").assertExists()
        shot("editor")
        compose.onNodeWithText("Repetir").performClick()
        compose.onNodeWithText("miércoles").performClick(); compose.onNodeWithText("sábado").performClick()
        assertEquals(setOf(DayOfWeek.WEDNESDAY, DayOfWeek.SATURDAY), state.draft!!.days)
        shot("repeat")
        state.editorPage = EditorPage.MAIN
        compose.onNodeWithText("mié, sáb").assertExists()
        compose.onNodeWithText("Sonido de la alarma").performClick()
        compose.onNodeWithText("Predeterminado (Amanecer)").assertExists()
        shot("sound")
        compose.onNodeWithText("Añadir nuevo").performClick()
        assertEquals(1, picked)
        compose.onNodeWithText("Cristal").performClick()
        state.editorPage = EditorPage.VIBRATION
        compose.onNodeWithText("Vals").performClick()
        shot("vibration")
        state.editorPage = EditorPage.SNOOZE
        compose.onNodeWithText("5 min").performClick()
        shot("snooze")
        state.editorPage = EditorPage.MAIN
        compose.onNodeWithText("Sonar en fecha específica").performClick()
        assertEquals(LocalDate.of(2026, 11, 22), state.draft!!.date)
        shot("editor-date")
        compose.onNodeWithText("Guardar").performClick()
        val saved = state.alarms.first { it.vibration == Vibration.WALTZ }
        assertEquals("Cristal", saved.sound.name); assertEquals(5, saved.snoozeMinutes); assertEquals(5, state.alarms.size)
    }

    @Test fun worldClockAndCities() {
        state.tab = Tab.WORLD
        state.addCity(Cities.find("Madrid")!!); state.addCity(Cities.find("Tokio")!!)
        show()
        compose.onNodeWithText("Hoy, +6 h").assertExists()
        compose.onNodeWithText("Hoy, +14 h").assertExists()
        shot("world")
        compose.onNodeWithContentDescription("6:36").performClick()
        compose.onNodeWithText("6:36:40").assertExists()
        assertEquals(false, state.analog)
        shot("world-digital")
        state.page = Page.CITIES
        compose.onNodeWithText("Seleccionar ciudad").assertExists()
        shot("cities")
        compose.onNodeWithText("Abu Dabi").performClick()
        assertEquals(3, state.cities.size)
    }

    @Test fun timerAndStopwatch() {
        state.tab = Tab.TIMER
        show()
        compose.onNodeWithText("Reunión").assertExists()
        compose.onNodeWithText("00:20:00").assertExists()
        shot("timer")
        compose.onNodeWithText("Sueño").performClick()
        assertTrue(state.timer.running)
        now = now.plusMinutes(4); state.tick()
        compose.onNodeWithText("06:00").assertExists()
        compose.onNodeWithText("Sonará a las 6:46 a. m.").assertExists()
        shot("timer-running")
        state.page = Page.TIMER_EDITOR
        compose.onNodeWithText("Nombre del temporizador").assertExists()
        shot("timer-add")
        state.page = Page.MAIN

        state.tab = Tab.STOPWATCH
        compose.onNodeWithText("00:00.00").assertExists()
        compose.onNodeWithContentDescription("Iniciar").performClick()
        now = now.plusSeconds(12); state.tick(); state.lap()
        now = now.plusSeconds(20); state.tick(); state.lap()
        now = now.plusNanos(340_000_000); state.tick()
        compose.onNodeWithText("00:32.34").assertExists()
        compose.onNodeWithText("Vuelta 2").assertExists()
        shot("stopwatch")
    }

    @Test fun settingsMcpRingingAndThemes() {
        show()
        state.page = Page.SETTINGS
        compose.onNodeWithText("Días festivos (Ecuador)").performClick()
        assertTrue(state.skipHolidays)
        compose.onNodeWithText("Modo MCP").assertExists()
        shot("settings")
        compose.onNodeWithText("Lo controla Arka", substring = true).assertExists()
        shot("mcp")
        state.page = Page.MAIN
        state.changeTheme(ThemeMode.LIGHT); state.changeAccent(Accent.AZUL)
        seed()
        shot("alarms-light")
    }

    @Test fun ringing() {
        compose.setContent {
            ClockTheme {
                RingScreen(RingInfo(1, "Almuerzo", 13, 30, true, 10, Sound.DEFAULT, Vibration.STANDARD), now.withHour(13).withMinute(30), false, {}, {})
            }
        }
        compose.onNodeWithText("Posponer 10 min").assertExists()
        compose.onNodeWithText("Detener").assertExists()
        shot("ringing")
    }
}
