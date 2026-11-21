package com.zirakusa.reloj.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.reloj.R
import com.zirakusa.reloj.data.Alarm
import com.zirakusa.reloj.data.Schedule
import com.zirakusa.reloj.ui.AppActions
import com.zirakusa.reloj.ui.ClockState
import com.zirakusa.reloj.ui.Issue
import com.zirakusa.reloj.ui.Page
import com.zirakusa.reloj.ui.theme.ClockColors

/** «Alarma»: every alarm with its switch and when it rings; tap to edit, hold to delete. */
@Composable fun AlarmsScreen(state: ClockState, actions: AppActions) {
    var deleting by remember { mutableStateOf<Alarm?>(null) }
    Column(Modifier.fillMaxSize()) {
        TabHeader(stringResource(R.string.tab_alarms), state::newAlarm) { state.page = Page.SETTINGS }
        state.issue?.let { IssueCard(it) { actions.fix(it) } }
        if (state.alarms.isEmpty()) Text(stringResource(R.string.no_alarms), Modifier.fillMaxWidth().padding(32.dp), color = ClockColors.Muted, fontSize = 16.sp)
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 28.dp)) {
            items(state.alarms, key = { it.id }) { alarm ->
                AlarmRow(state, alarm, onClick = { state.edit(alarm) }, onLong = { deleting = alarm })
                HorizontalDivider(color = ClockColors.Chip)
            }
        }
    }
    deleting?.let { a ->
        AlertDialog({ deleting = null }, title = { Text(stringResource(R.string.delete_alarm_title)) },
            text = { Text(timeParts(a.hour, a.minute, state.h24).let { (t, m) -> listOfNotNull(t, m, a.label.ifBlank { null }).joinToString(" ") }) },
            confirmButton = { TextButton({ state.delete(a.id); deleting = null }) { Text(stringResource(R.string.delete), color = ClockColors.Danger) } },
            dismissButton = { TextButton({ deleting = null }) { Text(stringResource(R.string.cancel), color = ClockColors.Text) } })
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun AlarmRow(state: ClockState, alarm: Alarm, onClick: () -> Unit, onLong: () -> Unit) {
    val on = alarm.enabled
    val color = if (on) ClockColors.Text else ClockColors.Muted
    Row(Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLong).padding(vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            val (time, half) = timeParts(alarm.hour, alarm.minute, state.h24)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(time, fontSize = 46.sp, fontWeight = FontWeight.Light, color = color)
                if (half != null) Text(half, Modifier.padding(start = 10.dp, bottom = 8.dp), fontSize = 20.sp, color = color)
            }
            val next = state.nextOf(alarm)
            val parts = listOfNotNull(alarm.label.ifBlank { null }, repeatText(alarm),
                next?.let { if (alarm.snoozedUntil > state.millis) stringResource(R.string.snoozed) else untilText(Schedule.until(state.now, it)) })
            Text(parts.joinToString(". "), fontSize = 15.sp, color = color)
        }
        Switch(on, { state.toggle(alarm, it) }, colors = SwitchDefaults.colors(checkedTrackColor = ClockColors.Accent, checkedThumbColor = ClockColors.OnAccent))
    }
}

/** What stops alarms from ringing well (notifications, full screen, exact time), with the button that fixes it. */
@Composable private fun IssueCard(issue: Issue, onFix: () -> Unit) =
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(ClockColors.Card)
        .clickable(onClick = onFix).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(issue.title), fontSize = 16.sp, color = ClockColors.AccentText, fontWeight = FontWeight.Medium)
        Text(stringResource(issue.text), fontSize = 14.sp, color = ClockColors.Muted)
    }
