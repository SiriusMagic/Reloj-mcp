package com.zirakusa.reloj.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.HourglassBottom
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.reloj.R
import com.zirakusa.reloj.RingInfo
import com.zirakusa.reloj.ui.theme.ClockColors
import java.time.ZonedDateTime

/** The ringing alarm over the lock screen: the time now, its name, «Posponer» (when it can) and «Detener». */
@Composable fun RingScreen(info: RingInfo, now: ZonedDateTime, h24: Boolean, onSnooze: () -> Unit, onStop: () -> Unit) =
    Column(Modifier.fillMaxSize().background(ClockColors.Background).systemBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(1f, 1.15f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "scale")
        Spacer(Modifier.weight(0.6f))
        Box(Modifier.size(96.dp).scale(pulse).clip(CircleShape).background(ClockColors.Accent.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
            Icon(if (info.timer) Icons.Outlined.HourglassBottom else Icons.Outlined.Alarm, null, Modifier.size(48.dp), tint = ClockColors.Accent)
        }
        val (time, half) = timeParts(now.hour, now.minute, h24)
        Text(listOfNotNull(time, half).joinToString(" "), Modifier.padding(top = 32.dp), fontSize = 72.sp, fontWeight = FontWeight.Light)
        Text(info.label.ifBlank { stringResource(if (info.timer) R.string.timer_done else R.string.alarm) }, fontSize = 24.sp, textAlign = TextAlign.Center)
        if (info.timer) Text(stringResource(R.string.timer_done_text), color = ClockColors.Muted, fontSize = 16.sp)
        Spacer(Modifier.weight(1f))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (info.canSnooze) Text(stringResource(R.string.snooze_for, info.snoozeMinutes), Modifier.fillMaxWidth().clip(CircleShape).background(ClockColors.Chip)
                .clickable(onClick = onSnooze).padding(vertical = 20.dp), fontSize = 20.sp, textAlign = TextAlign.Center)
            Text(stringResource(R.string.stop), Modifier.fillMaxWidth().clip(CircleShape).background(ClockColors.Accent).clickable(onClick = onStop).padding(vertical = 20.dp),
                color = ClockColors.OnAccent, fontSize = 20.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Medium)
        }
    }
