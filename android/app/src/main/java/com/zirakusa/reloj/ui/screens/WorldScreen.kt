package com.zirakusa.reloj.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.reloj.R
import com.zirakusa.reloj.data.City
import com.zirakusa.reloj.data.Format
import com.zirakusa.reloj.ui.ClockState
import com.zirakusa.reloj.ui.Page
import com.zirakusa.reloj.ui.theme.ClockColors
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** «Reloj mundial»: the phone's time (tap the clock: analog ↔ digital with seconds), then the cities added. */
@OptIn(ExperimentalFoundationApi::class)
@Composable fun WorldScreen(state: ClockState) {
    var removing by remember { mutableStateOf<City?>(null) }
    val now = state.now
    val l = locale()
    Column(Modifier.fillMaxSize()) {
        TabHeader(stringResource(R.string.tab_world), { state.page = Page.CITIES }) { state.page = Page.SETTINGS }
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                val tap = Modifier.clickable(remember { MutableInteractionSource() }, null, onClickLabel = stringResource(R.string.switch_clock)) { state.toggleAnalog() }
                if (state.analog) AnalogClock(now, tap.fillMaxWidth().padding(horizontal = 56.dp, vertical = 16.dp).aspectRatio(1f))
                else DigitalClock(now, state.h24, tap.fillMaxWidth().padding(vertical = 56.dp))
                val zone = TimeZone.getTimeZone(now.zone).getDisplayName(TimeZone.getTimeZone(now.zone).inDaylightTime(java.util.Date.from(now.toInstant())), TimeZone.LONG, l)
                Text("${DateTimeFormatter.ofPattern("EEE, d MMM", l).format(now).replace(".", "")}    $zone", Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    color = ClockColors.Muted, fontSize = 16.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
            items(state.cities, key = { it.id }) { city ->
                val there = now.withZoneSameInstant(ZoneId.of(city.zone))
                val diff = (there.offset.totalSeconds - now.offset.totalSeconds) / 60
                val day = there.toLocalDate().compareTo(now.toLocalDate())
                Row(Modifier.fillMaxWidth().combinedClickable(onClick = {}, onLongClick = { removing = city }).padding(horizontal = 28.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(city.name, fontSize = 20.sp)
                        Text("${stringResource(when { day < 0 -> R.string.yesterday; day > 0 -> R.string.tomorrow; else -> R.string.today })}, ${differenceText(diff)}",
                            color = ClockColors.Muted, fontSize = 14.sp)
                    }
                    val (t, half) = timeParts(there.hour, there.minute, state.h24)
                    Text(t, fontSize = 36.sp, fontWeight = FontWeight.Light)
                    if (half != null) Text(half, Modifier.padding(start = 6.dp), fontSize = 16.sp)
                }
                HorizontalDivider(Modifier.padding(horizontal = 28.dp), color = ClockColors.Chip)
            }
        }
    }
    removing?.let { c ->
        AlertDialog({ removing = null }, title = { Text(stringResource(R.string.remove_city, c.name)) },
            confirmButton = { TextButton({ state.removeCity(c); removing = null }) { Text(stringResource(R.string.delete), color = ClockColors.Danger) } },
            dismissButton = { TextButton({ removing = null }) { Text(stringResource(R.string.cancel), color = ClockColors.Text) } })
    }
}

@Composable private fun DigitalClock(now: ZonedDateTime, h24: Boolean, modifier: Modifier) = Row(modifier, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    verticalAlignment = Alignment.Bottom) {
    val (t, half) = timeParts(now.hour, now.minute, h24)
    Text("$t:%02d".format(now.second), fontSize = 72.sp, fontWeight = FontWeight.Light)
    if (half != null) Text(half, Modifier.padding(start = 6.dp, bottom = 14.dp), fontSize = 22.sp, fontWeight = FontWeight.Medium)
}

/** A round face with the hour numbers, minute ticks, white hands and the accent second hand. */
@Composable fun AnalogClock(now: ZonedDateTime, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val face = ClockColors.Card; val text = ClockColors.Text; val muted = ClockColors.Lines; val accent = ClockColors.Accent
    val numberStyle = TextStyle(color = text, fontSize = 26.sp)
    Canvas(modifier.semantics { contentDescription = "%d:%02d".format(Format.hour12(now.hour), now.minute) }) {
        val r = size.minDimension / 2; val c = center
        drawCircle(face, r)
        for (i in 0 until 60) {
            val a = i * 6.0 * PI / 180
            val long = i % 5 == 0
            val outer = r * 0.95f; val inner = r * if (long) 0.86f else 0.9f
            drawLine(if (long) text else muted, point(c, a, inner), point(c, a, outer), (if (long) 3.dp else 2.dp).toPx(), StrokeCap.Round)
        }
        for (h in 1..12) {
            val layout = measurer.measure(h.toString(), numberStyle)
            val p = point(c, h * 30.0 * PI / 180, r * 0.72f)
            drawText(layout, topLeft = Offset(p.x - layout.size.width / 2f, p.y - layout.size.height / 2f))
        }
        val sec = now.second + now.nano / 1e9
        val min = now.minute + sec / 60; val hour = now.hour % 12 + min / 60
        hand(c, hour * 30 * PI / 180, r * 0.5f, 0f, 7.dp.toPx(), text)
        hand(c, min * 6 * PI / 180, r * 0.8f, 0f, 5.dp.toPx(), text)
        hand(c, sec * 6 * PI / 180, r * 0.9f, r * 0.2f, 2.dp.toPx(), accent)
        drawCircle(accent, 6.dp.toPx(), c)
        drawCircle(face, 3.dp.toPx(), c)
    }
}

private fun point(c: Offset, angle: Double, radius: Float) = Offset(c.x + radius * sin(angle).toFloat(), c.y - radius * cos(angle).toFloat())

private fun DrawScope.hand(c: Offset, angle: Double, length: Float, tail: Float, width: Float, color: androidx.compose.ui.graphics.Color) =
    drawLine(color, point(c, angle + PI, tail), point(c, angle, length), width, StrokeCap.Round)

