package de.limad.tvlauncher

import android.view.KeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Panel = RoundedCornerShape(26.dp)

// ---------------------------------------------------------------- Kontrollzentrum

@Composable
fun ControlCenter(s: UiState, a: Actions) {
    val first = remember { FocusRequester() }
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            a.refreshNowPlaying()
            delay(3_000)
        }
    }

    Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color(0xD9050914), 0.6f to Color(0x99050914)))) {
        Column(Modifier.align(Alignment.CenterStart).padding(start = 48.dp).widthIn(max = 380.dp)) {
            BasicText("LIMAD-TV · DEIN ENTERTAINMENT", style = ts(13, FontWeight.SemiBold, Color(0xCCFFFFFF)).copy(letterSpacing = 3.sp))
            Spacer(Modifier.height(12.dp))
            BasicText(
                buildAnnotatedString { append("Alles im "); withStyle(SpanStyle(color = C.BlueLight)) { append("Blick.") } },
                style = ts(46, FontWeight.Black),
            )
            Spacer(Modifier.height(10.dp))
            BasicText("Steuere Wiedergabe, Ton und System bequem mit der Fernbedienung.", style = ts(17, color = Color(0xE6FFFFFF)))
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 40.dp)
                .width(400.dp)
                .glass(Panel, strong = true)
                .padding(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText("Kontrollzentrum", Modifier.weight(1f), style = ts(20, FontWeight.Bold))
                BasicText(SimpleDateFormat("EE, d. MMMM · HH:mm", Locale.GERMANY).format(now), style = ts(13, color = C.Muted))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CcTile(Ic.POWER, "Standby", "Bildschirm ruhen", Modifier.weight(1f).focusRequester(first), a::standby)
                CcTile(Ic.WIFI, "WLAN", s.wifi, Modifier.weight(1f), a::openWifi)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CcTile(Ic.MOON, "Nicht stören", if (s.dnd) "An · kein Bildschirmschoner" else "Aus", Modifier.weight(1f), a::toggleDnd, active = s.dnd)
                CcTile(Ic.TIMER, "Sleep-Timer", if (s.sleepMinutes > 0) "${s.sleepMinutes} Minuten" else "Aus", Modifier.weight(1f), a::cycleSleep, active = s.sleepMinutes > 0)
            }
            CcRow(Ic.SPEAKER, "Audioausgabe", "Ton & Lautsprecher", a::openSound)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CcSmall(Ic.SEARCH, "Suche", Modifier.weight(1f)) { a.closeOverlays(); a.selectTab(Tab.SUCHE) }
                CcSmall(Ic.ACCESS, "Bedienung", Modifier.weight(1f), a::openAccessibility)
                CcSmall(Ic.HOME, "Start", Modifier.weight(1f)) { a.closeOverlays(); a.selectTab(Tab.START) }
                CcSmall(Ic.GEAR, "Optionen", Modifier.weight(1f)) { a.closeOverlays(); a.openSettings(0) }
            }
            NowPlayingCard(s.nowPlaying, a)
        }
    }
    LaunchedEffect(Unit) {
        delay(150)
        runCatching { first.requestFocus() }
    }
}

@Composable
private fun CcTile(icon: Ic, title: String, sub: String, modifier: Modifier, onClick: () -> Unit, active: Boolean = false) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .height(92.dp)
            .glow(focused, radius = 16.dp, corner = 18.dp)
            .clip(shape)
            .background(if (active) Color(0x592F7BFF) else C.Glass)
            .border(if (focused) 2.dp else 1.dp, if (focused) C.BlueLight else C.GlassBorder, shape)
            .onFocusChanged { focused = it.isFocused }
            .okKeys(onClick)
            .focusable()
            .padding(14.dp),
    ) {
        Icon(icon, 20.dp)
        Spacer(Modifier.weight(1f))
        BasicText(title, style = ts(15, FontWeight.Bold))
        BasicText(sub, maxLines = 1, overflow = TextOverflow.Ellipsis, style = ts(11, color = C.Muted))
    }
}

