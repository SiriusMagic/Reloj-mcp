package com.zirakusa.reloj

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.zirakusa.reloj.data.Format
import com.zirakusa.reloj.data.Settings
import com.zirakusa.reloj.data.SharedValues
import com.zirakusa.reloj.data.Vibration

/**
 * Rings: sound on the alarm stream, vibration and a full-screen notification that opens [RingActivity] over the lock
 * screen, with «Posponer» and «Detener». After the minutes of «Silenciar tras» it snoozes (or stops) by itself.
 */
class RingService : Service() {
    private val player by lazy { Player(this) }
    private val handler = Handler(Looper.getMainLooper())
    private var ringing: Long? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getLongExtra(AlarmScheduler.EXTRA_ID, -1) ?: -1
        when (intent?.action) {
            ACTION_RING -> ring(id)
            ACTION_SNOOZE -> { Ringing.snooze(this, id); finish(id) }
            ACTION_DISMISS -> { Ringing.dismiss(this, id); finish(id) }
            else -> if (ringing == null) stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun ring(id: Long) {
        val info = Ringing.describe(this, id)
        if (info == null) { if (ringing == null) { foreground(placeholder()); stopNow() }; return }
        ringing?.takeIf { it != id }?.let { Ringing.snooze(this, it) } // a new alarm replaces the one ringing
        ringing = id
        Ringing.current = info
        foreground(notification(info))
        val settings = Settings(SharedValues(this))
        player.play(info.sound, rising = settings.rising && !info.timer)
        vibrate(info.vibration)
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ Ringing.snooze(this, id); finish(id) }, settings.ringMinutes * 60_000L)
    }

    private fun finish(id: Long) {
        if (ringing != null && ringing != id) return
        stopNow()
    }

    private fun stopNow() {
        handler.removeCallbacksAndMessages(null)
        player.stop()
        vibrator()?.cancel()
        ringing = null
        Ringing.current = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() { handler.removeCallbacksAndMessages(null); player.stop(); vibrator()?.cancel(); Ringing.current = null; super.onDestroy() }

    private fun vibrator(): Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        getSystemService(VibratorManager::class.java)?.defaultVibrator else @Suppress("DEPRECATION") getSystemService(Vibrator::class.java)

    private fun vibrate(v: Vibration) {
        if (v == Vibration.NONE) return
        vibrator()?.takeIf { it.hasVibrator() }?.vibrate(VibrationEffect.createWaveform(v.pattern, 0))
    }

    private fun foreground(n: Notification) = ServiceCompat.startForeground(this, NOTIFICATION, n,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0)

    private fun channel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.channel_alarms), NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(null, null); enableVibration(false); setBypassDnd(true)
        })
    }

    private fun placeholder(): Notification { channel(); return NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_stat_alarm).build() }

    private fun notification(info: RingInfo): Notification {
        channel()
        val screen = PendingIntent.getActivity(this, 1, Intent(this, RingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val title = info.label.ifBlank { getString(if (info.timer) R.string.timer_done else R.string.alarm) }
        val text = if (info.timer) getString(R.string.timer_done_text) else "%d:%02d".format(Format.hour12(info.hour), info.minute)
        return NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_stat_alarm).setContentTitle(title).setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_ALARM).setPriority(NotificationCompat.PRIORITY_MAX).setOngoing(true)
            .setFullScreenIntent(screen, true).setContentIntent(screen).setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .apply { if (info.canSnooze) addAction(0, getString(R.string.snooze), action(ACTION_SNOOZE, info.id)) }
            .addAction(0, getString(R.string.stop), action(ACTION_DISMISS, info.id)).build()
    }

    private fun action(name: String, id: Long) = PendingIntent.getService(this, name.hashCode(), intent(this, name, id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    companion object {
        const val ACTION_RING = "com.zirakusa.reloj.action.RING"
        const val ACTION_SNOOZE = "com.zirakusa.reloj.action.SNOOZE"
        const val ACTION_DISMISS = "com.zirakusa.reloj.action.DISMISS"
        const val CHANNEL = "alarms"
        private const val NOTIFICATION = 7
        fun intent(context: Context, action: String, id: Long): Intent =
            Intent(context, RingService::class.java).setAction(action).putExtra(AlarmScheduler.EXTRA_ID, id)
    }
}
