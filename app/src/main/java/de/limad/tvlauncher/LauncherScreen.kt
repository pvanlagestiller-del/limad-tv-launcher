package de.limad.tvlauncher

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------------------------------------------------------------- Theme

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
    Font(R.font.inter_black, FontWeight.Black),
)

private val Navy0 = Color(0xFF050914)
private val Navy1 = Color(0xFF0A1330)
private val Navy2 = Color(0xFF12204A)
private val Blue = Color(0xFF2F7BFF)
private val BlueLight = Color(0xFF6FB1FF)
private val BlueDeep = Color(0xFF1546C8)
private val Muted = Color(0xFFA9B4CF)
private val Soft = Shadow(Color(0x99000000), blurRadius = 14f)

private fun text(size: Int, weight: FontWeight = FontWeight.Normal, color: Color = Color.White) =
    TextStyle(fontFamily = Inter, fontSize = size.sp, fontWeight = weight, color = color, shadow = Soft)

/** Fake bloom: concentric translucent rounded rects outside the element. */
private fun Modifier.glow(enabled: Boolean, color: Color = Blue, radius: Dp = 22.dp, corner: Dp = 18.dp) =
    drawBehind {
        if (!enabled) return@drawBehind
        val steps = 12
        for (i in steps downTo 1) {
            val spread = radius.toPx() * i / steps
            drawRoundRect(
                color = color.copy(alpha = 0.045f),
                topLeft = Offset(-spread, -spread),
                size = Size(size.width + spread * 2, size.height + spread * 2),
                cornerRadius = CornerRadius(corner.toPx() + spread),
            )
        }
    }

/** OK = click, holding OK = long click. */
private fun Modifier.select(onClick: () -> Unit, onLong: () -> Unit, state: BooleanArray) = onKeyEvent { ev ->
    val ok = ev.key == Key.DirectionCenter || ev.key == Key.Enter || ev.key == Key.NumPadEnter
    if (!ok) return@onKeyEvent false
    when (ev.type) {
        KeyEventType.KeyDown -> {
            val r = ev.nativeKeyEvent.repeatCount
            if (r == 0) state[0] = false
            if (r == 1 && !state[0]) { state[0] = true; onLong() }
            true
        }
        KeyEventType.KeyUp -> { if (!state[0]) onClick(); state[0] = false; true }
        else -> false
    }
}

enum class Page(val label: String) { HOME("Home"), APPS("Apps"), SETTINGS("Einstellungen") }

private data class TileItem(
    val key: String,
    val label: String,
    val app: AppEntry?,
    val symbol: String = "",
    val color: Color,
    val onClick: () -> Unit,
    val onLongClick: () -> Unit,
)

