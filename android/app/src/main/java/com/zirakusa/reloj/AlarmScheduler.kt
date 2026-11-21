package com.zirakusa.reloj

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.zirakusa.reloj.data.AlarmStore
import com.zirakusa.reloj.data.Holidays
import com.zirakusa.reloj.data.Schedule
import com.zirakusa.reloj.data.Settings
import com.zirakusa.reloj.data.SharedValues
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * Hands Android the next ring of every alarm (as an alarm clock, so it shows in the status bar and wakes the phone in
 * Doze) and the end of the running timer. Called after every change, at boot and when the clock or zone changes.
 */
object AlarmScheduler {
    const val ACTION_FIRE = "com.zirakusa.reloj.action.FIRE"
    const val EXTRA_ID = "id"
    /** The timer's id (alarms start at 1). */
    const val TIMER = 0L

    @Synchronized fun sync(context: Context) {
        val app = context.applicationContext
        val values = SharedValues(app)
        val settings = Settings(values)
        val manager = app.getSystemService(AlarmManager::class.java) ?: return
        val now = ZonedDateTime.now()
        val holiday: (LocalDate) -> Boolean = if (settings.skipHolidays) Holidays::isEcuador else { _ -> false }
        val before = values.get(SCHEDULED)?.split(',')?.mapNotNull { it.toLongOrNull() }.orEmpty()
        val planned = AlarmStore(values).list().mapNotNull { a -> Schedule.next(a, now, holiday)?.let { a.id to it.toInstant().toEpochMilli() } }
        (before - planned.map { it.first }.toSet()).forEach { manager.cancel(fire(app, it)) }
        planned.forEach { (id, at) -> set(app, manager, id, at) }
        values.put(SCHEDULED, planned.joinToString(",") { it.first.toString() })
        val timer = settings.timer
        if (timer.running) set(app, manager, TIMER, timer.endsAt) else manager.cancel(fire(app, TIMER))
    }

    private fun set(context: Context, manager: AlarmManager, id: Long, at: Long) {
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
        val pending = fire(context, id)
        if (!exact) { manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending); return }
        val show = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        if (id == TIMER) manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        else manager.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pending)
    }

    private fun fire(context: Context, id: Long) = PendingIntent.getBroadcast(context, id.toInt(),
        Intent(context, AlarmReceiver::class.java).setAction(ACTION_FIRE).putExtra(EXTRA_ID, id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private const val SCHEDULED = "scheduled"
}

/** An alarm or the timer is due: ring. The clock changed or the phone started: plan again. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == AlarmScheduler.ACTION_FIRE) {
            val id = intent.getLongExtra(AlarmScheduler.EXTRA_ID, -1)
            if (id >= 0) ContextCompat.startForegroundService(context, RingService.intent(context, RingService.ACTION_RING, id))
        } else AlarmScheduler.sync(context)
    }
}
