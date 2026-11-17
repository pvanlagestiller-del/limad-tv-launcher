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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class Tab(val label: String) {
    START("Start"), ENTDECKEN("Entdecken"), FILME("Filme"), SERIEN("Serien"), LISTE("Meine Liste"), APPS("Apps"), SUCHE("Suche");

    companion object {
        /** Tabs shown in the segmented control (search has its own button). */
        val bar = listOf(START, ENTDECKEN, FILME, SERIEN, LISTE, APPS)
    }
}

// ---------------------------------------------------------------- Top bar

@Composable
fun TopBar(
    tab: Tab?,
    onTab: (Tab) -> Unit,
    onBell: (() -> Unit)?,
    onAvatar: () -> Unit,
    onBack: (() -> Unit)?,
    tabModifier: (Tab) -> Modifier = { Modifier },
    onSearch: (() -> Unit)? = null,
    onControl: (() -> Unit)? = null,
    wifi: String? = null,
    profile: Profile? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 40.dp, end = 40.dp, top = 22.dp, bottom = 10.dp),
    ) {
        if (onBack != null) {
            CircleButton(Ic.BACK, onBack)
            Spacer(Modifier.width(16.dp))
        }
        LogoMark(28.dp)
        Spacer(Modifier.width(10.dp))
        BasicText("LiMaD-TV", style = ts(22, FontWeight.Bold))
        Spacer(Modifier.weight(1f))
        if (tab != null) {
            Row(Modifier.glass(RoundedCornerShape(50)).padding(4.dp)) {
                Tab.bar.forEach { t -> Segment(t.label, t == tab, tabModifier(t)) { onTab(t) } }
            }
        }
        Spacer(Modifier.weight(1f))
        if (wifi != null) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(if (wifi.startsWith("Verbunden")) Color(0xFF4ADE80) else Color(0xFFF87171)))
            Spacer(Modifier.width(6.dp))
            BasicText("WLAN", style = ts(14, FontWeight.Medium, Color(0xE6FFFFFF)))
            Spacer(Modifier.width(16.dp))
        }
        if (onSearch != null) {
            CircleButton(Ic.SEARCH, onSearch, 40.dp)
            Spacer(Modifier.width(10.dp))
        }
        if (onControl != null) {
            CircleButton(Ic.SLIDERS, onControl, 40.dp)
            Spacer(Modifier.width(10.dp))
        }
        if (onBell != null) {
            CircleButton(Ic.BELL, onBell, 40.dp)
            Spacer(Modifier.width(10.dp))
        }
        Clock()
        Spacer(Modifier.width(14.dp))
        AvatarButton(profile, onAvatar)
    }
}

@Composable
private fun AvatarButton(profile: Profile?, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val color = profile?.let { Color(ProfileColors[it.color % ProfileColors.size]) } ?: C.Blue
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .glow(focused, radius = 14.dp, corner = 20.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(lerp(color, Color.White, 0.2f), lerp(color, Color.Black, 0.3f))))
            .border(if (focused) 2.dp else 1.dp, if (focused) Color.White else Color(0x55FFFFFF), CircleShape)
            .onFocusChanged { focused = it.isFocused }
            .okKeys(onClick)
            .focusable(),
    ) {
        if (profile != null) BasicText(profile.name.take(1).uppercase(), style = ts(17, FontWeight.Bold)) else Icon(Ic.PERSON, 20.dp)
    }
}

@Composable
private fun Segment(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)
    val white = selected || focused
    BasicText(
        label,
        modifier = modifier
            .glow(focused, radius = 14.dp, corner = 22.dp)
            .clip(shape)
            .background(
                when {
                    focused -> Color.White
                    selected -> Color(0xF2FFFFFF)
                    else -> Color.Transparent
                }
            )
            .onFocusChanged { focused = it.isFocused }
            .okKeys(onClick)
            .focusable()
            .padding(horizontal = 22.dp, vertical = 8.dp),
        style = ts(17, if (white) FontWeight.SemiBold else FontWeight.Medium, if (white) C.Ink else Color.White, shadow = !white),
    )
}

