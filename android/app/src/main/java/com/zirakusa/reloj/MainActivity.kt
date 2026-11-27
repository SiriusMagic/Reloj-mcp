package com.zirakusa.reloj

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.provider.AlarmClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.zirakusa.reloj.data.Sound
import com.zirakusa.reloj.data.SharedValues
import com.zirakusa.reloj.data.Vibration
import com.zirakusa.reloj.mcp.McpMode
import com.zirakusa.reloj.mcp.showMcpOptions
import com.zirakusa.reloj.ui.AppActions
import com.zirakusa.reloj.ui.ClockState
import com.zirakusa.reloj.ui.EditorPage
import com.zirakusa.reloj.ui.Issue
import com.zirakusa.reloj.ui.Page
import com.zirakusa.reloj.ui.RelojApp
import com.zirakusa.reloj.ui.SoundSource
import com.zirakusa.reloj.ui.Tab
import com.zirakusa.reloj.ui.theme.ClockColors
import com.zirakusa.reloj.ui.theme.ClockTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Creates the state, keeps Android's schedule in step, and does what the screens ask (sounds, permissions, copy). */
class MainActivity : ComponentActivity() {
    private lateinit var state: ClockState
    private val preview by lazy { Player(this) }
    private val tone by lazy { runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 60) }.getOrNull() }
    private var soundPicked: ((Sound?) -> Unit)? = null
    private var asked = 0
    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { asked++; checkIssues() }
    private val picker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        val done = soundPicked.also { soundPicked = null } ?: return@registerForActivityResult
        if (uri == null) { done(null); return@registerForActivityResult }
        lifecycleScope.launch {
            val sound = withContext(Dispatchers.IO) { Sounds.import(this@MainActivity, uri) }
            if (sound == null) Toast.makeText(this@MainActivity, R.string.sound_failed, Toast.LENGTH_LONG).show()
            done(sound)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppIcon.load(this)
        enableEdgeToEdge()
        state = ClockState(SharedValues(this), onChanged = { AlarmScheduler.sync(this); checkIssues() })
        McpMode.onChanged = { state.reload() }
        McpMode.load(this)
        AlarmScheduler.sync(this)
        val actions = AppActions(sounds = sounds, addSound = ::addSound, feel = ::feel, tick = { tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 40) },
            alarmSaved = ::askNotifications, fix = ::fix, openArka = ::openArka)
        if (savedInstanceState == null && order(intent)) return
        setContent {
            ClockTheme(state.themeMode, state.accent) {
                val light = ClockColors.isLight
                SideEffect {
                    WindowCompat.getInsetsController(window, window.decorView).run { isAppearanceLightStatusBars = light; isAppearanceLightNavigationBars = light }
                }
                LaunchedEffect(Ringing.revision) { state.reload() }
                BackHandler(state.page != Page.MAIN || state.tab != Tab.ALARMS, ::back)
                RelojApp(state, actions)
            }
        }
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); order(intent) }

    /**
     * Android's standard orders (FAMILIA.md §10): «poner alarma», «poner temporizador», «ver alarmas», «ver temporizadores».
     * With «sin mostrar» (EXTRA_SKIP_UI) it only says so and closes. True when Reloj closed itself.
     */
    private fun order(intent: Intent?): Boolean {
        val skip = intent?.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI, false) == true
        val message: Int; val arg: String
        when (intent?.action) {
            AlarmClock.ACTION_SET_ALARM -> {
                if (!intent.hasExtra(AlarmClock.EXTRA_HOUR)) { state.newAlarm(); return false }
                val a = state.alarmFromOrder(intent.getIntExtra(AlarmClock.EXTRA_HOUR, -1), intent.getIntExtra(AlarmClock.EXTRA_MINUTES, 0),
                    intent.getStringExtra(AlarmClock.EXTRA_MESSAGE), intent.getIntegerArrayListExtra(AlarmClock.EXTRA_DAYS)) ?: return false
                askNotifications(); message = R.string.order_alarm; arg = a.time
            }
            AlarmClock.ACTION_SET_TIMER -> {
                if (!intent.hasExtra(AlarmClock.EXTRA_LENGTH)) { state.tab = Tab.TIMER; return false }
                if (!state.timerFromOrder(intent.getIntExtra(AlarmClock.EXTRA_LENGTH, 0), intent.getStringExtra(AlarmClock.EXTRA_MESSAGE))) return false
                val s = intent.getIntExtra(AlarmClock.EXTRA_LENGTH, 0); message = R.string.order_timer; arg = "%d:%02d".format(s / 60, s % 60)
            }
            AlarmClock.ACTION_SHOW_ALARMS -> { state.tab = Tab.ALARMS; state.page = Page.MAIN; return false }
            AlarmClock.ACTION_SHOW_TIMERS -> { state.tab = Tab.TIMER; state.page = Page.MAIN; return false }
            else -> return false
        }
        Toast.makeText(this, getString(message, arg), Toast.LENGTH_SHORT).show()
        if (skip) { finish(); return true }
        return false
    }

    override fun onResume() {
        super.onResume()
        state.h24 = DateFormat.is24HourFormat(this)
        state.reload()
        checkIssues()
    }

    override fun onStop() { super.onStop(); preview.stop() }
    override fun onDestroy() { super.onDestroy(); tone?.release() }

    private fun back() = when {
        state.page == Page.EDITOR && state.editorPage != EditorPage.MAIN -> state.editorPage = EditorPage.MAIN
        state.page == Page.EDITOR -> state.closeEditor()
        state.page != Page.MAIN -> state.page = Page.MAIN
        else -> state.tab = Tab.ALARMS
    }

    private val sounds = object : SoundSource {
        private val system by lazy { Sounds.system(this@MainActivity) }
        override fun system() = system
        override fun custom() = Sounds.custom(this@MainActivity)
        override fun defaultName() = Sounds.defaultName(this@MainActivity)
        override fun preview(sound: Sound) = preview.play(sound, loop = false)
        override fun stop() = preview.stop()
        override fun remove(sound: Sound) { preview.stop(); Sounds.remove(this@MainActivity, sound) }
    }

    /**
     * «Añadir nuevo»: our Filem opens when it is installed; otherwise the phone offers the file apps it has (or its own
     * document picker). The chosen file is copied into Reloj.
     */
    private fun addSound(done: (Sound?) -> Unit) {
        preview.stop()
        soundPicked = done
        val pick = Intent(Intent.ACTION_GET_CONTENT).setType("audio/*").addCategory(Intent.CATEGORY_OPENABLE)
        val filem = Intent(pick).setPackage(FILEM)
        val intent = when {
            filem.resolveActivity(packageManager) != null -> filem
            pick.resolveActivity(packageManager) != null -> Intent.createChooser(pick, getString(R.string.pick_sound_with))
            else -> Intent(Intent.ACTION_OPEN_DOCUMENT).setType("audio/*").addCategory(Intent.CATEGORY_OPENABLE)
        }
        try { picker.launch(intent) } catch (_: ActivityNotFoundException) {
            soundPicked = null; done(null); Toast.makeText(this, R.string.no_file_app, Toast.LENGTH_LONG).show()
        }
    }

    private fun feel(v: Vibration) {
        if (v == Vibration.NONE) return
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) getSystemService(VibratorManager::class.java)?.defaultVibrator
            else @Suppress("DEPRECATION") getSystemService(Vibrator::class.java)
        vibrator?.vibrate(VibrationEffect.createWaveform(v.pattern + v.pattern.drop(1), -1))
    }

    private fun notificationsOff() = !NotificationManagerCompat.from(this).areNotificationsEnabled()

    /** The card on «Alarma» when alarms would not ring well; only while some alarm or the timer is on. */
    private fun checkIssues() {
        val needed = state.alarms.any { it.enabled } || state.timer.running
        val alarms = getSystemService(AlarmManager::class.java)
        val notifications = getSystemService(NotificationManager::class.java)
        state.issue = when {
            !needed -> null
            notificationsOff() -> Issue.NOTIFICATIONS
            Build.VERSION.SDK_INT >= 34 && notifications?.canUseFullScreenIntent() == false -> Issue.FULL_SCREEN
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarms?.canScheduleExactAlarms() == false -> Issue.EXACT
            else -> null
        }
    }

    /** After the first alarm: ask for notifications (Android 13+), which carry the ringing screen. */
    private fun askNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && asked < 2 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun fix(issue: Issue) {
        val app = "package:$packageName".toUri()
        val intent = when (issue) {
            Issue.NOTIFICATIONS -> if (Build.VERSION.SDK_INT >= 33 && asked < 2) { askNotifications(); return }
                else Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            Issue.FULL_SCREEN -> if (Build.VERSION.SDK_INT >= 34) Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, app) else return
            Issue.EXACT -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, app) else return
        }
        try { startActivity(intent) } catch (_: ActivityNotFoundException) { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, app)) }
    }

    private fun openArka() = showMcpOptions(this)

    private companion object { const val FILEM = "com.zirakusa.filem" }
}
