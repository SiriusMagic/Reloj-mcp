package com.zirakusa.reloj.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.reloj.R
import com.zirakusa.reloj.data.Format
import com.zirakusa.reloj.ui.AppActions
import com.zirakusa.reloj.ui.ClockState
import com.zirakusa.reloj.ui.Page
import com.zirakusa.reloj.ui.theme.ClockColors

/** «Cronómetro»: the running time in hundredths, the laps (newest first, the fastest and slowest marked) and the controls. */
@Composable fun StopwatchScreen(state: ClockState, actions: AppActions) = Column(Modifier.fillMaxSize()) {
    val w = state.stopwatch
    val click = { if (state.stopwatchSound) actions.tick() }
    TabHeader(stringResource(R.string.tab_stopwatch), null) { state.page = Page.SETTINGS }
    Text(Format.stopwatch(w.elapsed(state.millis)), Modifier.fillMaxWidth().padding(top = if (w.laps.isEmpty()) 96.dp else 24.dp, bottom = 16.dp),
        fontSize = 68.sp, fontWeight = FontWeight.Light, textAlign = TextAlign.Center, maxLines = 1)
    Box(Modifier.weight(1f).fillMaxWidth()) {
        val splits = w.splits()
        val fastest = splits.takeIf { it.size > 1 }?.minOf { it.second }
        val slowest = splits.takeIf { it.size > 1 }?.maxOf { it.second }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 28.dp)) {
            items(splits, key = { it.first }) { (n, lap, total) ->
                val color = when (lap) { fastest -> ClockColors.AccentText; slowest -> ClockColors.Danger; else -> ClockColors.Text }
                Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.lap_n, n), Modifier.weight(1f), color = color, fontSize = 17.sp)
                    Text(Format.stopwatch(lap), Modifier.weight(1f), color = color, fontSize = 17.sp, textAlign = TextAlign.Center)
                    Text(Format.stopwatch(total), Modifier.weight(1f), color = ClockColors.Muted, fontSize = 17.sp, textAlign = TextAlign.End)
                }
                HorizontalDivider(color = ClockColors.Chip)
            }
        }
    }
    Controls {
        RoundButton(Icons.Outlined.Replay, stringResource(R.string.reset), size = 72.dp, enabled = !w.running && !w.idle) { click(); state.resetStopwatch() }
        if (w.running) PlayButton(Icons.Outlined.Pause, stringResource(R.string.pause)) { click(); state.pauseStopwatch() }
        else PlayButton(Icons.Outlined.PlayArrow, stringResource(R.string.start)) { click(); state.startStopwatch() }
        RoundButton(Icons.Outlined.Flag, stringResource(R.string.lap), size = 72.dp, enabled = w.running) { click(); state.lap() }
    }
}
