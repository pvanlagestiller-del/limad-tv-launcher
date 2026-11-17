package de.limad.tvlauncher

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

// ---------------------------------------------------------------- State & actions

class UiState {
    var tab by mutableStateOf(Tab.START)
    var detail by mutableStateOf<Title?>(null)
    var settingsOpen by mutableStateOf(false)
    var settingsSection by mutableIntStateOf(0)
    var apps by mutableStateOf<List<AppEntry>>(emptyList())
    var favorites by mutableStateOf<List<AppEntry>>(emptyList())
    /** Installed apps that TMDB knows as a streaming service, with provider id. */
    var services by mutableStateOf<List<Pair<AppEntry, Int>>>(emptyList())
    var weather by mutableStateOf("")
    var query by mutableStateOf<String?>(null)
    var paletteIndex by mutableIntStateOf(0)
    var watchTick by mutableIntStateOf(0)
    var settingsTick by mutableIntStateOf(0)
}

data class SettingItem(val id: String, val label: String, val value: String = "", val chevron: Boolean = true)

interface Actions {
    val ctx: Context
    fun openApp(app: AppEntry)
    fun appMenu(app: AppEntry)
    fun openStore()
    fun openMeteo()
    fun openDetail(t: Title)
    fun play(t: Title, providerIds: List<Int>)
    fun toggleWatch(t: Title): Boolean
    fun inWatch(t: Title): Boolean
    fun toggleLike(t: Title): Boolean
    fun liked(t: Title): Boolean
    fun watchlist(): List<Title>
    fun askSearch()
    fun bell()
    fun openSettings(section: Int)
    fun settingRows(section: Int): List<SettingItem>
    fun setting(id: String)
    fun selectTab(t: Tab)
    fun back()
    fun version(): String
}

private const val SIDE = 40

// ---------------------------------------------------------------- Root

@Composable
fun LimadRoot(s: UiState, a: Actions) {
    val pal = Palettes[s.paletteIndex]
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(pal.top, pal.bottom)))
            .drawBehind {
                drawCircle(Brush.radialGradient(listOf(pal.glow1, Color.Transparent), Offset(size.width * 0.8f, 0f), size.width * 0.6f), size.width * 0.6f, Offset(size.width * 0.8f, 0f))
                drawCircle(Brush.radialGradient(listOf(pal.glow2, Color.Transparent), Offset(0f, size.height), size.width * 0.5f), size.width * 0.5f, Offset(0f, size.height))
            }
    ) {
        val detail = s.detail
        when {
            s.settingsOpen -> SettingsScreen(s, a)
            detail != null -> DetailScreen(detail, s, a)
            s.tab == Tab.START -> StartScreen(s, a)
            s.tab == Tab.FILME -> BrowseScreen("movie", s, a)
            s.tab == Tab.SERIEN -> BrowseScreen("tv", s, a)
            s.tab == Tab.APPS -> AppsScreen(s, a)
            else -> SearchScreen(s, a)
        }
    }
    BackHandler(enabled = s.settingsOpen || s.detail != null || s.tab != Tab.START) { a.back() }
}

@Composable
private fun TabBar(s: UiState, a: Actions, tabFocus: FocusRequester? = null) {
    TopBar(
        tab = s.tab,
        onTab = a::selectTab,
        onBell = a::bell,
        onAvatar = { a.openSettings(4) },
        onBack = null,
        tabModifier = { t -> if (t == s.tab && tabFocus != null) Modifier.focusRequester(tabFocus) else Modifier },
    )
}

// ---------------------------------------------------------------- Hero

