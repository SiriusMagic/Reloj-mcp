package com.zirakusa.reloj.ui.screens

import com.zirakusa.reloj.IconCard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.reloj.BuildConfig
import com.zirakusa.reloj.R
import com.zirakusa.reloj.data.Settings
import com.zirakusa.reloj.mcp.McpMode
import com.zirakusa.reloj.mcp.RelayLink
import com.zirakusa.reloj.ui.ClockState
import com.zirakusa.reloj.ui.Page
import com.zirakusa.reloj.ui.theme.ClockColors

/** «Ajustes»: alarm, timer and stopwatch options, Temas, Modo MCP, privacy and version. */
@Composable fun SettingsScreen(state: ClockState) {
    var privacy by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(ClockColors.Background).systemBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundButton(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back), size = 52.dp) { state.page = Page.MAIN }
            Text(stringResource(R.string.settings), Modifier.padding(start = 16.dp), fontSize = 22.sp)
        }
        Caption(stringResource(R.string.tab_alarms))
        Group {
            SwitchItem(stringResource(R.string.holidays), stringResource(R.string.holidays_hint), state.skipHolidays, onChange = state::changeSkipHolidays)
            Divider()
            SwitchItem(stringResource(R.string.rising), stringResource(R.string.rising_hint), state.rising, onChange = state::changeRising)
            Divider()
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.ring_minutes), fontSize = 18.sp)
                Segmented(Settings.RING_MINUTES.map { "$it min" }, Settings.RING_MINUTES.indexOf(state.ringMinutes), Modifier.fillMaxWidth()) {
                    state.changeRingMinutes(Settings.RING_MINUTES[it])
                }
            }
        }
        Caption(stringResource(R.string.tab_timer))
        Group { SwitchItem(stringResource(R.string.timer_sound), null, state.timerSound, onChange = state::changeTimerSound) }
        Caption(stringResource(R.string.tab_stopwatch))
        Group { SwitchItem(stringResource(R.string.stopwatch_sound), null, state.stopwatchSound, onChange = state::changeStopwatchSound) }
        Group {
            val status = when {
                !McpMode.active -> stringResource(R.string.mcp_status_off)
                McpMode.hubRedirect -> stringResource(R.string.mcp_redirect)
                McpMode.state == RelayLink.State.ONLINE -> stringResource(R.string.mcp_online)
                McpMode.state == RelayLink.State.CONNECTING -> stringResource(R.string.mcp_connecting)
                else -> stringResource(R.string.mcp_offline)
            }
            Item(stringResource(R.string.mcp_title), "$status · ${stringResource(R.string.mcp_settings_hint)}") { state.page = Page.MCP }
        }
        IconCard(ClockColors.Card, ClockColors.Text, ClockColors.Muted, ClockColors.Accent)
        ThemeCard(state.themeMode, state.accent, state::changeTheme, state::changeAccent)
        Caption(stringResource(R.string.about))
        Group {
            Item(stringResource(R.string.privacy), null) { privacy = true }
            Divider()
            Item(stringResource(R.string.version_title), BuildConfig.VERSION_NAME, onClick = null)
        }
    }
    if (privacy) AlertDialog({ privacy = false }, confirmButton = { TextButton({ privacy = false }) { Text(stringResource(R.string.done), color = ClockColors.AccentText) } },
        title = { Text(stringResource(R.string.privacy)) }, text = { Text(stringResource(R.string.privacy_text)) })
}
