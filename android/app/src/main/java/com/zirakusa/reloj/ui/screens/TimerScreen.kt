package com.zirakusa.reloj.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.reloj.R
import com.zirakusa.reloj.data.Format
import com.zirakusa.reloj.data.Preset
import com.zirakusa.reloj.ui.ClockState
import com.zirakusa.reloj.ui.Page
import com.zirakusa.reloj.ui.theme.ClockColors

/** «Temporizador»: wheels and the saved timers while idle; the ring counting down while it runs. */
@Composable fun TimerScreen(state: ClockState) = Column(Modifier.fillMaxSize()) {
    val t = state.timer
    TabHeader(stringResource(R.string.tab_timer), { state.page = Page.TIMER_EDITOR }) { state.page = Page.SETTINGS }
    Box(Modifier.weight(1f).fillMaxWidth()) { if (t.idle) Idle(state) else Running(state) }
    Controls {
        RoundButton(Icons.Outlined.Close, stringResource(R.string.cancel), size = 72.dp, enabled = !t.idle, onClick = state::resetTimer)
        when {
            t.idle -> PlayButton(Icons.Outlined.PlayArrow, stringResource(R.string.start)) { state.startTimer(state.lastTimer) }
            t.running -> PlayButton(Icons.Outlined.Pause, stringResource(R.string.pause), onClick = state::pauseTimer)
            else -> PlayButton(Icons.Outlined.PlayArrow, stringResource(R.string.resume), onClick = state::resumeTimer)
        }
        if (t.idle) RoundButton(Icons.Outlined.NotificationsNone, stringResource(R.string.timer_sound), size = 72.dp) { state.page = Page.TIMER_SOUND }
        else Box(Modifier.width(72.dp), contentAlignment = Alignment.Center) {
            TextButton(state::plusMinute) { Text("+1:00", color = ClockColors.Text, fontSize = 18.sp) }
        }
    }
}

/** Hours, minutes and seconds wheels («00 h 05 m 00 s»). */
@Composable fun DurationWheels(ms: Long, onChange: (Long) -> Unit) {
    val s = ms / 1000
    val h = (s / 3600).toInt().coerceAtMost(23); val m = (s / 60 % 60).toInt(); val sec = (s % 60).toInt()
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(Triple(24, h, "h"), Triple(60, m, "m"), Triple(60, sec, "s")).forEachIndexed { i, (count, value, unit) ->
            Wheel(count, value, { v ->
                val parts = intArrayOf(h, m, sec).also { it[i] = v }
                onChange((parts[0] * 3600L + parts[1] * 60L + parts[2]) * 1000)
            }, Modifier.weight(1f), fontSize = 56.sp, row = 84.dp, name = unit) { "%02d".format(it) }
            Text(unit, color = ClockColors.Muted, fontSize = 20.sp)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun Idle(state: ClockState) {
    var removing by remember { mutableStateOf<Preset?>(null) }
    LazyColumn(Modifier.fillMaxSize()) {
        item { DurationWheels(state.lastTimer, state::changeLastTimer) }
        items(state.presets) { p ->
            Row(Modifier.fillMaxWidth().combinedClickable(onClick = { state.startTimer(p.ms, p.name) }, onLongClick = { removing = p })
                .padding(horizontal = 28.dp, vertical = 20.dp)) {
                Text(p.name.ifBlank { stringResource(R.string.tab_timer) }, Modifier.weight(1f), fontSize = 19.sp)
                Text(Format.countdown(p.ms).let { if (p.ms < 3_600_000) "00:$it" else it }, fontSize = 19.sp)
            }
            HorizontalDivider(Modifier.padding(horizontal = 28.dp), color = ClockColors.Chip)
        }
    }
    removing?.let { p ->
        AlertDialog({ removing = null }, title = { Text(stringResource(R.string.delete_timer, p.name)) },
            confirmButton = { TextButton({ state.removePreset(p); removing = null }) { Text(stringResource(R.string.delete), color = ClockColors.Danger) } },
            dismissButton = { TextButton({ removing = null }) { Text(stringResource(R.string.cancel), color = ClockColors.Text) } })
    }
}

@Composable private fun Running(state: ClockState) = Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
    val t = state.timer
    val left = t.left(state.millis)
    val accent = ClockColors.Accent; val track = ClockColors.Chip
    Box(Modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 16.dp).aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = 8.dp.toPx()
            drawArc(track, 0f, 360f, false, Offset(w / 2, w / 2), Size(size.width - w, size.height - w), style = Stroke(w))
            drawArc(accent, -90f, 360f * (1 - t.progress(state.millis)), false, Offset(w / 2, w / 2), Size(size.width - w, size.height - w),
                style = Stroke(w, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (state.timerName.isNotBlank()) Text(state.timerName, color = ClockColors.Muted, fontSize = 18.sp)
            Text(Format.countdown(left), fontSize = 60.sp, fontWeight = FontWeight.Light)
            val end = state.now.plusNanos(left * 1_000_000)
            val (time, half) = timeParts(end.hour, end.minute, state.h24)
            Text(stringResource(if (t.running) R.string.ends_at else R.string.paused, listOfNotNull(time, half).joinToString(" ")),
                color = if (t.running) ClockColors.Muted else ClockColors.AccentText, fontSize = 16.sp)
        }
    }
}

/** «Agregar temporizador»: a duration and a name, saved under the wheels. */
@Composable fun TimerEditor(state: ClockState) {
    var ms by remember { mutableLongStateOf(30 * 60_000L) }
    var name by remember { mutableStateOf("") }
    var naming by remember { mutableStateOf(false) }
    Sheet(stringResource(R.string.add_timer), onClose = { state.page = Page.MAIN }) {
        Box(Modifier.weight(1f)) {
            Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                DurationWheels(ms) { ms = it }
                Group(Modifier.padding(top = 16.dp)) { Item(stringResource(R.string.timer_name), name.ifBlank { stringResource(R.string.timer_name_auto) }) { naming = true } }
            }
            MainButton(stringResource(R.string.save), Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp)) {
                state.addPreset(name.ifBlank { Format.countdown(ms) }, ms)
            }
        }
    }
    if (naming) TextDialog(stringResource(R.string.timer_name), name, Preset.NAME_MAX, { naming = false }) { name = it.trim(); naming = false }
}
