package com.zirakusa.reloj.ui.screens

import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.reloj.ui.theme.ClockColors
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs

/**
 * A scroll wheel of [count] values (hours, minutes, a.m./p.m., months…): the centred one is chosen; farther rows fade.
 * [cyclic] wheels go round (59 → 00). Changing [selected] from outside scrolls it there.
 */
@Composable fun Wheel(count: Int, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, cyclic: Boolean = true,
    row: Dp = 64.dp, visible: Int = 3, fontSize: TextUnit = 40.sp, name: String = "", label: (Int) -> String) {
    val rows = if (cyclic) count * 400 else count
    val start = if (cyclic) count * 200 else 0
    val state = rememberLazyListState(start + selected.coerceIn(0, count - 1))
    val rowPx = with(LocalDensity.current) { row.toPx() }
    val centre by remember { derivedStateOf { state.firstVisibleItemIndex + if (state.firstVisibleItemScrollOffset > rowPx / 2) 1 else 0 } }
    val pick by rememberUpdatedState(onSelect)
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress to centre }.distinctUntilChanged().collect { (moving, c) -> if (!moving) pick(c.mod(count)) }
    }
    LaunchedEffect(selected, count) {
        if (centre.mod(count) != selected && !state.isScrollInProgress) state.scrollToItem(centre - centre.mod(count) + selected.coerceIn(0, count - 1))
    }
    LazyColumn(modifier.height(row * visible).semantics { contentDescription = "$name ${label(selected)}" }, state,
        contentPadding = PaddingValues(vertical = row * (visible / 2)), flingBehavior = rememberSnapFlingBehavior(state)) {
        items(rows) { i ->
            val far = abs(i - centre)
            Box(Modifier.fillMaxWidth().height(row), contentAlignment = Alignment.Center) {
                Text(label(i.mod(count)), fontSize = if (far == 0) fontSize else fontSize * 0.72f, fontWeight = if (far == 0) FontWeight.Normal else FontWeight.Light,
                    color = if (far == 0) ClockColors.Text else ClockColors.Muted.copy(alpha = if (far == 1) 0.7f else 0.35f))
            }
        }
    }
}
