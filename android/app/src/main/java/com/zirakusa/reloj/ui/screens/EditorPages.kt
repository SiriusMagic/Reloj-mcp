package com.zirakusa.reloj.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.reloj.R
import com.zirakusa.reloj.data.Alarm
import com.zirakusa.reloj.data.Sound
import com.zirakusa.reloj.data.SoundKind
import com.zirakusa.reloj.data.Vibration
import com.zirakusa.reloj.ui.AppActions
import com.zirakusa.reloj.ui.theme.ClockColors

/** «Repetir»: the days of the week, in the phone's order. */
@Composable fun RepeatPage(alarm: Alarm, change: ((Alarm) -> Alarm) -> Unit, onBack: () -> Unit) = Sheet(stringResource(R.string.repeat), onBack = onBack) {
    val l = locale()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Group {
            com.zirakusa.reloj.data.Schedule.week(firstDay()).forEachIndexed { i, day ->
                if (i > 0) Divider()
                ChoiceRow(dayFull(day, l), day in alarm.days, square = true) {
                    change { it.copy(days = if (day in it.days) it.days - day else it.days + day) }
                }
            }
        }
    }
}

/** «Sonido de la alarma»: «Añadir nuevo» (Filem, or another file app), the user's sounds, silence and the phone's tones. */
@Composable fun SoundPage(current: Sound, actions: AppActions, onPick: (Sound) -> Unit, onBack: () -> Unit) {
    var revision by remember { mutableIntStateOf(0) }
    val custom = remember(revision) { actions.sounds.custom() }
    val system = remember { actions.sounds.system() }
    DisposableEffect(Unit) { onDispose { actions.sounds.stop() } }
    val pick = { s: Sound -> onPick(s); actions.sounds.preview(s) }
    Sheet(stringResource(R.string.sound), onBack = { actions.sounds.stop(); onBack() }) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Caption(stringResource(R.string.your_sounds))
            Group {
                Row(Modifier.fillMaxWidth().clickable { actions.addSound { s -> if (s != null) { revision++; pick(s) } } }.padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.add_new), Modifier.weight(1f), fontSize = 18.sp)
                    Icon(Icons.Outlined.Add, null, tint = ClockColors.Text)
                }
                custom.forEach { s ->
                    Divider()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { ChoiceRow(s.name, current.uri == s.uri) { pick(s) } }
                        IconButton({ actions.sounds.remove(s); if (current.uri == s.uri) onPick(Sound.DEFAULT); revision++ }) {
                            Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.delete), tint = ClockColors.Muted)
                        }
                    }
                }
            }
            Caption(stringResource(R.string.device_sounds))
            Group {
                ChoiceRow(stringResource(R.string.silent), current.kind == SoundKind.SILENT) { actions.sounds.stop(); onPick(Sound.SILENT) }
                Divider()
                ChoiceRow(defaultLabel(actions), current.kind == SoundKind.DEFAULT) { pick(Sound.DEFAULT) }
                system.forEach { s -> Divider(); ChoiceRow(s.name, current.kind == SoundKind.SYSTEM && current.uri == s.uri) { pick(s) } }
            }
        }
    }
}

@Composable private fun defaultLabel(actions: AppActions) =
    actions.sounds.defaultName()?.let { stringResource(R.string.default_named, it) } ?: stringResource(R.string.default_sound)

/** The sound's name as shown in the editor. */
@Composable fun soundName(sound: Sound, actions: AppActions) = when (sound.kind) {
    SoundKind.SILENT -> stringResource(R.string.silent)
    SoundKind.DEFAULT -> stringResource(R.string.default_sound)
    else -> sound.name
}

/** «Vibrar mientras suena»: our patterns, each felt once when chosen, or none. */
@Composable fun VibrationPage(current: Vibration, actions: AppActions, onPick: (Vibration) -> Unit, onBack: () -> Unit) =
    Sheet(stringResource(R.string.vibrate), onBack = onBack) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Group {
                Vibration.entries.filter { it != Vibration.NONE }.forEachIndexed { i, v ->
                    if (i > 0) Divider()
                    ChoiceRow(stringResource(vibrationNames.getValue(v)), v == current) { onPick(v); actions.feel(v) }
                }
            }
            Group { ChoiceRow(stringResource(R.string.vib_none), current == Vibration.NONE) { onPick(Vibration.NONE) } }
        }
    }

val vibrationNames = mapOf(
    Vibration.STANDARD to R.string.vib_standard, Vibration.PULSE to R.string.vib_pulse, Vibration.FAST to R.string.vib_fast,
    Vibration.HEARTBEAT to R.string.vib_heartbeat, Vibration.WALTZ to R.string.vib_waltz, Vibration.WAVES to R.string.vib_waves,
    Vibration.URGENT to R.string.vib_urgent, Vibration.NONE to R.string.vib_none,
)

/** «Posponer»: how many minutes and how many times, or off. */
@Composable fun SnoozePage(alarm: Alarm, change: ((Alarm) -> Alarm) -> Unit, onBack: () -> Unit) = Sheet(stringResource(R.string.snooze), onBack = onBack) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Group { SwitchItem(stringResource(R.string.snooze), snoozeText(alarm), alarm.snoozeTimes > 0) { on -> change { it.copy(snoozeTimes = if (on) 3 else 0) } } }
        if (alarm.snoozeTimes > 0) Group {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.snooze_interval), fontSize = 17.sp)
                Segmented(Alarm.SNOOZE_MINUTES.map { "$it min" }, Alarm.SNOOZE_MINUTES.indexOf(alarm.snoozeMinutes), Modifier.fillMaxWidth()) { i ->
                    change { it.copy(snoozeMinutes = Alarm.SNOOZE_MINUTES[i]) }
                }
                Text(stringResource(R.string.snooze_times), fontSize = 17.sp)
                Segmented(Alarm.SNOOZE_TIMES.map { "$it" }, Alarm.SNOOZE_TIMES.indexOf(alarm.snoozeTimes), Modifier.fillMaxWidth()) { i ->
                    change { it.copy(snoozeTimes = Alarm.SNOOZE_TIMES[i]) }
                }
            }
        }
    }
}
