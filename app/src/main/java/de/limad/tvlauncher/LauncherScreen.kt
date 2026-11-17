package de.limad.tvlauncher

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Background = Brush.verticalGradient(listOf(Color(0xFF1C1F26), Color(0xFF0A0B0E)))
private val CardColor = Color(0xFF2A2E37)
private val Shape = RoundedCornerShape(16.dp)

@Composable
fun LauncherScreen(
    apps: List<AppEntry>,
    favorites: List<String>,
    weather: String,
    onOpen: (AppEntry) -> Unit,
    onMenu: (AppEntry) -> Unit,
    onSettings: () -> Unit,
    onMeteo: () -> Unit,
    onWeatherMenu: () -> Unit,
) {
    val byPkg = apps.associateBy { it.pkg }
    val favApps = favorites.mapNotNull { byPkg[it] }
    val others = apps.filter { it.pkg !in favorites }
    val firstFocus = remember { FocusRequester() }

    Box(Modifier.fillMaxSize().background(Background)) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(6),
            contentPadding = PaddingValues(start = 56.dp, end = 56.dp, top = 28.dp, bottom = 56.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                TopBar(weather = weather, onWeather = onMeteo, onWeatherMenu = onWeatherMenu)
            }

            if (favApps.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(32.dp),
                        contentPadding = PaddingValues(vertical = 28.dp, horizontal = 12.dp),
                    ) {
                        itemsIndexed(favApps, key = { _, a -> a.pkg }) { index, app ->
                            AppCard(
                                app = app,
                                width = 320.dp,
                                modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                                onClick = { onOpen(app) },
                                onLongClick = { onMenu(app) },
                            )
                        }
                    }
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle("Alle Apps") }

            items(others, key = { it.pkg }) { app ->
                AppCard(app = app, width = null, onClick = { onOpen(app) }, onLongClick = { onMenu(app) })
            }

            item(key = "limeteo") {
                FocusCard(width = null, label = "LiMeteo", onClick = onMeteo, onLongClick = onWeatherMenu) {
                    BasicText("☀", style = TextStyle(color = Color(0xFFFAC775), fontSize = 40.sp))
                }
            }

            item(key = "settings") {
                FocusCard(width = null, label = "Einstellungen", onClick = onSettings, onLongClick = onSettings) {
                    BasicText("⚙", style = TextStyle(color = Color.White, fontSize = 40.sp))
                }
            }
        }
    }

    LaunchedEffect(favApps.isNotEmpty()) {
        if (favApps.isNotEmpty()) {
            delay(150)
            runCatching { firstFocus.requestFocus() }
        }
    }
}

@Composable
private fun TopBar(weather: String, onWeather: () -> Unit, onWeatherMenu: () -> Unit) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(10_000)
        }
    }
    val time = SimpleDateFormat("HH:mm", Locale.GERMANY).format(now)
    val date = SimpleDateFormat("EEEE, d. MMMM", Locale.GERMANY).format(now)

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        BasicText(date, style = TextStyle(color = Color(0x99FFFFFF), fontSize = 20.sp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (weather.isNotEmpty()) {
                WeatherChip(weather, onWeather, onWeatherMenu)
                Spacer(Modifier.width(24.dp))
            }
            BasicText(time, style = TextStyle(color = Color.White, fontSize = 42.sp, fontWeight = FontWeight.Light))
        }
    }
}

/** OK = open LiMeteo, long OK = change place. */
@Composable
private fun WeatherChip(text: String, onClick: () -> Unit, onLongClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    var longFired by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)
    BasicText(
        text,
        modifier = Modifier
            .clip(shape)
            .background(if (focused) Color(0x33FFFFFF) else Color.Transparent)
            .border(if (focused) 2.dp else 0.dp, if (focused) Color.White else Color.Transparent, shape)
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { ev ->
                val select = ev.key == Key.DirectionCenter || ev.key == Key.Enter || ev.key == Key.NumPadEnter
                if (!select) return@onKeyEvent false
                when (ev.type) {
                    KeyEventType.KeyDown -> {
                        val repeat = ev.nativeKeyEvent.repeatCount
                        if (repeat == 0) longFired = false
                        if (repeat == 1 && !longFired) { longFired = true; onLongClick() }
                        true
                    }
                    KeyEventType.KeyUp -> { if (!longFired) onClick(); longFired = false; true }
                    else -> false
                }
            }
            .focusable(),
        style = TextStyle(color = Color.White, fontSize = 24.sp),
    )
}

@Composable
private fun SectionTitle(text: String) {
    BasicText(
        text,
        modifier = Modifier.padding(start = 12.dp, top = 4.dp),
        style = TextStyle(color = Color(0x99FFFFFF), fontSize = 18.sp, fontWeight = FontWeight.Medium),
    )
}

@Composable
private fun AppCard(
    app: AppEntry,
    width: Dp?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    FocusCard(width = width, label = app.label, modifier = modifier, onClick = onClick, onLongClick = onLongClick) {
        if (app.isBanner) {
            Image(app.icon, app.label, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Image(app.icon, app.label, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(0.5f))
        }
    }
}

@Composable
private fun FocusCard(
    width: Dp?,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    var longFired by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.1f else 1f, label = "scale")

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.zIndex(if (focused) 1f else 0f),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
                .aspectRatio(16f / 9f)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .shadow(if (focused) 28.dp else 2.dp, Shape)
                .clip(Shape)
                .background(CardColor)
                .border(if (focused) 3.dp else 0.dp, if (focused) Color.White else Color.Transparent, Shape)
                .onFocusChanged { focused = it.isFocused }
                .onKeyEvent { ev ->
                    val select = ev.key == Key.DirectionCenter || ev.key == Key.Enter || ev.key == Key.NumPadEnter
                    if (!select) return@onKeyEvent false
                    when (ev.type) {
                        KeyEventType.KeyDown -> {
                            val repeat = ev.nativeKeyEvent.repeatCount
                            if (repeat == 0) longFired = false
                            if (repeat == 1 && !longFired) {
                                longFired = true
                                onLongClick()
                            }
                            true
                        }
                        KeyEventType.KeyUp -> {
                            if (!longFired) onClick()
                            longFired = false
                            true
                        }
                        else -> false
                    }
                }
                .focusable(),
        ) {
            content()
        }

        Spacer(Modifier.height(10.dp))
        BasicText(
            label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = (if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
                .graphicsLayer { alpha = if (focused) 1f else 0f },
            style = TextStyle(color = Color.White, fontSize = 16.sp, textAlign = TextAlign.Center),
        )
    }
}
