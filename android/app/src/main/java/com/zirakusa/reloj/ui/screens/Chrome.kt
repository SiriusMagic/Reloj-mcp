package com.zirakusa.reloj.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.reloj.R
import com.zirakusa.reloj.ui.theme.ClockColors

/** Round button with one icon (close, back, reset, lap…). [on] paints it with the accent. */
@Composable fun RoundButton(icon: ImageVector, label: String, modifier: Modifier = Modifier, size: Dp = 48.dp, on: Boolean = false,
    enabled: Boolean = true, onClick: () -> Unit) =
    Box(modifier.size(size).clip(CircleShape).background(if (on) ClockColors.Accent else ClockColors.Chip)
        .clickable(enabled = enabled, onClickLabel = label, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, Modifier.size(size * 0.45f), tint = when { on -> ClockColors.OnAccent; enabled -> ClockColors.Text; else -> ClockColors.Muted })
    }

/** The big round «play» in the middle: a soft accent disc with the accent icon. */
@Composable fun PlayButton(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) =
    Box(modifier.size(88.dp).clip(CircleShape).background(ClockColors.Accent.copy(alpha = 0.25f)).clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center) { Icon(icon, label, Modifier.size(40.dp), tint = ClockColors.Accent) }

/** The header of a tab: the pill with «+» and «⋮» (Ajustes) on the right, and the big title. */
@Composable fun TabHeader(title: String, onAdd: (() -> Unit)?, onSettings: () -> Unit) = Column(Modifier.fillMaxWidth().statusBarsPadding()) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.End) {
        var menu by remember { mutableStateOf(false) }
        Row(Modifier.clip(CircleShape).background(ClockColors.Card).border(1.dp, ClockColors.Chip, CircleShape)) {
            if (onAdd != null) IconSlot(Icons.Outlined.Add, stringResource(R.string.add), onAdd)
            Box { IconSlot(Icons.Outlined.MoreVert, stringResource(R.string.more)) { menu = true }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text(stringResource(R.string.settings)) }, { menu = false; onSettings() })
                }
            }
        }
    }
    Text(title, Modifier.padding(start = 28.dp, top = 12.dp, bottom = 12.dp), fontSize = 36.sp, fontWeight = FontWeight.Medium)
}

@Composable private fun IconSlot(icon: ImageVector, label: String, onClick: () -> Unit) =
    Box(Modifier.size(52.dp).clickable(onClickLabel = label, onClick = onClick), contentAlignment = Alignment.Center) { Icon(icon, label, tint = ClockColors.Text) }

/** A page that slides over the tabs, like a sheet: title in the middle, close (X) on the right or back (←) on the left. */
@Composable fun Sheet(title: String, onClose: (() -> Unit)? = null, onBack: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) =
    Box(Modifier.fillMaxSize().background(ClockColors.Background).statusBarsPadding().padding(top = 16.dp)) {
        Column(Modifier.fillMaxSize().clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)).background(ClockColors.Sheet)) {
            Box(Modifier.fillMaxWidth().padding(16.dp).height(52.dp)) {
                if (onBack != null) RoundButton(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back), Modifier.align(Alignment.CenterStart), 52.dp, onClick = onBack)
                Text(title, Modifier.align(Alignment.Center).padding(horizontal = 60.dp), fontSize = 20.sp, textAlign = TextAlign.Center, maxLines = 1)
                if (onClose != null) RoundButton(Icons.Outlined.Close, stringResource(R.string.close), Modifier.align(Alignment.CenterEnd), 52.dp, onClick = onClose)
            }
            content()
        }
    }

/** A rounded card of rows. */
@Composable fun Group(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) =
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(ClockColors.Card), content = content)

/** One choice among a few, as a pill. */
@Composable fun Segmented(options: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) =
    Row(modifier.clip(CircleShape).background(ClockColors.Chip).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEachIndexed { i, label ->
            Text(label, Modifier.weight(1f).clip(CircleShape).background(if (i == selected) ClockColors.Accent else Color.Transparent)
                .clickable { onSelect(i) }.padding(horizontal = 6.dp, vertical = 9.dp),
                color = if (i == selected) ClockColors.OnAccent else ClockColors.Text, fontSize = 14.sp, textAlign = TextAlign.Center,
                maxLines = 1, fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Normal)
        }
    }

/** The wide accent button at the bottom («Guardar»). */
@Composable fun MainButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) =
    Text(text, modifier.fillMaxWidth(0.62f).clip(CircleShape).background(ClockColors.Accent).clickable(onClick = onClick).padding(vertical = 16.dp),
        color = ClockColors.OnAccent, fontSize = 18.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Medium)

/** A row of three round controls (left, the big one in the middle, right), as in Temporizador and Cronómetro. */
@Composable fun Controls(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) =
    Row(modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 16.dp), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically, content = content)
