package de.limad.tvlauncher

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.graphics.Color as AColor
import android.graphics.Typeface
import android.service.dreams.DreamService
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The four nature backgrounds shipped with the app. */
val NatureImages = listOf(
    R.drawable.bg_alpen_bergsee,
    R.drawable.bg_tropischer_wasserfall,
    R.drawable.bg_mittelmeerkueste,
    R.drawable.bg_wueste_sonnenuntergang,
)

private const val SLIDE_MS = 20_000L

/**
 * In-app screensaver: slow Ken-Burns zoom/pan, crossfade every 20 s, clock and date.
 * [titleUrls] are used when the "Titelbilder" mode is active; otherwise the nature images.
 */
@Composable
fun Screensaver(titleUrls: List<String>) {
    val useTitles = titleUrls.isNotEmpty()
    val count = if (useTitles) titleUrls.size else NatureImages.size
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(count) {
        while (true) {
            delay(SLIDE_MS)
            index = (index + 1) % count
        }
    }
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(5_000)
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Crossfade(targetState = index, animationSpec = tween(2500), label = "saver") { i ->
            val zoom = rememberInfiniteTransition(label = "kb$i")
            val scale by zoom.animateFloat(1.0f, 1.14f, infiniteRepeatable(tween(SLIDE_MS.toInt() + 3000, easing = LinearEasing), RepeatMode.Reverse), label = "s")
            val dx by zoom.animateFloat(if (i % 2 == 0) -20f else 20f, if (i % 2 == 0) 20f else -20f, infiniteRepeatable(tween(SLIDE_MS.toInt() + 3000, easing = LinearEasing), RepeatMode.Reverse), label = "x")
            val m = Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale; translationX = dx * density }
            if (useTitles) {
                AsyncImage(titleUrls[i % titleUrls.size], null, contentScale = ContentScale.Crop, modifier = m)
            } else {
                Image(painterResource(NatureImages[i % NatureImages.size]), null, contentScale = ContentScale.Crop, modifier = m)
            }
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color(0xB3000000))))

        Column(Modifier.align(Alignment.BottomStart).padding(start = 56.dp, bottom = 48.dp)) {
            BasicText(SimpleDateFormat("HH:mm", Locale.GERMANY).format(now), style = ts(84, FontWeight.Light))
            BasicText(SimpleDateFormat("EEEE, d. MMMM", Locale.GERMANY).format(now), style = ts(22, FontWeight.Medium, Color(0xE6FFFFFF)))
        }
        Row(Modifier.align(Alignment.BottomEnd).padding(end = 56.dp, bottom = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            LogoMark(26.dp)
            Spacer(Modifier.width(8.dp))
            BasicText("LiMaD-TV", style = ts(20, FontWeight.Bold, Color(0xCCFFFFFF)))
        }
        Spacer(Modifier.height(0.dp))
    }
}

/**
 * System screensaver (Daydream) with the same images, built from plain views
 * because DreamService has no Compose lifecycle. Fire OS uses it only if it was
 * selected via ADB (the setup wizard does that).
 */
class LimadDream : DreamService() {

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var index = 0
    private lateinit var front: ImageView // top
    private lateinit var back: ImageView

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true

        val root = FrameLayout(this).apply { setBackgroundColor(AColor.BLACK) }
        back = image()
        front = image()
        root.addView(back, FrameLayout.LayoutParams(-1, -1))
        root.addView(front, FrameLayout.LayoutParams(-1, -1))
        root.addView(View(this).apply {
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(AColor.TRANSPARENT, AColor.TRANSPARENT, 0xB3000000.toInt()),
            )
        }, FrameLayout.LayoutParams(-1, -1))

        val d = resources.displayMetrics.density
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((56 * d).toInt(), 0, 0, (48 * d).toInt())
        }
        box.addView(TextClock(this).apply {
            format24Hour = "HH:mm"; format12Hour = "HH:mm"
            textSize = 84f; setTextColor(AColor.WHITE); typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        })
        box.addView(TextClock(this).apply {
            format24Hour = "EEEE, d. MMMM"; format12Hour = "EEEE, d. MMMM"
            textSize = 22f; setTextColor(0xE6FFFFFF.toInt())
        })
        root.addView(box, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START))
        root.addView(TextView(this).apply {
            text = "LiMaD-TV"; textSize = 20f; setTextColor(0xCCFFFFFF.toInt()); typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, (56 * d).toInt(), (56 * d).toInt())
        }, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END))
        setContentView(root)

        front.setImageResource(NatureImages[0])
        kenBurns(front)
        handler.postDelayed(::next, SLIDE_MS)
    }

    private fun image() = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }

    private var topShown = true

    /** Crossfade by fading the top view in/out; z-order never changes, so the clock stays on top. */
    private fun next() {
        index = (index + 1) % NatureImages.size
        if (topShown) {
            back.setImageResource(NatureImages[index])
            kenBurns(back)
            front.animate().alpha(0f).setDuration(2500).start()
        } else {
            front.setImageResource(NatureImages[index])
            kenBurns(front)
            front.animate().alpha(1f).setDuration(2500).start()
        }
        topShown = !topShown
        handler.postDelayed(::next, SLIDE_MS)
    }

    private fun kenBurns(v: ImageView) {
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(v, View.SCALE_X, 1f, 1.14f),
                ObjectAnimator.ofFloat(v, View.SCALE_Y, 1f, 1.14f),
                ObjectAnimator.ofFloat(v, View.TRANSLATION_X, -30f, 30f),
            )
            duration = SLIDE_MS + 3000
            interpolator = LinearInterpolator()
            start()
        }
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacksAndMessages(null)
        super.onDetachedFromWindow()
    }
}