@Composable
private fun HeroInfo(
    hero: Title?,
    logo: String?,
    tmdb: Boolean,
    onPlay: () -> Unit,
    onInfo: () -> Unit,
    onFocus: (Boolean) -> Unit,
    playFocus: FocusRequester?,
    modifier: Modifier,
) {
    Box(modifier) {
        AnimatedContent(
            targetState = hero to logo,
            transitionSpec = { fadeIn(tween(600)) togetherWith fadeOut(tween(300)) },
            label = "hero",
            modifier = Modifier.align(Alignment.BottomStart).padding(start = SIDE.dp, bottom = 22.dp),
        ) { (h, lg) ->
            Column(Modifier.widthIn(max = 560.dp)) {
                if (h == null) {
                    BasicText("Willkommen bei", style = ts(30, FontWeight.Bold))
                    BasicText("LiMaD-TV", style = ts(56, FontWeight.Black))
                    Spacer(Modifier.height(8.dp))
                    BasicText(
                        if (tmdb) "Titelbilder werden geladen …" else "Deine Apps an einem Ort. Titelbilder: Einstellungen → Apps → TMDB",
                        style = ts(17, color = Color(0xE6FFFFFF)),
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Badge(if (h.type == "tv") "SERIE" else "FILM")
                        h.service?.let { BasicText(it, style = ts(14, FontWeight.SemiBold, C.BlueLight)) }
                    }
                    Spacer(Modifier.height(10.dp))
                    if (lg != null) {
                        AsyncImage(lg, h.title, contentScale = ContentScale.Fit, alignment = Alignment.BottomStart, modifier = Modifier.heightIn(max = 110.dp).widthIn(max = 440.dp))
                    } else {
                        BasicText(h.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = ts(46, FontWeight.Black).copy(lineHeight = 50.sp))
                    }
                    Spacer(Modifier.height(10.dp))
                    BasicText(
                        h.overview.ifBlank { listOf(h.year, rating(h.rating)).filter { it.isNotBlank() }.joinToString(" · ") },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = ts(16, color = Color(0xE6FFFFFF)).copy(lineHeight = 22.sp),
                    )
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        PillButton("Abspielen", Ic.PLAY, true, onPlay, if (playFocus != null) Modifier.focusRequester(playFocus) else Modifier, onFocus)
                        PillButton("Mehr Infos", Ic.INFO, false, onInfo, onFocus = onFocus)
                    }
                }
            }
        }
    }
}

private fun rating(r: Double) = if (r > 0) "★ " + String.format(java.util.Locale.GERMANY, "%.1f", r) else ""

@Composable
private fun rememberHeroLogo(a: Actions, hero: Title?): String? {
    val logo by produceState<String?>(null, hero?.key) { value = hero?.let { Tmdb.logo(a.ctx, it) } }
    return logo
}

/** Rotates through [items] every 10 s, paused while the hero buttons have focus. */
@Composable
private fun rememberRotation(items: List<Title>, paused: Boolean): Int {
    var idx by remember(items) { mutableIntStateOf(0) }
    LaunchedEffect(items, paused) {
        while (items.size > 1 && !paused) {
            delay(10_000)
            idx = (idx + 1) % items.size
        }
    }
    return idx
}

@Composable
private fun Dots(count: Int, index: Int) {
    if (count < 2) return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(count) { i ->
            Box(Modifier.size(if (i == index) 8.dp else 6.dp).clip(CircleShape).background(if (i == index) Color.White else Color(0x66FFFFFF)))
        }
    }
}

// ---------------------------------------------------------------- Start