// ---------------------------------------------------------------- Screen

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
    val ctx = LocalContext.current
    val byPkg = apps.associateBy { it.pkg }
    val favApps = favorites.mapNotNull { byPkg[it] }
    val meteo = TileItem("limeteo", "LiMeteo", null, "⛅", Color(0xFF2F7FD8), onMeteo, onWeatherMenu)
    val settings = TileItem("settings", "Einstellungen", null, "⚙", Color(0xFF55607A), onSettings, onSettings)
    val homeTiles = favApps.map { it.toTile(onOpen, onMenu) } + meteo + settings
    val allTiles = apps.map { it.toTile(onOpen, onMenu) } + meteo

    var page by remember { mutableStateOf(Page.HOME) }

    // Popular titles on the favourite services (TMDB); the first favourites with a match.
    val trending by produceState(initialValue = emptyList<Pair<String, HeroItem>>(), favApps.map { it.label }) {
        value = runCatching { Tmdb.mixed(ctx, favApps.map { it.label }.take(6)) }.getOrDefault(emptyList())
    }

    val firstNav = remember { FocusRequester() }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Navy1, Navy0)))
            .drawBehind {
                drawCircle(Brush.radialGradient(listOf(Color(0x332F7BFF), Color.Transparent), center = Offset(size.width * 0.85f, 0f), radius = size.width * 0.6f))
                drawCircle(Brush.radialGradient(listOf(Color(0x226A3CFF), Color.Transparent), center = Offset(0f, size.height), radius = size.width * 0.5f))
            }
    ) {
        val heroH = maxHeight * 0.46f
        val side = 32.dp

        Column(Modifier.fillMaxSize()) {
            TopBar(page, { page = it }, weather, onMeteo, onWeatherMenu, firstNav, Modifier.padding(horizontal = side, vertical = 18.dp))

            when (page) {
                Page.HOME -> LazyColumn(
                    contentPadding = PaddingValues(bottom = 48.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(key = "hero") {
                        HeroCarousel(
                            slides = trending.take(6),
                            byLabel = favApps.associateBy { it.label },
                            onOpen = onOpen,
                            modifier = Modifier.padding(horizontal = side).fillMaxWidth().height(heroH),
                        )
                    }
                    item(key = "apps") {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(horizontal = side, vertical = 26.dp),
                        ) {
                            items(homeTiles, key = { it.key }) { t -> AppTile(t, 112.dp) }
                        }
                    }
                    if (trending.isNotEmpty()) {
                        item(key = "trendTitle") {
                            BasicText("Jetzt beliebt", Modifier.padding(start = side, bottom = 4.dp), style = text(22, FontWeight.Bold))
                        }
                        item(key = "trend") {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(18.dp),
                                contentPadding = PaddingValues(horizontal = side, vertical = 14.dp),
                            ) {
                                items(trending, key = { "${it.first}|${it.second.backdrop}" }) { (label, h) ->
                                    PosterCard(h, label) { favApps.firstOrNull { it.label == label }?.let(onOpen) }
                                }
                            }
                        }
                    }
                }

                Page.APPS -> AppsGrid(allTiles, side)

                Page.SETTINGS -> SettingsPage(
                    side = side,
                    items = listOf(
                        TileItem("manage", "Apps verwalten", null, "☰", Color(0xFF3B4C7A), onManage, onManage),
                        TileItem("place", "Ort für Wetter", null, "⌖", Color(0xFF2F7FD8), onWeatherMenu, onWeatherMenu),
                        TileItem("fire", "Fire-TV-Einstellungen", null, "⚙", Color(0xFF55607A), onSettings, onSettings),
                    ),
                    tmdb = Tmdb.hasToken(ctx),
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        delay(200)
        runCatching { firstNav.requestFocus() }
    }
}

private fun AppEntry.toTile(onOpen: (AppEntry) -> Unit, onMenu: (AppEntry) -> Unit) =
    TileItem(pkg, label, this, color = Color(tint), onClick = { onOpen(this) }, onLongClick = { onMenu(this) })

// ---------------------------------------------------------------- Top bar

@Composable
private fun TopBar(
    page: Page,
    onPage: (Page) -> Unit,
    weather: String,
    onWeather: () -> Unit,
    onWeatherMenu: () -> Unit,
    firstNav: FocusRequester,
    modifier: Modifier,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
        PlayMark(Modifier.size(34.dp))
        Spacer(Modifier.width(12.dp))
        BasicText(
            buildAnnotatedString {
                append("LiMaD")
                withStyle(SpanStyle(color = BlueLight)) { append("-TV") }
            },
            style = text(24, FontWeight.Black),
        )
        Spacer(Modifier.width(48.dp))
        Page.entries.forEachIndexed { i, p ->
            NavPill(p.label, p == page, if (i == 0) Modifier.focusRequester(firstNav) else Modifier) { onPage(p) }
            Spacer(Modifier.width(8.dp))
        }
        Spacer(Modifier.weight(1f))
        if (weather.isNotEmpty()) {
            WeatherPill(weather, onWeather, onWeatherMenu)
            Spacer(Modifier.width(18.dp))
            Box(Modifier.width(1.dp).height(26.dp).background(Color(0x33FFFFFF)))
            Spacer(Modifier.width(18.dp))
        }
        Clock()
    }
}

@Composable
private fun NavPill(label: String, selected: Boolean, modifier: Modifier, onSelect: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)
    BasicText(
        label,
        modifier = modifier
            .glow(focused, radius = 14.dp, corner = 22.dp)
            .clip(shape)
            .background(
                when {
                    focused -> Brush.verticalGradient(listOf(BlueLight, Blue))
                    selected -> Brush.verticalGradient(listOf(Color(0xFF1E4FD6), Color(0xFF143A9E)))
                    else -> Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
                }
            )
            .border(1.dp, if (selected || focused) Color(0x806FB1FF) else Color.Transparent, shape)
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onSelect() }
            .focusable()
            .padding(horizontal = 22.dp, vertical = 9.dp),
        style = text(18, if (selected || focused) FontWeight.Bold else FontWeight.Medium, if (selected || focused) Color.White else Muted),
    )
}

@Composable
private fun WeatherPill(label: String, onClick: () -> Unit, onLong: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val st = remember { BooleanArray(1) }
    val shape = RoundedCornerShape(50)
    BasicText(
        label,
        modifier = Modifier
            .glow(focused, radius = 12.dp, corner = 20.dp)
            .clip(shape)
            .background(if (focused) Blue else Color.Transparent)
            .onFocusChanged { focused = it.isFocused }
            .select(onClick, onLong, st)
            .focusable()
            .padding(horizontal = 14.dp, vertical = 7.dp),
        style = text(18, FontWeight.Medium),
    )
}

@Composable
private fun Clock() {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); delay(10_000) } }
    BasicText(SimpleDateFormat("HH:mm", Locale.GERMANY).format(now), style = text(22, FontWeight.SemiBold))
}