@Composable
private fun CcRow(icon: Ic, title: String, sub: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .glow(focused, radius = 14.dp, corner = 16.dp)
            .clip(shape)
            .background(C.Glass)
            .border(if (focused) 2.dp else 1.dp, if (focused) C.BlueLight else C.GlassBorder, shape)
            .onFocusChanged { focused = it.isFocused }
            .okKeys(onClick)
            .focusable()
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Icon(icon, 20.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            BasicText(title, style = ts(15, FontWeight.Bold))
            BasicText(sub, style = ts(11, color = C.Muted))
        }
        Icon(Ic.CHEVRON, 14.dp)
    }
}

@Composable
private fun CcSmall(icon: Ic, label: String, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .glow(focused, radius = 12.dp, corner = 14.dp)
            .clip(shape)
            .background(if (focused) Color.White else C.Glass)
            .border(1.dp, if (focused) Color.White else C.GlassBorder, shape)
            .onFocusChanged { focused = it.isFocused }
            .okKeys(onClick)
            .focusable()
            .padding(vertical = 10.dp),
    ) {
        Icon(icon, 20.dp, if (focused) C.Ink else Color.White)
        Spacer(Modifier.height(4.dp))
        BasicText(label, style = ts(11, FontWeight.Medium, if (focused) C.Ink else Color.White, shadow = !focused))
    }
}

@Composable
private fun NowPlayingCard(np: NowPlaying?, a: Actions) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clip(shape).background(C.Glass).border(1.dp, C.GlassBorder, shape).padding(12.dp),
    ) {
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)).background(Brush.linearGradient(listOf(Color(0xFFE040FB), Color(0xFF7C4DFF)))),
            contentAlignment = Alignment.Center,
        ) { BasicText("♪", style = ts(22)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            BasicText(
                (np?.app?.uppercase() ?: "MEDIEN") + if (np?.playing == true) " · JETZT SPIELT" else "",
                maxLines = 1,
                style = ts(10, FontWeight.SemiBold, C.Muted).copy(letterSpacing = 1.sp),
            )
            BasicText(np?.title ?: "Nichts wird abgespielt", maxLines = 1, overflow = TextOverflow.Ellipsis, style = ts(15, FontWeight.Bold))
            if (!np?.artist.isNullOrBlank()) BasicText(np!!.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = ts(11, color = C.Muted))
        }
        MiniButton(Ic.PREV) { a.media(KeyEvent.KEYCODE_MEDIA_PREVIOUS) }
        MiniButton(if (np?.playing == true) Ic.PAUSE else Ic.PLAY) { a.media(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) }
        MiniButton(Ic.NEXT) { a.media(KeyEvent.KEYCODE_MEDIA_NEXT) }
    }
}

@Composable
private fun MiniButton(icon: Ic, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (focused) Color.White else Color.Transparent)
            .onFocusChanged { focused = it.isFocused }
            .okKeys(onClick)
            .focusable(),
    ) { Icon(icon, 16.dp, if (focused) C.Ink else Color.White) }
}

// ---------------------------------------------------------------- Profile picker