@Composable
fun StartScreen(s: UiState, a: Actions) {
    val heroes by produceState(emptyList<Title>(), s.services) {
        value = Tmdb.interleave(*s.services.take(5).map { (app, id) -> Tmdb.popularOn(a.ctx, id, app.label).take(3) }.toTypedArray()).take(6)
    }
    var heroFocus by remember { mutableStateOf(false) }
    val idx = rememberRotation(heroes, heroFocus)
    val hero = heroes.getOrNull(idx)
    val logo = rememberHeroLogo(a, hero)
    val dockFocus = remember { FocusRequester() }
    val watch = remember(s.watchTick) { a.watchlist() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        Backdrop(hero?.backdropUrl("w1280"), Modifier.fillMaxWidth().height(maxHeight * 0.8f), Palettes[s.paletteIndex].bottom)
        Column(Modifier.fillMaxSize()) {
            TabBar(s, a)
            LazyColumn(contentPadding = PaddingValues(bottom = 56.dp), modifier = Modifier.fillMaxSize()) {
                item(key = "hero") {
                    HeroInfo(
                        hero, logo, Tmdb.hasToken(a.ctx),
                        onPlay = { hero?.let { a.play(it, emptyList()) } },
                        onInfo = { hero?.let(a::openDetail) },
                        onFocus = { heroFocus = it },
                        playFocus = null,
                        modifier = Modifier.fillMaxWidth().height(this@BoxWithConstraints.maxHeight * 0.44f),
                    )
                }
                item(key = "dock") {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
                            modifier = Modifier
                                .padding(horizontal = (SIDE - 14).dp)
                                .fillMaxWidth()
                                .glass(RoundedCornerShape(26.dp)),
                        ) {
                            items(s.favorites, key = { it.pkg }) { app ->
                                AppTile(
                                    app, 136.dp, { a.openApp(app) }, { a.appMenu(app) },
                                    modifier = if (app == s.favorites.first()) Modifier.focusRequester(dockFocus) else Modifier,
                                )
                            }
                            item(key = "plus") {
                                ActionTile(
                                    "Apps", Ic.PLUS, null, Color(0xFF3A4566), 136.dp, { a.selectTab(Tab.APPS) },
                                    showLabel = false,
                                    modifier = if (s.favorites.isEmpty()) Modifier.focusRequester(dockFocus) else Modifier,
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Dots(heroes.size, idx)
                    }
                }
                if (watch.isNotEmpty()) item(key = "watch") { Shelf("Meine Merkliste", watch, a::openDetail) }
                s.services.take(6).forEach { (app, id) ->
                    item(key = "svc$id") {
                        val list by produceState(emptyList<Title>(), id) { value = Tmdb.popularOn(a.ctx, id, app.label) }
                        Shelf("Beliebt auf ${app.label}", list, a::openDetail)
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        delay(250)
        runCatching { dockFocus.requestFocus() }
    }
}

// ---------------------------------------------------------------- Filme / Serien

@Composable
fun BrowseScreen(type: String, s: UiState, a: Actions) {
    val ids = s.services.map { it.second }
    val heroes by produceState(emptyList<Title>(), type, ids) {
        value = Tmdb.discover(a.ctx, type, ids).map { t -> t.copy(service = serviceOf(t, s)) }.take(6)
    }
    var heroFocus by remember { mutableStateOf(false) }
    val idx = rememberRotation(heroes, heroFocus)
    val hero = heroes.getOrNull(idx)
    val logo = rememberHeroLogo(a, hero)
    val playFocus = remember { FocusRequester() }
    val tabFocus = remember { FocusRequester() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        Backdrop(hero?.backdropUrl("w1280"), Modifier.fillMaxWidth().height(maxHeight * 0.8f), Palettes[s.paletteIndex].bottom)
        Column(Modifier.fillMaxSize()) {
            TabBar(s, a, tabFocus)
            LazyColumn(contentPadding = PaddingValues(bottom = 56.dp), modifier = Modifier.fillMaxSize()) {
                item(key = "hero") {
                    HeroInfo(
                        hero, logo, Tmdb.hasToken(a.ctx),
                        onPlay = { hero?.let { a.play(it, emptyList()) } },
                        onInfo = { hero?.let(a::openDetail) },
                        onFocus = { heroFocus = it },
                        playFocus = playFocus,
                        modifier = Modifier.fillMaxWidth().height(this@BoxWithConstraints.maxHeight * 0.5f),
                    )
                }
                item(key = "dots") {
                    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) { Dots(heroes.size, idx) }
                }
                s.services.take(6).forEach { (app, id) ->
                    item(key = "svc$type$id") {
                        val list by produceState(emptyList<Title>(), type, id) { value = Tmdb.discover(a.ctx, type, listOf(id), service = app.label) }
                        Shelf("Beliebt auf ${app.label}", list, a::openDetail)
                    }
                }
                item(key = "top$type") {
                    val list by produceState(emptyList<Title>(), type, ids) { value = Tmdb.discover(a.ctx, type, ids, "vote_average.desc", 800) }
                    Shelf("Bestbewertet", list, a::openDetail)
                }
                item(key = "trend$type") {
                    val list by produceState(emptyList<Title>(), type) { value = Tmdb.trending(a.ctx, type) }
                    Shelf("Im Trend diese Woche", list, a::openDetail)
                }
            }
        }
    }
    LaunchedEffect(hero != null) {
        delay(200)
        runCatching { if (hero != null) playFocus.requestFocus() else tabFocus.requestFocus() }
    }
}

private fun serviceOf(t: Title, s: UiState): String? = t.service ?: s.services.firstOrNull()?.first?.label?.takeIf { s.services.size == 1 }

// ---------------------------------------------------------------- Apps

private sealed interface GridItem {
    data class App(val app: AppEntry) : GridItem
    data class Action(val label: String, val icon: Ic?, val symbol: String?, val color: Color, val onClick: () -> Unit) : GridItem
}

@Composable
fun AppsScreen(s: UiState, a: Actions) {
    val first = remember { FocusRequester() }
    val rest = s.apps.filter { app -> s.favorites.none { it.pkg == app.pkg } }
    val grid: List<GridItem> = (s.favorites + rest).map { GridItem.App(it) } + listOf(
        GridItem.Action("LiMeteo", null, "⛅", Color(0xFF2F7FD8), a::openMeteo),
        GridItem.Action("Einstellungen", Ic.GEAR, null, Color(0xFF4A5470)) { a.openSettings(0) },
        GridItem.Action("App hinzufügen", Ic.PLUS, null, Color(0xFF3A4566), a::openStore),
    )
    val cols = 6
    Column(Modifier.fillMaxSize()) {
        TabBar(s, a)
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val gap = 22.dp
            val w = (maxWidth - (SIDE * 2).dp - gap * (cols - 1)) / cols
            val rows = grid.chunked(cols)
            LazyColumn(
                contentPadding = PaddingValues(start = SIDE.dp, end = SIDE.dp, top = 12.dp, bottom = 56.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item { BasicText("Meine Apps", style = ts(26, FontWeight.Bold)) }
                items(rows.size) { r ->
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        rows[r].forEachIndexed { c, g ->
                            val m = if (r == 0 && c == 0) Modifier.focusRequester(first) else Modifier
                            when (g) {
                                is GridItem.App -> AppTile(g.app, w, { a.openApp(g.app) }, { a.appMenu(g.app) }, modifier = m)
                                is GridItem.Action -> ActionTile(g.label, g.icon, g.symbol, g.color, w, g.onClick, modifier = m)
                            }
                        }
                    }
                }
                item {
                    BasicText("OK gedrückt halten: Favorit, Reihenfolge, Ausblenden", style = ts(14, color = C.Muted))
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        delay(200)
        runCatching { first.requestFocus() }
    }
}

// ---------------------------------------------------------------- Suche

@Composable
fun SearchScreen(s: UiState, a: Actions) {
    val q = s.query
    val field = remember { FocusRequester() }
    val results by produceState(emptyList<Title>(), q) { value = if (q.isNullOrBlank()) emptyList() else Tmdb.search(a.ctx, q) }
    val apps = if (q.isNullOrBlank()) emptyList() else s.apps.filter { it.label.contains(q, ignoreCase = true) }

    Column(Modifier.fillMaxSize()) {
        TabBar(s, a)
        LazyColumn(contentPadding = PaddingValues(bottom = 56.dp), modifier = Modifier.fillMaxSize()) {
            item {
                SearchField(q, a::askSearch, Modifier.padding(horizontal = SIDE.dp, vertical = 16.dp).focusRequester(field))
            }
            if (apps.isNotEmpty()) {
                item {
                    Column {
                        BasicText("Apps", Modifier.padding(start = SIDE.dp, top = 6.dp), style = ts(20, FontWeight.Bold))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(horizontal = SIDE.dp, vertical = 16.dp)) {
                            items(apps, key = { it.pkg }) { app -> AppTile(app, 170.dp, { a.openApp(app) }, { a.appMenu(app) }) }
                        }
                    }
                }
            }
            item { Shelf("Filme", results.filter { it.type == "movie" }, a::openDetail) }
            item { Shelf("Serien", results.filter { it.type == "tv" }, a::openDetail) }
            if (!q.isNullOrBlank() && results.isEmpty() && apps.isEmpty()) {
                item { BasicText("Keine Treffer für „$q“", Modifier.padding(SIDE.dp), style = ts(17, color = C.Muted)) }
            }
        }
    }
    LaunchedEffect(Unit) {
        delay(200)
        runCatching { field.requestFocus() }
    }
}

@Composable
private fun SearchField(q: String?, onClick: () -> Unit, modifier: Modifier) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth(0.62f)
            .glow(focused, radius = 16.dp, corner = 28.dp)
            .clip(shape)
            .background(if (focused) Color(0x40FFFFFF) else C.Glass)
            .border(if (focused) 2.dp else 1.dp, if (focused) Color.White else C.GlassBorder, shape)
            .onFocusChanged { focused = it.isFocused }
            .okKeys(onClick)
            .focusable()
            .padding(horizontal = 22.dp, vertical = 14.dp),
    ) {
        Icon(Ic.SEARCH, 22.dp, C.Muted)
        Spacer(Modifier.width(14.dp))
        BasicText(q ?: "Filme, Serien und Apps suchen", style = ts(18, color = if (q != null) Color.White else C.Muted))
    }
}

// ---------------------------------------------------------------- Detail

@Composable
fun DetailScreen(t: Title, s: UiState, a: Actions) {
    val details by produceState<Details?>(null, t.key) { value = Tmdb.details(a.ctx, t) }
    val d = details
    val title = d?.title ?: t
    val playFocus = remember { FocusRequester() }
    var watch by remember(t.key, s.watchTick) { mutableStateOf(a.inWatch(t)) }
    var liked by remember(t.key) { mutableStateOf(a.liked(t)) }
    val available = d?.providerIds?.mapNotNull { id -> s.services.firstOrNull { it.second == id }?.first?.label }?.distinct() ?: emptyList()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        Backdrop(title.backdropUrl("w1280"), Modifier.fillMaxSize(), Palettes[s.paletteIndex].bottom)
        Column(Modifier.fillMaxSize()) {
            TopBar(tab = null, onTab = {}, onBell = null, onAvatar = { a.openSettings(4) }, onBack = a::back)
            LazyColumn(contentPadding = PaddingValues(bottom = 40.dp), modifier = Modifier.fillMaxSize()) {
                item(key = "info") {
                    Box(Modifier.fillMaxWidth().height(this@BoxWithConstraints.maxHeight * 0.6f)) {
                        Column(Modifier.align(Alignment.BottomStart).padding(start = SIDE.dp, bottom = 10.dp).widthIn(max = 600.dp)) {
                            if (d?.logo != null) {
                                AsyncImage(d.logo, title.title, contentScale = ContentScale.Fit, alignment = Alignment.BottomStart, modifier = Modifier.heightIn(max = 130.dp).widthIn(max = 480.dp))
                            } else {
                                BasicText(title.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = ts(50, FontWeight.Black).copy(lineHeight = 54.sp))
                            }
                            Spacer(Modifier.height(14.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val meta = listOfNotNull(
                                    title.year.ifBlank { null },
                                    d?.genres?.take(2)?.joinToString(", ")?.ifBlank { null },
                                    d?.runtime?.ifBlank { null },
                                ).joinToString(" · ")
                                BasicText(meta, style = ts(15, FontWeight.Medium, Color(0xE6FFFFFF)))
                                d?.certification?.let { Badge(it) }
                                if (title.rating > 0) Badge(rating(title.rating))
                            }
                            Spacer(Modifier.height(10.dp))
                            BasicText(title.overview, maxLines = 3, overflow = TextOverflow.Ellipsis, style = ts(16, color = Color(0xE6FFFFFF)).copy(lineHeight = 22.sp))
                            if (available.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                BasicText("Verfügbar bei " + available.joinToString(", "), style = ts(14, FontWeight.SemiBold, C.BlueLight))
                            }
                            Spacer(Modifier.height(18.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                PillButton("Abspielen", Ic.PLAY, true, { a.play(title, d?.providerIds ?: emptyList()) }, Modifier.focusRequester(playFocus))
                                PillButton(if (watch) "In Merkliste" else "Merkliste", if (watch) Ic.CHECK else Ic.PLUS, false, { watch = a.toggleWatch(title) })
                                PillButton(if (liked) "Bewertet" else "Bewerten", if (liked) Ic.STAR_FILLED else Ic.STAR, false, { liked = a.toggleLike(title) })
                            }
                        }
                    }
                }
                item(key = "similar") {
                    Shelf("Ähnliche Inhalte", d?.similar ?: emptyList(), a::openDetail)
                }
            }
        }
    }
    LaunchedEffect(t.key) {
        delay(200)
        runCatching { playFocus.requestFocus() }
    }
}

// ---------------------------------------------------------------- Settings

private val SECTIONS = listOf(
    Ic.GEAR to "Allgemein",
    Ic.MONITOR to "Bild & Ton",
    Ic.WIFI to "Netzwerk",
    Ic.GRID to "Apps",
    Ic.PERSON to "Benutzer & Profile",
    Ic.SYSTEM to "System",
)

@Composable
fun SettingsScreen(s: UiState, a: Actions) {
    val menuFocus = remember { FocusRequester() }
    val rows = remember(s.settingsSection, s.settingsTick, s.paletteIndex, s.weather) { a.settingRows(s.settingsSection) }

    Column(Modifier.fillMaxSize()) {
        TopBar(tab = null, onTab = {}, onBell = null, onAvatar = { s.settingsSection = 4 }, onBack = a::back)
        BasicText("Einstellungen", Modifier.padding(start = (SIDE + 4).dp, top = 4.dp, bottom = 18.dp), style = ts(34, FontWeight.Bold))
        Row(Modifier.fillMaxSize().padding(start = SIDE.dp, end = SIDE.dp, bottom = 32.dp)) {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.width(290.dp).fillMaxHeight().glass(RoundedCornerShape(22.dp)).padding(12.dp),
            ) {
                SECTIONS.forEachIndexed { i, (icon, label) ->
                    MenuItem(icon, label, i == s.settingsSection, if (i == s.settingsSection) Modifier.focusRequester(menuFocus) else Modifier) {
                        s.settingsSection = i
                    }
                }
            }
            Spacer(Modifier.width(28.dp))
            Column(Modifier.fillMaxSize()) {
                SettingsHeader(a.version())
                Spacer(Modifier.height(18.dp))
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().glass(RoundedCornerShape(20.dp)),
                    contentPadding = PaddingValues(vertical = 6.dp),
                ) {
                    items(rows, key = { it.id + it.label }) { r ->
                        SettingRowView(r) { a.setting(r.id) }
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        delay(200)
        runCatching { menuFocus.requestFocus() }
    }
}

@Composable
private fun MenuItem(icon: Ic, label: String, selected: Boolean, modifier: Modifier, onSelect: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .glow(focused, radius = 14.dp, corner = 14.dp)
            .clip(shape)
            .background(
                when {
                    focused -> Brush.verticalGradient(listOf(Color(0xFFDCEBFF), Color(0xFFB7D4FF)))
                    selected -> Brush.verticalGradient(listOf(Color(0x40FFFFFF), Color(0x26FFFFFF)))
                    else -> Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
                }
            )
            .border(1.dp, if (focused) Color.White else Color.Transparent, shape)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onSelect()
            }
            .okKeys(onSelect)
            .focusable()
            .padding(horizontal = 16.dp, vertical = 13.dp),
    ) {
        Icon(icon, 22.dp, if (focused) C.Ink else Color.White)
        Spacer(Modifier.width(16.dp))
        BasicText(label, style = ts(17, if (selected || focused) FontWeight.SemiBold else FontWeight.Normal, if (focused) C.Ink else Color.White, shadow = !focused))
    }
}

@Composable
private fun SettingsHeader(version: String) {
    val shape = RoundedCornerShape(22.dp)
    Box(Modifier.fillMaxWidth().height(150.dp).clip(shape).border(1.dp, C.GlassBorder, shape)) {
        Image(painterResource(R.drawable.hero_default), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color(0xE60A1A45), 0.6f to Color(0x330A1A45))))
        Column(Modifier.align(Alignment.CenterStart).padding(start = 32.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LogoMark(40.dp)
                Spacer(Modifier.width(12.dp))
                BasicText("LiMaD-TV", style = ts(36, FontWeight.Bold))
            }
            Spacer(Modifier.height(4.dp))
            BasicText("Version $version", Modifier.padding(start = 52.dp), style = ts(16, color = Color(0xE6FFFFFF)))
        }
    }
}

@Composable
private fun SettingRowView(r: SettingItem, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(shape)
            .background(if (focused) Color.White else Color.Transparent)
            .onFocusChanged { focused = it.isFocused }
            .okKeys(onClick)
            .focusable()
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        val ink = if (focused) C.Ink else Color.White
        BasicText(r.label, Modifier.weight(1f), style = ts(17, FontWeight.Medium, ink, shadow = !focused))
        if (r.value.isNotEmpty()) BasicText(r.value, style = ts(16, color = if (focused) Color(0xFF3A4566) else C.Muted, shadow = !focused))
        if (r.chevron) {
            Spacer(Modifier.width(12.dp))
            Icon(Ic.CHEVRON, 16.dp, ink)
        }
    }
}

