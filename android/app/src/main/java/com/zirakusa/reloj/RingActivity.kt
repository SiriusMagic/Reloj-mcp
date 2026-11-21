package com.zirakusa.reloj

import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.zirakusa.reloj.data.SharedValues
import com.zirakusa.reloj.ui.screens.RingScreen
import com.zirakusa.reloj.ui.theme.Accent
import com.zirakusa.reloj.ui.theme.ClockTheme
import com.zirakusa.reloj.ui.theme.ThemeMode
import kotlinx.coroutines.delay
import java.time.ZonedDateTime

/** The ringing alarm, over the lock screen and with the screen on. Closes when the alarm stops (here or elsewhere). */
class RingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) { setShowWhenLocked(true); setTurnScreenOn(true) }
        else @Suppress("DEPRECATION") window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val settings = com.zirakusa.reloj.data.Settings(SharedValues(this))
        val mode = settings.theme?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.BLACK
        val accent = settings.accent?.let { runCatching { Accent.valueOf(it) }.getOrNull() } ?: Accent.NARANJA
        val h24 = DateFormat.is24HourFormat(this)
        setContent {
            ClockTheme(mode, accent) {
                val info = Ringing.current
                var now by remember { mutableStateOf(ZonedDateTime.now()) }
                LaunchedEffect(Unit) { while (true) { now = ZonedDateTime.now(); delay(1000) } }
                LaunchedEffect(info) { if (info == null) { delay(300); if (Ringing.current == null) finish() } }
                BackHandler {} // the alarm is stopped with its buttons, not by going back
                if (info != null) RingScreen(info, now, h24,
                    onSnooze = { startService(RingService.intent(this, RingService.ACTION_SNOOZE, info.id)) },
                    onStop = { startService(RingService.intent(this, RingService.ACTION_DISMISS, info.id)) })
            }
        }
    }
}
