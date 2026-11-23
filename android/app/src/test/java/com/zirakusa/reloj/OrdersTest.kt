package com.zirakusa.reloj

import com.zirakusa.reloj.data.Alarm
import com.zirakusa.reloj.data.MapValues
import com.zirakusa.reloj.data.Orders
import com.zirakusa.reloj.ui.ClockState
import com.zirakusa.reloj.ui.Tab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime

/** Android's «poner alarma / temporizador» from assistants and other apps (FAMILIA.md §10). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OrdersTest {
    @Test fun pureRules() {
        assertEquals(DayOfWeek.SUNDAY, Orders.day(1)); assertEquals(DayOfWeek.MONDAY, Orders.day(2)); assertEquals(DayOfWeek.SATURDAY, Orders.day(7)); assertNull(Orders.day(8))
        val a = Orders.alarm(7, 30, " Gimnasio ", listOf(2, 4), emptyList())!!
        assertEquals("07:30", a.time); assertEquals("Gimnasio", a.label); assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), a.days)
        val old = Alarm(id = 4, hour = 7, minute = 30, enabled = false, label = "Gimnasio", days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY))
        assertEquals(Alarm(id = 4, hour = 7, minute = 30, enabled = true, label = "Gimnasio", days = old.days), Orders.alarm(7, 30, "Gimnasio", listOf(2, 4), listOf(old)))
        assertNull(Orders.alarm(24, 0, null, null, emptyList())); assertNull(Orders.alarm(7, 60, null, null, emptyList()))
        assertEquals(600_000L, Orders.timer(600)); assertNull(Orders.timer(0)); assertNull(Orders.timer(25 * 3600))
    }

    @Test fun theStateSavesAndShows() {
        val s = ClockState(MapValues(), clock = { ZonedDateTime.of(2026, 11, 21, 9, 0, 0, 0, ZoneId.of("America/Guayaquil")) })
        val a = s.alarmFromOrder(7, 30, null, null)!!
        assertEquals(listOf(a.id), s.alarms.map { it.id }); assertEquals(Tab.ALARMS, s.tab); assertTrue(a.enabled)
        s.alarmFromOrder(7, 30, null, null)
        assertEquals(1, s.alarms.size)                                                          // asked twice: no copy
        assertNull(s.alarmFromOrder(99, 0, null, null))
        assertTrue(s.timerFromOrder(600, "Huevos")); assertEquals(Tab.TIMER, s.tab); assertEquals("Huevos", s.timerName)
        assertFalse(s.timerFromOrder(-5, null))
    }
}