/** Glowing rounded play triangle (logo mark). */
@Composable
private fun PlayMark(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val path = Path().apply {
            moveTo(w * 0.22f, h * 0.12f)
            lineTo(w * 0.88f, h * 0.5f)
            lineTo(w * 0.22f, h * 0.88f)
            close()
        }
        for (i in 6 downTo 1) {
            drawPath(path, Blue.copy(alpha = 0.07f), style = Stroke(width = i * 3f, join = StrokeJoin.Round))
        }
        drawPath(path, Brush.linearGradient(listOf(BlueLight, BlueDeep), Offset(0f, 0f), Offset(w, h)))
        drawPath(path, Color(0xCCBFE0FF), style = Stroke(width = 2.5f, join = StrokeJoin.Round))
    }
}

// ---------------------------------------------------------------- Hero carousel

@Composable
private fun HeroCarousel(
    slides: List<Pair<String, HeroItem>>,
    byLabel: Map<String, AppEntry>,
    onOpen: (AppEntry) -> Unit,
    modifier: Modifier,
) {
    val total = slides.size + 1 // welcome slide first
    var index by remember(slides) { mutableIntStateOf(0) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(slides, focused) {
        while (total > 1 && !focused) {
            delay(9000)
            index = (index + 1) % total
        }
    }
    val shape = RoundedCornerShape(26.dp)
    val slide = if (index == 0) null else slides.getOrNull(index - 1)

    Box(
        modifier
            .glow(true, color = Color(0xFF2F7BFF), radius = 16.dp, corner = 26.dp)
            .clip(shape)
            .background(Navy2)
            .border(1.dp, Color(0x556FB1FF), shape)
    ) {
        AnimatedContent(
            targetState = slide,
            transitionSpec = { fadeIn(tween(800)) togetherWith fadeOut(tween(500)) },
            label = "hero",
        ) { s ->
            Box(Modifier.fillMaxSize()) {
                if (s == null) {
                    Image(painterResource(R.drawable.wallpaper), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    PlayMark(Modifier.align(Alignment.CenterEnd).padding(end = 90.dp).size(170.dp))
                } else {
                    AsyncImage(s.second.backdrop, null, contentScale = ContentScale.Crop, filterQuality = FilterQuality.High, modifier = Modifier.fillMaxSize())
                }
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color(0xE6081028), 0.6f to Color(0x00081028))))
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.6f to Color.Transparent, 1f to Color(0x99081028))))

                Column(Modifier.align(Alignment.CenterStart).padding(start = 48.dp).widthIn(max = 520.dp)) {
                    if (s == null) {
                        BasicText("Willkommen bei", style = text(40, FontWeight.Black))
                        BasicText(
                            buildAnnotatedString { append("LiMaD"); withStyle(SpanStyle(color = BlueLight)) { append("-TV") } },
                            style = text(54, FontWeight.Black),
                        )
                        Spacer(Modifier.height(8.dp))
                        BasicText("Deine Apps, aufgeräumt und ohne Werbung", style = text(19, color = Color(0xE6FFFFFF)))
                    } else {
                        val h = s.second
                        BasicText("JETZT BELIEBT AUF ${s.first.uppercase()}", style = text(13, FontWeight.Bold, BlueLight))
                        Spacer(Modifier.height(10.dp))
                        if (h.logo != null) {
                            AsyncImage(h.logo, h.title, contentScale = ContentScale.Fit, alignment = Alignment.CenterStart, modifier = Modifier.heightIn(max = 96.dp).widthIn(max = 420.dp))
                        } else {
                            BasicText(h.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = text(40, FontWeight.Black).copy(lineHeight = 44.sp))
                        }
                        if (h.overview.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))
                            BasicText(h.overview, maxLines = 2, overflow = TextOverflow.Ellipsis, style = text(15, color = Color(0xD9FFFFFF)).copy(lineHeight = 21.sp))
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    val app = s?.let { byLabel[it.first] }
                    HeroButton(
                        label = if (app != null) "In ${app.label} öffnen" else "Los geht's",
                        onFocus = { focused = it },
                        onClick = { app?.let(onOpen) },
                    )
                }

                // dots
                if (total > 1) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
                    ) {
                        repeat(total) { i ->
                            Box(
                                Modifier
                                    .size(width = if (i == index) 22.dp else 8.dp, height = 8.dp)
                                    .clip(CircleShape)
                                    .background(if (i == index) Color.White else Color(0x66FFFFFF))
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroButton(label: String, onFocus: (Boolean) -> Unit, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val st = remember { BooleanArray(1) }
    val shape = RoundedCornerShape(50)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .glow(focused, radius = 16.dp, corner = 26.dp)
            .clip(shape)
            .background(if (focused) Brush.verticalGradient(listOf(BlueLight, Blue)) else Brush.verticalGradient(listOf(Color(0x332F7BFF), Color(0x222F7BFF))))
            .border(1.dp, if (focused) Color(0xCCBFE0FF) else Color(0x806FB1FF), shape)
            .onFocusChanged { focused = it.isFocused; onFocus(it.isFocused) }
            .select(onClick, onClick, st)
            .focusable()
            .padding(horizontal = 24.dp, vertical = 11.dp),
    ) {
        BasicText("▶", style = text(16))
        Spacer(Modifier.width(12.dp))
        BasicText(label, style = text(17, FontWeight.SemiBold))
    }
}

// ---------------------------------------------------------------- Tiles

@Composable
private fun AppTile(t: TileItem, width: Dp, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val st = remember { BooleanArray(1) }
    val scale by animateFloatAsState(if (focused) 1.12f else 1f, tween(160), label = "s")
    val shape = RoundedCornerShape(20.dp)
    val base = t.color

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .width(width)
            .aspectRatio(0.92f)
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .glow(focused, radius = 22.dp, corner = 20.dp)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(lerp(base, Navy2, 0.45f), lerp(base, Navy0, 0.78f))))
            .border(if (focused) 2.dp else 1.dp, if (focused) BlueLight else Color(0x22FFFFFF), shape)
            .onFocusChanged { focused = it.isFocused }
            .select(t.onClick, t.onLongClick, st)
            .focusable(),
    ) {
        // top sheen
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(if (focused) 0x30FFFFFF else 0x14FFFFFF), 0.45f to Color.Transparent)))
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(10.dp)) {
            Box(Modifier.size(width * 0.46f), contentAlignment = Alignment.Center) {
                val app = t.app
                if (app != null) {
                    Image(app.squareIcon, app.label, contentScale = ContentScale.Fit, filterQuality = FilterQuality.High, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)))
                } else {
                    BasicText(t.symbol, style = text(36))
                }
            }
            Spacer(Modifier.height(10.dp))
            BasicText(t.label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = text(14, FontWeight.SemiBold).copy(textAlign = TextAlign.Center))
        }
    }
}

