package de.limad.tvlauncher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
    Font(R.font.inter_black, FontWeight.Black),
)

/** Selectable background themes ("Oberflächendesign"). */
data class Palette(val name: String, val top: Color, val bottom: Color, val glow1: Color, val glow2: Color)

val Palettes = listOf(
    Palette("Dunkel (Standard)", Color(0xFF0D1A40), Color(0xFF03060F), Color(0x4D2F7BFF), Color(0x2E3B5BFF)),
    Palette("Blau", Color(0xFF15398F), Color(0xFF07143A), Color(0x664A9BFF), Color(0x3D3B6CFF)),
    Palette("OLED-Schwarz", Color(0xFF000000), Color(0xFF000000), Color(0x1F2F7BFF), Color(0x00000000)),
)

object C {
    val Blue = Color(0xFF2F7BFF)
    val BlueLight = Color(0xFF8CC2FF)
    val Glass = Color(0x1AFFFFFF)
    val GlassStrong = Color(0x2EFFFFFF)
    val GlassBorder = Color(0x33FFFFFF)
    val Muted = Color(0xFFB7C1D9)
    val Card = Color(0xFF16213F)
    val Ink = Color(0xFF0A0F1E)
}

private val TextShadow = Shadow(Color(0x8C000000), blurRadius = 14f)

fun ts(size: Int, weight: FontWeight = FontWeight.Normal, color: Color = Color.White, shadow: Boolean = true) =
    TextStyle(
        fontFamily = Inter,
        fontSize = size.sp,
        fontWeight = weight,
        color = color,
        shadow = if (shadow) TextShadow else null,
    )

/** Soft bloom outside the element, built from concentric translucent rounded rects. */
fun Modifier.glow(enabled: Boolean, color: Color = C.BlueLight, radius: Dp = 20.dp, corner: Dp = 16.dp) =
    drawBehind {
        if (!enabled) return@drawBehind
        val steps = 12
        for (i in steps downTo 1) {
            val spread = radius.toPx() * i / steps
            drawRoundRect(
                color = color.copy(alpha = 0.05f),
                topLeft = Offset(-spread, -spread),
                size = Size(size.width + spread * 2, size.height + spread * 2),
                cornerRadius = CornerRadius(corner.toPx() + spread),
            )
        }
    }

fun Modifier.glass(shape: Shape, strong: Boolean = false) =
    clip(shape).background(if (strong) C.GlassStrong else C.Glass).border(1.dp, C.GlassBorder, shape)

/** OK / Enter = click, holding OK = long click (D-pad remotes have no long-press gesture). */
fun Modifier.okKeys(onClick: () -> Unit, onLong: (() -> Unit)? = null): Modifier = composed {
    val longFired = remember { BooleanArray(1) }
    onKeyEvent { ev ->
        val ok = ev.key == Key.DirectionCenter || ev.key == Key.Enter || ev.key == Key.NumPadEnter
        if (!ok) return@onKeyEvent false
        when (ev.type) {
            KeyEventType.KeyDown -> {
                val r = ev.nativeKeyEvent.repeatCount
                if (r == 0) longFired[0] = false
                if (r == 1 && onLong != null && !longFired[0]) {
                    longFired[0] = true
                    onLong()
                }
                true
            }
            KeyEventType.KeyUp -> {
                if (!longFired[0]) onClick()
                longFired[0] = false
                true
            }
            else -> false
        }
    }
}

// ---------------------------------------------------------------- Line icons

enum class Ic { PLAY, PLUS, CHECK, INFO, BELL, BACK, STAR, STAR_FILLED, GEAR, MONITOR, WIFI, GRID, PERSON, SYSTEM, CHEVRON, SEARCH, POWER, MOON, TIMER, SPEAKER, ACCESS, HOME, PREV, NEXT, PAUSE, SLIDERS, IMAGE }

@Composable
fun Icon(kind: Ic, size: Dp, color: Color = Color.White, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) { drawIcon(kind, color) }
}

