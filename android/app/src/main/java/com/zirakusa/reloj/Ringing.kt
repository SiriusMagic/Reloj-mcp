package com.zirakusa.reloj

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.zirakusa.reloj.data.Alarm
import com.zirakusa.reloj.data.AlarmStore
import com.zirakusa.reloj.data.CountDown
import com.zirakusa.reloj.data.Settings
import com.zirakusa.reloj.data.SharedValues
import com.zirakusa.reloj.data.Sound
import com.zirakusa.reloj.data.Vibration

/** What is ringing now: an alarm or the timer ([id] = [AlarmScheduler.TIMER]). [label] blank = no name. */
data class RingInfo(val id: Long, val label: String, val hour: Int, val minute: Int, val canSnooze: Boolean,
    val snoozeMinutes: Int, val sound: Sound, val vibration: Vibration) {
    val timer get() = id == AlarmScheduler.TIMER
}

/** The ringing alarm (shown by the ring screen) and what «Posponer» and «Detener» do to the saved alarms. */
object Ringing {
    var current by mutableStateOf<RingInfo?>(null)
    /** Grows when a ring changed the alarms or the timer, so the open app reloads them. */
    var revision by mutableIntStateOf(0); private set

    fun describe(context: Context, id: Long): RingInfo? {
        val values = SharedValues(context)
        val settings = Settings(values)
        if (id == AlarmScheduler.TIMER) {
            if (settings.timer.idle) return null
            return RingInfo(id, settings.timerName, 0, 0, false, 0, if (settings.timerSound) settings.timerTone else Sound.SILENT, Vibration.STANDARD)
        }
        val a = AlarmStore(values).get(id)?.takeIf { it.enabled } ?: return null
        return RingInfo(a.id, a.label, a.hour, a.minute, a.canSnooze, a.snoozeMinutes, a.sound, a.vibration)
    }

    /** «Detener»: a one-off alarm (or one set to switch off after ringing) goes off; the timer is cleared. */
    fun dismiss(context: Context, id: Long) {
        val values = SharedValues(context)
        if (id == AlarmScheduler.TIMER) Settings(values).apply { timer = CountDown(); timerName = "" }
        else AlarmStore(values).let { store ->
            store.get(id)?.let { a -> store.save(stopped(a)) }
        }
        changed(context)
    }

    /** «Posponer»: rings again after its minutes while it has snoozes left; otherwise it stops. */
    fun snooze(context: Context, id: Long, now: Long = System.currentTimeMillis()) {
        val store = AlarmStore(SharedValues(context))
        val a = store.get(id)
        if (id == AlarmScheduler.TIMER || a == null || !a.canSnooze) return dismiss(context, id)
        store.save(a.copy(snoozedUntil = now + a.snoozeMinutes * 60_000L, snoozed = a.snoozed + 1))
        changed(context)
    }

    fun stopped(a: Alarm) = a.copy(enabled = a.repeats && !a.offAfterRing, snoozedUntil = 0, snoozed = 0)

    private fun changed(context: Context) { AlarmScheduler.sync(context); revision++ }
}