@Composable
fun CircleButton(icon: Ic, onClick: () -> Unit, size: Dp = 44.dp) {
    var focused by remember { mutableStateOf(false) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .glow(focused, radius = 14.dp, corner = size / 2)
            .clip(CircleShape)
            .background(if (focused) Color.White else C.Glass)
            .border(1.dp, if (focused) Color.White else C.GlassBorder, CircleShape)
            .onFocusChanged { focused = it.isFocused }
            .okKeys(onClick)
            .focusable(),
    ) {
        Icon(icon, size * 0.5f, if (focused) C.Ink else Color.White)
    }
}

@Composable
fun Clock() {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(10_000)
        }
    }
    BasicText(SimpleDateFormat("HH:mm", Locale.GERMANY).format(now), style = ts(20, FontWeight.SemiBold))
}

// ---------------------------------------------------------------- Buttons

@Composable
fun PillButton(
    label: String,
    icon: Ic?,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocus: (Boolean) -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, tween(150), label = "btn")
    val shape = RoundedCornerShape(50)
    val light = primary || focused
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .glow(focused, radius = 16.dp, corner = 24.dp)
            .clip(shape)
            .background(if (light) Color.White else C.GlassStrong)
            .border(1.dp, if (light) Color.White else C.GlassBorder, shape)
            .onFocusChanged { focused = it.isFocused; onFocus(it.isFocused) }
            .okKeys(onClick)
            .focusable()
            .padding(horizontal = 22.dp, vertical = 10.dp),
    ) {
        if (icon != null) {
            Icon(icon, 18.dp, if (light) C.Ink else Color.White)
            Spacer(Modifier.width(10.dp))
        }
        BasicText(label, style = ts(16, FontWeight.SemiBold, if (light) C.Ink else Color.White, shadow = !light))
    }
}

@Composable
fun Badge(text: String) {
    BasicText(
        text,
        modifier = Modifier
            .border(1.dp, Color(0x99FFFFFF), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp),
        style = ts(12, FontWeight.Bold, Color(0xE6FFFFFF)),
    )
}

// ---------------------------------------------------------------- Tiles

private val TileShape = RoundedCornerShape(16.dp)

/** App tile (16:9). Opaque banners fill the tile, transparent ones sit on the app colour, icon-only apps get a label. */
@Composable
fun AppTile(
    app: AppEntry,
    width: Dp,
    onClick: () -> Unit,
    onLong: () -> Unit,
    modifier: Modifier = Modifier,
    forceLabel: Boolean = false,
    onFocused: () -> Unit = {},
) {
    FocusTile(
        width = width,
        color = Color(app.tint),
        label = if (forceLabel || !app.isBanner) app.label else null,
        onClick = onClick,
        onLong = onLong,
        modifier = modifier,
        onFocused = onFocused,
    ) {
        when {
            app.isBanner && app.opaque -> Image(app.icon, app.label, contentScale = ContentScale.Crop, filterQuality = FilterQuality.High, modifier = Modifier.fillMaxSize())
            app.isBanner -> Image(
                app.icon, app.label,
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.High,
                modifier = Modifier.fillMaxSize().padding(horizontal = width * 0.1f, vertical = width * 0.06f),
            )
            else -> Image(app.squareIcon, app.label, contentScale = ContentScale.Fit, filterQuality = FilterQuality.High, modifier = Modifier.fillMaxSize(0.5f).clip(RoundedCornerShape(12.dp)))
        }
    }
}

/** Tile with an icon instead of an app (LiMeteo, App hinzufügen, …). */
@Composable
fun ActionTile(
    label: String,
    icon: Ic?,
    symbol: String?,
    color: Color,
    width: Dp,
    onClick: () -> Unit,
    showLabel: Boolean = true,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
) {
    FocusTile(width, color, if (showLabel) label else null, onClick, onClick, modifier, onFocused) {
        if (icon != null) Icon(icon, width * 0.2f) else BasicText(symbol ?: "", style = ts(34))
    }
}

