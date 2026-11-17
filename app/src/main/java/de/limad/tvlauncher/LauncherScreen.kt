package de.limad.tvlauncher

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Glass = Color(0x26FFFFFF)
private val GlassBorder = Color(0x40FFFFFF)
private val TileShape = RoundedCornerShape(18.dp)
private val TextShadow = Shadow(Color(0x99000000), blurRadius = 12f)

/** One tile in the dock or the grid. */
private data class TileItem(
    val key: String,
    val label: String,
    val app: AppEntry?,
    val symbol: String = "",
    val color: Color = Color(0xFF3A3F4B),
    val onClick: () -> Unit,
    val onLongClick: () -> Unit,
)

@Composable
fun LauncherScreen(
    apps: List<AppEntry>,
    favorites: List<String>,
    lastOpened: String?,
    weather: String,
    onOpen: (AppEntry) -> Unit,
    onMenu: (AppEntry) -> Unit,
    onSettings: () -> Unit,
    onMeteo: () -> Unit,
    onWeatherMenu: () -> Unit,
    onManage: () -> Unit,
) {
    val byPkg = apps.associateBy { it.pkg }
    val dock = favorites.mapNotNull { byPkg[it] }.map { it.toTile(onOpen, onMenu) }
    val grid = apps.filter { it.pkg !in favorites }.map { it.toTile(onOpen, onMenu) } + listOf(
        TileItem("limeteo", "LiMeteo", null, "☀", Color(0xFF2F7FD8), onMeteo, onWeatherMenu),
        TileItem("manage", "Apps verwalten", null, "☰", Color(0xFF4A4F5C), onManage, onManage),
        TileItem("settings", "Einstellungen", null, "⚙", Color(0xFF5A606E), onSettings, onSettings),
    )

    // Backdrop follows focus; starts with the last opened app.
    var focusedKey by remember(lastOpened) { mutableStateOf(lastOpened) }
    val focusedApp = focusedKey?.let { byPkg[it] }
    val title = focusedApp?.label ?: (dock + grid).firstOrNull { it.key == focusedKey }?.label ?: ""

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val firstFocus = remember { FocusRequester() }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF0B0E1C))) {
        val tileW = (maxWidth - 128.dp - 56.dp - 20.dp * 5) / 6
        val heroH = maxHeight * 0.52f

        // --- Backdrop ---------------------------------------------------------------
        Crossfade(targetState = focusedApp, animationSpec = tween(600), label = "backdrop") { app ->
            if (app != null) {
                Image(
                    bitmap = app.backdrop,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.High,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Image(
                    painter = painterResource(R.drawable.wallpaper),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        // Vignette so text and tiles stay readable on any artwork.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color(0x55000000),
                    0.45f to Color(0x22000000),
                    0.75f to Color(0xAA000000),
                    1f to Color(0xEE000000),
                )
            )
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(0f to Color(0x88000000), 0.5f to Color.Transparent)
            )
        )

        // --- Content ------------------------------------------------------------------
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 64.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "hero") {
                Box(Modifier.fillMaxWidth().height(heroH)) {
                    BasicText(
                        title,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 72.dp, end = 300.dp, bottom = 24.dp),
                        style = TextStyle(
                            color = Color.White,
                            fontSize = 54.sp,
                            fontWeight = FontWeight.Black,
                            lineHeight = 56.sp,
                            shadow = TextShadow,
                        ),
                    )
                }
            }

            item(key = "dock") {
                Box(
                    Modifier
                        .padding(horizontal = 64.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(30.dp))
                        .background(Glass)
                        .border(1.dp, GlassBorder, RoundedCornerShape(30.dp))
                        .padding(horizontal = 28.dp, vertical = 22.dp)
                ) {
                    if (dock.isEmpty()) {
                        BasicText(
                            "Lange OK auf eine App drücken → „Zu Favoriten hinzufügen“",
                            modifier = Modifier.padding(16.dp),
                            style = TextStyle(color = Color(0xCCFFFFFF), fontSize = 18.sp),
                        )
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        itemsIndexed(dock, key = { _, t -> t.key }) { i, t ->
                            Tile(
                                item = t,
                                width = tileW,
                                modifier = if (i == 0) Modifier.focusRequester(firstFocus) else Modifier,
                                onFocused = {
                                    focusedKey = t.key
                                    scope.launch { listState.animateScrollToItem(0) }
                                },
                            )
                        }
                    }
                }
            }

            val rows = grid.chunked(6)
            rows.forEachIndexed { r, row ->
                item(key = "row$r") {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        modifier = Modifier.padding(start = 92.dp, end = 92.dp, top = if (r == 0) 36.dp else 12.dp),
                    ) {
                        row.forEach { t ->
                            Tile(item = t, width = tileW, onFocused = { focusedKey = t.key })
                        }
                    }
                }
            }
        }

        // --- Clock / weather pill ---------------------------------------------------------
        ClockPill(
            weather = weather,
            onWeather = onMeteo,
            onWeatherMenu = onWeatherMenu,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 28.dp, end = 48.dp),
        )
    }

    LaunchedEffect(dock.isNotEmpty()) {
        if (dock.isNotEmpty()) {
            delay(150)
            runCatching { firstFocus.requestFocus() }
        }
    }
}

