package com.zirakusa.reloj.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zirakusa.reloj.R
import com.zirakusa.reloj.data.Cities
import com.zirakusa.reloj.data.City
import com.zirakusa.reloj.data.Format
import com.zirakusa.reloj.ui.ClockState
import com.zirakusa.reloj.ui.Page
import com.zirakusa.reloj.ui.theme.ClockColors
import kotlinx.coroutines.launch
import java.util.Locale

/** «Seleccionar ciudad»: A to Z with the letters on the right, and «Buscar ciudades» at the bottom. */
@Composable fun CityPicker(state: ClockState) = Sheet(stringResource(R.string.pick_city), onClose = { state.page = Page.MAIN }) {
    val l = locale()
    var query by rememberSaveable { mutableStateOf("") }
    val country = { code: String -> if (code.isBlank()) "" else Locale.Builder().setRegion(code).build().getDisplayCountry(l) }
    val found = remember(query) { Cities.search(query, country) }
    val groups = remember(found, query) { if (query.isBlank()) found.groupBy(Cities::letter) else mapOf('#' to found) }
    // Row index of each letter's header, to jump there from the index.
    val starts = remember(groups) { var i = 0; groups.mapValues { (_, list) -> i.also { i += list.size + 1 } } }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val at = state.now.toInstant()
    Box(Modifier.weight(1f).fillMaxWidth()) {
        LazyColumn(Modifier.fillMaxSize().padding(start = 20.dp, end = 36.dp), list) {
            groups.forEach { (letter, cities) ->
                item(key = "h$letter") { if (query.isBlank()) Text(letter.toString(), Modifier.padding(start = 20.dp, top = 12.dp), color = ClockColors.Muted) }
                cities.forEach { city -> item(key = city.id) { CityRow(city, country(city.country), at) { state.addCity(city) } } }
            }
            item { Box(Modifier.padding(bottom = 96.dp)) }
        }
        if (query.isBlank()) Column(Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(32.dp).padding(vertical = 24.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly, horizontalAlignment = Alignment.CenterHorizontally) {
            groups.keys.forEach { letter ->
                Text(letter.toString(), Modifier.clickable { scope.launch { list.scrollToItem(starts.getValue(letter)) } }, fontSize = 13.sp, color = ClockColors.Muted)
            }
        }
        TextField(query, { query = it.take(40) }, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding()
            .padding(horizontal = 24.dp, vertical = 12.dp).fillMaxWidth().clip(CircleShape),
            placeholder = { Text(stringResource(R.string.search_cities)) }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true,
            colors = TextFieldDefaults.colors(focusedContainerColor = ClockColors.Chip, unfocusedContainerColor = ClockColors.Chip,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, cursorColor = ClockColors.Accent))
    }
}

@Composable private fun CityRow(city: City, country: String, at: java.time.Instant, onClick: () -> Unit) =
    Row(Modifier.fillMaxWidth().background(ClockColors.Card).clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp)) {
        Column {
            Text(city.name, fontSize = 19.sp)
            Text(listOf(country, Format.gmt(city.offset(at))).filter { it.isNotBlank() }.joinToString(", "), color = ClockColors.Muted, fontSize = 15.sp)
        }
    }