@Composable
fun FocusTile(
    width: Dp,
    color: Color,
    label: String?,
    onClick: () -> Unit,
    onLong: () -> Unit,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.1f else 1f, tween(170), label = "tile")
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(width).zIndex(if (focused) 1f else 0f)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .glow(focused, radius = 22.dp, corner = 16.dp)
                .clip(TileShape)
                .background(Brush.verticalGradient(listOf(lerp(color, Color.White, 0.12f), lerp(color, Color.Black, 0.25f))))
                .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused() }
                .okKeys(onClick, onLong)
                .focusable(),
        ) {
            content()
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(if (focused) 0x33FFFFFF else 0x14FFFFFF), 0.5f to Color.Transparent)))
            Box(Modifier.fillMaxSize().border(if (focused) 3.dp else 1.dp, if (focused) Color.White else Color(0x22FFFFFF), TileShape))
        }
        if (label != null) {
            Spacer(Modifier.height(10.dp))
            BasicText(
                label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
                style = ts(15, FontWeight.Medium).copy(textAlign = TextAlign.Center),
            )
        }
    }
}

/** Film/series card: backdrop with the title set in heavy caps (like the mock's "UNCHARTED"). */
@Composable
fun PosterCard(t: Title, width: Dp, onClick: () -> Unit, onFocused: () -> Unit = {}) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, tween(170), label = "card")
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .width(width)
            .aspectRatio(16f / 9f)
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .glow(focused, radius = 22.dp, corner = 14.dp)
            .clip(shape)
            .background(C.Card)
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused() }
            .okKeys(onClick)
            .focusable()
    ) {
        AsyncImage(t.backdropUrl("w780"), t.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color(0xE6000000))))
        BasicText(
            t.title.uppercase(),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 14.dp, vertical = 12.dp),
            style = ts(17, FontWeight.Black).copy(lineHeight = 19.sp, letterSpacing = 0.3.sp),
        )
        Box(Modifier.fillMaxSize().border(if (focused) 3.dp else 1.dp, if (focused) Color.White else Color(0x1FFFFFFF), shape))
    }
}

/** Top-10 card: big outlined rank number over the backdrop. */
@Composable
fun RankCard(rank: Int, t: Title, width: Dp, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, tween(170), label = "rank")
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .width(width)
            .aspectRatio(4f / 3f)
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .glow(focused, radius = 20.dp, corner = 14.dp)
            .clip(shape)
            .background(C.Card)
            .onFocusChanged { focused = it.isFocused }
            .okKeys(onClick)
            .focusable()
    ) {
        AsyncImage(t.backdropUrl("w780"), t.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x99000000), 0.4f to Color.Transparent, 1f to Color(0xCC000000))))
        BasicText(
            "$rank",
            modifier = Modifier.padding(start = 10.dp, top = 2.dp),
            style = ts(54, FontWeight.Black).copy(lineHeight = 58.sp),
        )
        BasicText(
            t.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
            style = ts(14, FontWeight.Bold),
        )
        Box(Modifier.fillMaxSize().border(if (focused) 3.dp else 1.dp, if (focused) Color.White else Color(0x1FFFFFFF), shape))
    }
}

@Composable
fun Shelf(title: String, items: List<Title>, onOpen: (Title) -> Unit, side: Dp = 40.dp) {
    if (items.isEmpty()) return
    Column(Modifier.padding(top = 10.dp)) {
        BasicText(title, Modifier.padding(start = side), style = ts(20, FontWeight.Bold))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding = PaddingValues(horizontal = side, vertical = 16.dp),
        ) {
            itemsIndexed(items, key = { _, t -> t.key }) { _, t -> PosterCard(t, 230.dp, { onOpen(t) }) }
        }
    }
}

// ---------------------------------------------------------------- Backdrop

/** Full-width backdrop with the gradients that keep text readable. */
@Composable
fun Backdrop(url: String?, modifier: Modifier = Modifier, fade: Color = Color(0xFF050914)) {
    Box(modifier) {
        Crossfade(targetState = url, animationSpec = tween(900), label = "backdrop") { u ->
            if (u != null) {
                AsyncImage(u, null, contentScale = ContentScale.Crop, filterQuality = FilterQuality.High, modifier = Modifier.fillMaxSize())
            } else {
                Image(
                    androidx.compose.ui.res.painterResource(R.drawable.hero_default),
                    null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to fade.copy(alpha = 0.85f), 0.55f to fade.copy(alpha = 0f))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to fade.copy(alpha = 0.5f), 0.22f to Color.Transparent, 0.6f to Color.Transparent, 1f to fade)))
    }
}
