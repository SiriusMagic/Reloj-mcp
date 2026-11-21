package com.zirakusa.reloj.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.reloj.R
import com.zirakusa.reloj.data.Alarm
import com.zirakusa.reloj.data.Format
import com.zirakusa.reloj.ui.AppActions
import com.zirakusa.reloj.ui.ClockState
import com.zirakusa.reloj.ui.EditorPage
import com.zirakusa.reloj.ui.theme.ClockColors
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle

/** «Agregar alarma» / «Editar alarma» and its inner pages (Repetir, Sonido, Vibrar, Posponer). */
@Composable fun AlarmEditor(state: ClockState, actions: AppActions) {
    val alarm = state.draft ?: return
    when (state.editorPage) {
        EditorPage.REPEAT -> return RepeatPage(alarm, state::change) { state.editorPage = EditorPage.MAIN }
        EditorPage.SOUND -> return SoundPage(alarm.sound, actions, { s -> state.change { it.copy(sound = s) } }) { state.editorPage = EditorPage.MAIN }
        EditorPage.VIBRATION -> return VibrationPage(alarm.vibration, actions, { v -> state.change { it.copy(vibration = v) } }) { state.editorPage = EditorPage.MAIN }
        EditorPage.SNOOZE -> return SnoozePage(alarm, state::change) { state.editorPage = EditorPage.MAIN }
        EditorPage.MAIN -> Unit
    }
    var naming by remember { mutableStateOf(false) }
    val saved = { state.saveDraft(); actions.alarmSaved() }
    Sheet(stringResource(if (alarm.id == 0L) R.string.add_alarm else R.string.edit_alarm), onClose = state::closeEditor) {
        Box(Modifier.weight(1f)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TimeWheels(alarm, state.h24, state::change)
                Group {
                    if (alarm.date == null) Item(stringResource(R.string.repeat), repeatText(alarm)) { state.editorPage = EditorPage.REPEAT }
                    else Divider()
                    SwitchItem(stringResource(R.string.on_date), null, alarm.date != null) { on ->
                        state.change { it.copy(date = if (on) state.now.toLocalDate().plusDays(1) else null) }
                    }
                    alarm.date?.let { d -> Item(stringResource(R.string.alarm_date), dateText(d, locale()), onClick = null); DateWheels(d) { nd -> state.change { it.copy(date = nd) } } }
                    Divider()
                    SwitchItem(stringResource(R.string.off_after), stringResource(R.string.off_after_hint), alarm.offAfterRing || !alarm.repeats, enabled = alarm.repeats) { on ->
                        state.change { it.copy(offAfterRing = on) }
                    }
                    Divider()
                    Item(stringResource(R.string.sound), soundName(alarm.sound, actions)) { state.editorPage = EditorPage.SOUND }
                    Divider()
                    Item(stringResource(R.string.label), alarm.label.ifBlank { null }) { naming = true }
                    Divider()
                    Item(stringResource(R.string.snooze), snoozeText(alarm)) { state.editorPage = EditorPage.SNOOZE }
                    Divider()
                    Item(stringResource(R.string.vibrate), stringResource(vibrationNames.getValue(alarm.vibration))) { state.editorPage = EditorPage.VIBRATION }
                }
                if (alarm.id != 0L) TextButton({ state.delete(alarm.id) }, Modifier.align(Alignment.CenterHorizontally)) {
                    Text(stringResource(R.string.delete_alarm), color = ClockColors.Danger, fontSize = 16.sp)
                }
            }
            MainButton(stringResource(R.string.save), Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp), saved)
        }
    }
    if (naming) TextDialog(stringResource(R.string.label), alarm.label, Alarm.LABEL_MAX, { naming = false }) { text ->
        state.change { it.copy(label = text.trim()) }; naming = false
    }
}

@Composable fun Divider() = HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = ClockColors.Chip)

@Composable private fun TimeWheels(alarm: Alarm, h24: Boolean, change: ((Alarm) -> Alarm) -> Unit) =
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        if (h24) Wheel(24, alarm.hour, { h -> change { it.copy(hour = h) } }, Modifier.weight(1f), name = stringResource(R.string.hour)) { "%02d".format(it) }
        else Wheel(12, Format.hour12(alarm.hour) - 1, { i -> change { it.copy(hour = (i + 1) % 12 + if (it.hour >= 12) 12 else 0) } },
            Modifier.weight(1f), name = stringResource(R.string.hour)) { (it + 1).toString() }
        Wheel(60, alarm.minute, { m -> change { it.copy(minute = m) } }, Modifier.weight(1f), name = stringResource(R.string.minute)) { "%02d".format(it) }
        if (!h24) {
            val am = stringResource(R.string.am); val pm = stringResource(R.string.pm)
            Wheel(2, if (alarm.hour < 12) 0 else 1, { i -> change { it.copy(hour = it.hour % 12 + 12 * i) } }, Modifier.weight(1f), cyclic = false,
                fontSize = 30.sp) { if (it == 0) am else pm }
        }
    }

@Composable private fun DateWheels(date: LocalDate, onChange: (LocalDate) -> Unit) {
    val l = locale()
    val year0 = LocalDate.now().year
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        Wheel(12, date.monthValue - 1, { m -> onChange(date.withDayOfMonth(1).withMonth(m + 1).let { it.withDayOfMonth(date.dayOfMonth.coerceAtMost(it.lengthOfMonth())) }) },
            Modifier.weight(1f), fontSize = 28.sp, row = 48.dp) { Month.of(it + 1).getDisplayName(TextStyle.SHORT_STANDALONE, l).trimEnd('.').replaceFirstChar(Char::uppercase) }
        Wheel(date.lengthOfMonth(), date.dayOfMonth - 1, { d -> onChange(date.withDayOfMonth(d + 1)) }, Modifier.weight(1f), fontSize = 28.sp, row = 48.dp) { (it + 1).toString() }
        Wheel(5, (date.year - year0).coerceIn(0, 4), { y -> onChange(date.withYear(year0 + y).let { if (it.isLeapYear || date.dayOfMonth < 29 || date.monthValue != 2) it else it.withDayOfMonth(28) }) },
            Modifier.weight(1f), cyclic = false, fontSize = 28.sp, row = 48.dp) { (year0 + it).toString() }
    }
}

@Composable fun snoozeText(alarm: Alarm) = if (alarm.snoozeTimes == 0) stringResource(R.string.snooze_off)
    else stringResource(R.string.snooze_summary, alarm.snoozeMinutes, pluralStringResource(R.plurals.times_n, alarm.snoozeTimes, alarm.snoozeTimes))