@Composable
private fun PosterCard(h: HeroItem, service: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val st = remember { BooleanArray(1) }
    val scale by animateFloatAsState(if (focused) 1.07f else 1f, tween(160), label = "s")
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier
            .width(250.dp)
            .aspectRatio(16f / 9f)
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .glow(focused, radius = 20.dp, corner = 16.dp)
            .clip(shape)
            .background(Navy2)
            .border(if (focused) 2.dp else 1.dp, if (focused) BlueLight else Color(0x1FFFFFFF), shape)
            .onFocusChanged { focused = it.isFocused }
            .select(onClick, onClick, st)
            .focusable()
    ) {
        AsyncImage(h.backdrop.replace("/w1280", "/w780"), h.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color(0xE6050914))))
        Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
            BasicText(h.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = text(16, FontWeight.Bold))
            BasicText(service, maxLines = 1, style = text(12, FontWeight.Medium, Muted))
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(if (focused) Blue else Color(0x332F7BFF)))
        }
    }
}

// ---------------------------------------------------------------- Apps & settings pages

@Composable
private fun AppsGrid(tiles: List<TileItem>, side: Dp) {
    val rows = tiles.chunked(7)
    LazyColumn(
        contentPadding = PaddingValues(start = side, end = side, top = 18.dp, bottom = 56.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(rows.size) { r ->
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                rows[r].forEach { AppTile(it, 118.dp) }
            }
        }
        item {
            BasicText(
                "OK lange drücken: Favorit, Reihenfolge, Ausblenden",
                Modifier.padding(top = 12.dp),
                style = text(15, color = Muted),
            )
        }
    }
}

@Composable
private fun SettingsPage(side: Dp, items: List<TileItem>, tmdb: Boolean) {
    Column(Modifier.padding(horizontal = side, vertical = 22.dp).fillMaxHeight()) {
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            items.forEach { AppTile(it, 150.dp) }
        }
        Spacer(Modifier.height(30.dp))
        BasicText(
            if (tmdb) "✔ Titelbilder (TMDB) aktiv" else "Titelbilder aus: TMDB-Token über „LiMaD TV Einrichtung“ senden",
            style = text(16, color = if (tmdb) BlueLight else Muted),
        )
        Spacer(Modifier.height(6.dp))
        BasicText("LiMaD-TV ${BuildConfigCompat.version}", style = text(14, color = Muted))
    }
}

/** Version name without enabling BuildConfig generation. */
private object BuildConfigCompat {
    val version: String
        get() = runCatching {
            val ctxClass = Class.forName("android.app.ActivityThread")
            val app = ctxClass.getMethod("currentApplication").invoke(null) as android.app.Application
            app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: ""
        }.getOrDefault("")
}
