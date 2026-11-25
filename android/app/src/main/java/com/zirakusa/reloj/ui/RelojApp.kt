package com.zirakusa.reloj.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.reloj.R
import com.zirakusa.reloj.data.Sound
import com.zirakusa.reloj.data.Vibration
import com.zirakusa.reloj.ui.screens.AlarmEditor
import com.zirakusa.reloj.ui.screens.AlarmsScreen
import com.zirakusa.reloj.ui.screens.CityPicker
import com.zirakusa.reloj.ui.screens.SettingsScreen
import com.zirakusa.reloj.ui.screens.SoundPage
import com.zirakusa.reloj.ui.screens.StopwatchScreen
import com.zirakusa.reloj.ui.screens.TimerEditor
import com.zirakusa.reloj.ui.screens.TimerScreen
import com.zirakusa.reloj.ui.screens.WorldScreen
import com.zirakusa.reloj.ui.theme.ClockColors
import kotlinx.coroutines.delay

/** The tones the sound page lists and plays (the activity gives the phone's; tests a fixed list). */
interface SoundSource {
    fun system(): List<Sound> = emptyList()
    fun custom(): List<Sound> = emptyList()
    fun defaultName(): String? = null
    fun preview(sound: Sound) {}
    fun stop() {}
    fun remove(sound: Sound) {}
}

/** What keeps alarms from ringing well, and how the card on «Alarma» explains it. */
enum class Issue(val title: Int, val text: Int) {
    NOTIFICATIONS(R.string.issue_notifications, R.string.issue_notifications_text),
    FULL_SCREEN(R.string.issue_full_screen, R.string.issue_full_screen_text),
    EXACT(R.string.issue_exact, R.string.issue_exact_text),
}

/** What the screens ask of the phone (the activity gives the real ones; tests leave the defaults). */
class AppActions(
    val sounds: SoundSource = object : SoundSource {},
    /** «Añadir nuevo»: Filem when installed, otherwise the phone's file apps; null when nothing usable came back. */
    val addSound: ((Sound?) -> Unit) -> Unit = {},
    val feel: (Vibration) -> Unit = {},
    val tick: () -> Unit = {},
    val alarmSaved: () -> Unit = {},
    val fix: (Issue) -> Unit = {},
    val copy: (String) -> Unit = {},
    val openArka: () -> Unit = {},
)

/** The whole app: one tab with the floating bar, or a page over it. [ticking] moves the clocks (off in tests). */
@Composable fun RelojApp(state: ClockState, actions: AppActions = AppActions(), ticking: Boolean = true) {
    if (ticking) LaunchedEffect(state.tab, state.stopwatch.running, state.timer.running) {
        while (true) {
            state.tick()
            delay(if (state.tab == Tab.STOPWATCH && state.stopwatch.running) 31 else if (state.tab == Tab.WORLD || state.timer.running) 200 else 1000)
        }
    }
    when (state.page) {
        Page.EDITOR -> return AlarmEditor(state, actions)
        Page.CITIES -> return CityPicker(state)
        Page.TIMER_EDITOR -> return TimerEditor(state)
        Page.TIMER_SOUND -> return SoundPage(state.timerTone, actions, state::changeTimerTone) { state.page = Page.MAIN }
        Page.SETTINGS -> return SettingsScreen(state, actions.openArka)
        Page.MAIN -> Unit
    }
    Box(Modifier.fillMaxSize().background(ClockColors.Background)) {
        Column(Modifier.fillMaxSize().padding(bottom = 96.dp).navigationBarsPadding()) {
            when (state.tab) {
                Tab.ALARMS -> AlarmsScreen(state, actions)
                Tab.WORLD -> WorldScreen(state)
                Tab.TIMER -> TimerScreen(state)
                Tab.STOPWATCH -> StopwatchScreen(state, actions)
            }
        }
        BottomBar(state.tab, Modifier.align(Alignment.BottomCenter)) { state.tab = it }
    }
}

private val tabs = listOf(
    Triple(Tab.ALARMS, Icons.Outlined.Alarm, R.string.tab_alarms),
    Triple(Tab.WORLD, Icons.Outlined.Public, R.string.tab_world),
    Triple(Tab.TIMER, Icons.Outlined.HourglassEmpty, R.string.tab_timer),
    Triple(Tab.STOPWATCH, Icons.Outlined.Timer, R.string.tab_stopwatch),
)

@Composable private fun BottomBar(selected: Tab, modifier: Modifier, onSelect: (Tab) -> Unit) =
    Row(modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth().clip(CircleShape).background(ClockColors.Card)
        .border(1.dp, ClockColors.Chip, CircleShape).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
        tabs.forEach { (tab, icon, label) -> TabButton(icon, stringResource(label), tab == selected, Modifier.weight(1f)) { onSelect(tab) } }
    }

@Composable private fun TabButton(icon: ImageVector, label: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) =
    Column(modifier.clip(CircleShape).background(if (on) ClockColors.Chip else ClockColors.Card).clickable(onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, Modifier.size(26.dp), tint = if (on) ClockColors.Accent else ClockColors.Text)
        Text(label, Modifier.padding(top = 2.dp, start = 2.dp, end = 2.dp), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (on) ClockColors.AccentText else ClockColors.Text)
    }