@Composable
fun ProfilePicker(s: UiState, a: Actions) {
    val current = remember { FocusRequester() }
    Box(Modifier.fillMaxSize()) {
        Image(painterResource(R.drawable.bg_tropischer_wasserfall), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Color(0x8C050914)))
        Row(Modifier.padding(start = 40.dp, top = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            LogoMark(28.dp)
            Spacer(Modifier.width(10.dp))
            BasicText("LiMaD-TV", style = ts(22, FontWeight.Bold))
        }
        Box(Modifier.align(Alignment.TopEnd).padding(end = 40.dp, top = 24.dp)) { Clock() }

        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.glass(RoundedCornerShape(30.dp), strong = true).padding(horizontal = 56.dp, vertical = 30.dp),
            ) {
                BasicText("WILLKOMMEN ZURÜCK", style = ts(13, FontWeight.SemiBold, Color(0xCCFFFFFF)).copy(letterSpacing = 3.sp))
                Spacer(Modifier.height(8.dp))
                BasicText("Wer schaut heute?", style = ts(40, FontWeight.Black))
                Spacer(Modifier.height(6.dp))
                BasicText("Wähle ein Profil für deine Merkliste und Bewertungen.", style = ts(16, color = Color(0xE6FFFFFF)))
                Spacer(Modifier.height(26.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(30.dp)) {
                    s.profiles.forEach { p ->
                        val isCurrent = p.id == s.currentProfile.id
                        Avatar(
                            p.name, Color(ProfileColors[p.color % ProfileColors.size]),
                            if (isCurrent) "Aktuelles Profil" else "Profil auswählen",
                            if (isCurrent) Modifier.focusRequester(current) else Modifier,
                            onClick = { a.selectProfile(p) },
                            onLong = { a.profileMenu(p) },
                        )
                    }
                    Avatar("Hinzufügen", Color(0x33FFFFFF), "Neues Profil", Modifier, plus = true, onClick = a::addProfile, onLong = a::addProfile)
                }
            }
            Spacer(Modifier.height(22.dp))
            SaverBar(s, a)
        }
    }
    LaunchedEffect(Unit) {
        delay(200)
        runCatching { current.requestFocus() }
    }
}

@Composable
private fun Avatar(name: String, color: Color, sub: String, modifier: Modifier, plus: Boolean = false, onClick: () -> Unit, onLong: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.12f else 1f, tween(160), label = "av")
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(110.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .size(92.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .glow(focused, radius = 18.dp, corner = 46.dp)
                .clip(CircleShape)
                .background(if (plus) Brush.linearGradient(listOf(color, color)) else Brush.linearGradient(listOf(lerp(color, Color.White, 0.25f), lerp(color, Color.Black, 0.25f))))
                .border(if (focused) 3.dp else 1.dp, if (focused) Color.White else Color(0x55FFFFFF), CircleShape)
                .onFocusChanged { focused = it.isFocused }
                .okKeys(onClick, onLong)
                .focusable(),
        ) {
            if (plus) Icon(Ic.PLUS, 34.dp) else BasicText(name.take(1).uppercase(), style = ts(38, FontWeight.Black))
        }
        Spacer(Modifier.height(12.dp))
        BasicText(name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = ts(17, FontWeight.Bold).copy(textAlign = TextAlign.Center))
        BasicText(sub, style = ts(12, color = C.Muted).copy(textAlign = TextAlign.Center))
    }
}

/** Screensaver choice on the profile screen, like the mock's bottom bar. */
@Composable
fun SaverBar(s: UiState, a: Actions) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.glass(RoundedCornerShape(18.dp)).padding(horizontal = 18.dp, vertical = 8.dp),
    ) {
        Icon(Ic.IMAGE, 16.dp)
        Spacer(Modifier.width(8.dp))
        BasicText("Bildschirmschoner:", style = ts(14, FontWeight.Medium))
        Spacer(Modifier.width(12.dp))
        listOf("Landschaften", "Titelbilder", "Aus").forEachIndexed { i, label ->
            Choice(label, s.saverMode == i) { a.setSaverMode(i) }
        }
        Spacer(Modifier.width(18.dp))
        Choice(if (s.saverMode == 2) "—" else "Automatisch nach ${s.saverMinutes} Min.", false, a::cycleSaverMinutes)
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (focused) Color.White else Color.Transparent)
            .onFocusChanged { focused = it.isFocused }
            .okKeys(onClick)
            .focusable()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Box(
            Modifier.size(10.dp).clip(CircleShape)
                .background(if (selected) (if (focused) C.Blue else Color.White) else Color.Transparent)
                .border(1.5.dp, if (focused) C.Ink else Color.White, CircleShape)
        )
        Spacer(Modifier.width(7.dp))
        BasicText(label, style = ts(14, if (selected) FontWeight.SemiBold else FontWeight.Normal, if (focused) C.Ink else Color.White, shadow = !focused))
    }
}