private fun DrawScope.drawIcon(kind: Ic, color: Color) {
    val w = size.width
    val h = size.height
    val sw = w * 0.09f
    val line = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun p(vararg pts: Pair<Float, Float>, close: Boolean = false) = Path().apply {
        pts.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(x * w, y * h) else lineTo(x * w, y * h) }
        if (close) close()
    }
    when (kind) {
        Ic.PLAY -> drawPath(p(0.24f to 0.14f, 0.86f to 0.5f, 0.24f to 0.86f, close = true), color)
        Ic.PLUS -> {
            drawLine(color, Offset(w * 0.5f, h * 0.18f), Offset(w * 0.5f, h * 0.82f), sw, StrokeCap.Round)
            drawLine(color, Offset(w * 0.18f, h * 0.5f), Offset(w * 0.82f, h * 0.5f), sw, StrokeCap.Round)
        }
        Ic.CHECK -> drawPath(p(0.18f to 0.52f, 0.42f to 0.76f, 0.84f to 0.26f), color, style = line)
        Ic.INFO -> {
            drawCircle(color, radius = w * 0.42f, style = line)
            drawLine(color, Offset(w * 0.5f, h * 0.46f), Offset(w * 0.5f, h * 0.72f), sw, StrokeCap.Round)
            drawCircle(color, radius = sw * 0.75f, center = Offset(w * 0.5f, h * 0.3f))
        }
        Ic.BELL -> {
            val bell = Path().apply {
                moveTo(w * 0.2f, h * 0.72f)
                cubicTo(w * 0.28f, h * 0.6f, w * 0.26f, h * 0.5f, w * 0.27f, h * 0.42f)
                cubicTo(w * 0.29f, h * 0.2f, w * 0.71f, h * 0.2f, w * 0.73f, h * 0.42f)
                cubicTo(w * 0.74f, h * 0.5f, w * 0.72f, h * 0.6f, w * 0.8f, h * 0.72f)
                close()
            }
            drawPath(bell, color, style = line)
            drawArc(color, 20f, 140f, false, Offset(w * 0.4f, h * 0.68f), Size(w * 0.2f, h * 0.18f), style = line)
        }
        Ic.BACK -> drawPath(p(0.62f to 0.18f, 0.3f to 0.5f, 0.62f to 0.82f), color, style = line)
        Ic.CHEVRON -> drawPath(p(0.38f to 0.18f, 0.7f to 0.5f, 0.38f to 0.82f), color, style = line)
        Ic.STAR, Ic.STAR_FILLED -> {
            val path = Path()
            for (i in 0 until 10) {
                val r = if (i % 2 == 0) 0.46f else 0.2f
                val a = Math.toRadians((-90 + i * 36).toDouble())
                val x = 0.5f + r * Math.cos(a).toFloat()
                val y = 0.53f + r * Math.sin(a).toFloat()
                if (i == 0) path.moveTo(x * w, y * h) else path.lineTo(x * w, y * h)
            }
            path.close()
            if (kind == Ic.STAR_FILLED) drawPath(path, color) else drawPath(path, color, style = line)
        }
        Ic.GEAR -> {
            drawCircle(color, radius = w * 0.16f, style = line)
            for (i in 0 until 8) {
                val a = Math.toRadians((i * 45).toDouble())
                val c = Math.cos(a).toFloat()
                val s = Math.sin(a).toFloat()
                drawLine(color, Offset(w * (0.5f + 0.27f * c), h * (0.5f + 0.27f * s)), Offset(w * (0.5f + 0.42f * c), h * (0.5f + 0.42f * s)), sw * 1.3f, StrokeCap.Round)
            }
            drawCircle(color, radius = w * 0.3f, style = line)
        }
        Ic.MONITOR -> {
            drawRoundRect(color, Offset(w * 0.1f, h * 0.18f), Size(w * 0.8f, h * 0.52f), CornerRadius(w * 0.06f), style = line)
            drawLine(color, Offset(w * 0.34f, h * 0.84f), Offset(w * 0.66f, h * 0.84f), sw, StrokeCap.Round)
        }
        Ic.WIFI -> {
            for ((i, r) in listOf(0.42f, 0.28f, 0.14f).withIndex()) {
                drawArc(color, 225f, 90f, false, Offset(w * (0.5f - r), h * (0.78f - r)), Size(w * r * 2, h * r * 2), style = line)
                if (i == 2) drawCircle(color, radius = sw * 0.9f, center = Offset(w * 0.5f, h * 0.78f))
            }
        }
        Ic.GRID -> {
            val s = w * 0.3f
            for ((x, y) in listOf(0.12f to 0.12f, 0.58f to 0.12f, 0.12f to 0.58f, 0.58f to 0.58f)) {
                drawRoundRect(color, Offset(w * x, h * y), Size(s, s), CornerRadius(w * 0.05f), style = line)
            }
        }
        Ic.PERSON -> {
            drawCircle(color, radius = w * 0.17f, center = Offset(w * 0.5f, h * 0.34f), style = line)
            drawArc(color, 200f, 140f, false, Offset(w * 0.18f, h * 0.58f), Size(w * 0.64f, h * 0.6f), style = line)
        }
        Ic.SYSTEM -> {
            drawCircle(color, radius = w * 0.42f, style = line)
            drawLine(color, Offset(w * 0.5f, h * 0.46f), Offset(w * 0.5f, h * 0.72f), sw, StrokeCap.Round)
            drawCircle(color, radius = sw * 0.75f, center = Offset(w * 0.5f, h * 0.3f))
        }
        Ic.POWER -> {
            drawArc(color, -50f, 280f, false, Offset(w * 0.16f, h * 0.18f), Size(w * 0.68f, h * 0.68f), style = line)
            drawLine(color, Offset(w * 0.5f, h * 0.1f), Offset(w * 0.5f, h * 0.48f), sw, StrokeCap.Round)
        }
        Ic.MOON -> {
            val m = Path().apply {
                moveTo(w * 0.62f, h * 0.12f)
                cubicTo(w * 0.2f, h * 0.18f, w * 0.18f, h * 0.84f, w * 0.62f, h * 0.88f)
                cubicTo(w * 0.42f, h * 0.7f, w * 0.42f, h * 0.3f, w * 0.62f, h * 0.12f)
                close()
            }
            drawPath(m, color, style = line)
        }
        Ic.TIMER -> {
            drawCircle(color, radius = w * 0.36f, center = Offset(w * 0.5f, h * 0.55f), style = line)
            drawLine(color, Offset(w * 0.5f, h * 0.55f), Offset(w * 0.5f, h * 0.34f), sw, StrokeCap.Round)
            drawLine(color, Offset(w * 0.5f, h * 0.55f), Offset(w * 0.66f, h * 0.62f), sw, StrokeCap.Round)
            drawLine(color, Offset(w * 0.4f, h * 0.08f), Offset(w * 0.6f, h * 0.08f), sw, StrokeCap.Round)
        }
        Ic.SPEAKER -> {
            drawPath(p(0.12f to 0.38f, 0.3f to 0.38f, 0.52f to 0.18f, 0.52f to 0.82f, 0.3f to 0.62f, 0.12f to 0.62f, close = true), color, style = line)
            drawArc(color, -45f, 90f, false, Offset(w * 0.46f, h * 0.3f), Size(w * 0.3f, h * 0.4f), style = line)
            drawArc(color, -45f, 90f, false, Offset(w * 0.42f, h * 0.16f), Size(w * 0.5f, h * 0.68f), style = line)
        }
        Ic.ACCESS -> {
            drawCircle(color, radius = w * 0.08f, center = Offset(w * 0.5f, h * 0.16f))
            drawLine(color, Offset(w * 0.18f, h * 0.34f), Offset(w * 0.82f, h * 0.34f), sw, StrokeCap.Round)
            drawLine(color, Offset(w * 0.5f, h * 0.34f), Offset(w * 0.5f, h * 0.6f), sw, StrokeCap.Round)
            drawLine(color, Offset(w * 0.5f, h * 0.6f), Offset(w * 0.32f, h * 0.88f), sw, StrokeCap.Round)
            drawLine(color, Offset(w * 0.5f, h * 0.6f), Offset(w * 0.68f, h * 0.88f), sw, StrokeCap.Round)
        }
        Ic.HOME -> drawPath(p(0.14f to 0.48f, 0.5f to 0.16f, 0.86f to 0.48f, 0.76f to 0.48f, 0.76f to 0.84f, 0.24f to 0.84f, 0.24f to 0.48f, close = true), color, style = line)
        Ic.PREV -> {
            drawPath(p(0.82f to 0.2f, 0.36f to 0.5f, 0.82f to 0.8f, close = true), color)
            drawLine(color, Offset(w * 0.24f, h * 0.2f), Offset(w * 0.24f, h * 0.8f), sw * 1.4f, StrokeCap.Round)
        }
        Ic.NEXT -> {
            drawPath(p(0.18f to 0.2f, 0.64f to 0.5f, 0.18f to 0.8f, close = true), color)
            drawLine(color, Offset(w * 0.76f, h * 0.2f), Offset(w * 0.76f, h * 0.8f), sw * 1.4f, StrokeCap.Round)
        }
        Ic.PAUSE -> {
            drawRoundRect(color, Offset(w * 0.24f, h * 0.18f), Size(w * 0.17f, h * 0.64f), CornerRadius(w * 0.04f))
            drawRoundRect(color, Offset(w * 0.59f, h * 0.18f), Size(w * 0.17f, h * 0.64f), CornerRadius(w * 0.04f))
        }
        Ic.SLIDERS -> {
            for ((i, y) in listOf(0.28f, 0.5f, 0.72f).withIndex()) {
                drawLine(color, Offset(w * 0.14f, h * y), Offset(w * 0.86f, h * y), sw, StrokeCap.Round)
                val x = listOf(0.34f, 0.66f, 0.46f)[i]
                drawCircle(color, radius = w * 0.08f, center = Offset(w * x, h * y))
            }
        }
        Ic.IMAGE -> {
            drawRoundRect(color, Offset(w * 0.1f, h * 0.18f), Size(w * 0.8f, h * 0.64f), CornerRadius(w * 0.08f), style = line)
            drawPath(p(0.16f to 0.74f, 0.4f to 0.48f, 0.56f to 0.64f, 0.68f to 0.54f, 0.86f to 0.74f), color, style = line)
            drawCircle(color, radius = w * 0.06f, center = Offset(w * 0.68f, h * 0.34f))
        }
        Ic.SEARCH -> {
            drawCircle(color, radius = w * 0.26f, center = Offset(w * 0.43f, h * 0.43f), style = line)
            drawLine(color, Offset(w * 0.62f, h * 0.62f), Offset(w * 0.84f, h * 0.84f), sw, StrokeCap.Round)
        }
    }
}

/** LiMaD-TV logo mark: glowing blue play triangle. */
@Composable
fun LogoMark(size: Dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val tri = Path().apply {
            moveTo(w * 0.2f, h * 0.1f); lineTo(w * 0.9f, h * 0.5f); lineTo(w * 0.2f, h * 0.9f); close()
        }
        for (i in 6 downTo 1) drawPath(tri, C.Blue.copy(alpha = 0.08f), style = Stroke(width = i * 2.6f, join = StrokeJoin.Round))
        drawPath(tri, androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF9CCBFF), Color(0xFF1F5FE0)), Offset.Zero, Offset(w, h)))
        drawPath(tri, Color(0x99FFFFFF), style = Stroke(width = 2f, join = StrokeJoin.Round))
    }
}