private fun AppEntry.toTile(onOpen: (AppEntry) -> Unit, onMenu: (AppEntry) -> Unit) = TileItem(
    key = pkg,
    label = label,
    app = this,
    color = Color(tint),
    onClick = { onOpen(this) },
    onLongClick = { onMenu(this) },
)

@Composable
private fun Tile(item: TileItem, width: Dp, modifier: Modifier = Modifier, onFocused: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    var longFired by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.12f else 1f, label = "scale")
    val base = item.color
    val bg = Brush.verticalGradient(listOf(lerp(base, Color.White, 0.12f), lerp(base, Color.Black, 0.35f)))

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(width).zIndex(if (focused) 1f else 0f),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    shadowElevation = if (focused) 36f else 6f
                    shape = TileShape
                    clip = true
                }
                .background(bg)
                .border(if (focused) 2.dp else 1.dp, if (focused) Color(0xCCFFFFFF) else Color(0x22FFFFFF), TileShape)
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onFocused()
                }
                .onKeyEvent { ev ->
                    val select = ev.key == Key.DirectionCenter || ev.key == Key.Enter || ev.key == Key.NumPadEnter
                    if (!select) return@onKeyEvent false
                    when (ev.type) {
                        KeyEventType.KeyDown -> {
                            val repeat = ev.nativeKeyEvent.repeatCount
                            if (repeat == 0) longFired = false
                            if (repeat == 1 && !longFired) {
                                longFired = true
                                item.onLongClick()
                            }
                            true
                        }
                        KeyEventType.KeyUp -> {
                            if (!longFired) item.onClick()
                            longFired = false
                            true
                        }
                        else -> false
                    }
                }
                .focusable(),
        ) {
            TileContent(item)
            // Glass highlight on the upper half when focused.
            if (focused) {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(0f to Color(0x40FFFFFF), 0.5f to Color.Transparent)
                    )
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        BasicText(
            item.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = if (focused) 1f else 0f },
            style = TextStyle(color = Color.White, fontSize = 15.sp, textAlign = TextAlign.Center, shadow = TextShadow),
        )
    }
}

@Composable
private fun BoxScope.TileContent(item: TileItem) {
    val app = item.app
    when {
        app != null && app.isBanner -> Image(
            app.icon, app.label,
            contentScale = ContentScale.Crop,
            filterQuality = FilterQuality.High,
            modifier = Modifier.fillMaxSize(),
        )
        app != null -> Image(
            app.icon, app.label,
            contentScale = ContentScale.Fit,
            filterQuality = FilterQuality.High,
            modifier = Modifier.fillMaxSize(0.55f),
        )
        else -> BasicText(item.symbol, style = TextStyle(color = Color.White, fontSize = 38.sp))
    }
}

@Composable
private fun ClockPill(weather: String, onWeather: () -> Unit, onWeatherMenu: () -> Unit, modifier: Modifier) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(10_000)
        }
    }
    val time = SimpleDateFormat("HH:mm", Locale.GERMANY).format(now)
    val pill = RoundedCornerShape(50)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(pill)
            .background(Glass)
            .border(1.dp, GlassBorder, pill)
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        if (weather.isNotEmpty()) WeatherChip(weather, onWeather, onWeatherMenu)
        BasicText(
            time,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            style = TextStyle(color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Medium),
        )
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
            .background(if (focused) Color.White else Color.Transparent)
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
        style = TextStyle(color = if (focused) Color.Black else Color.White, fontSize = 18.sp),
    )
}
