package de.limad.tvlauncher

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

data class AppEntry(
    val label: String,
    val pkg: String,
    val icon: ImageBitmap,
    val isBanner: Boolean,
    val intent: Intent,
    /** Average colour of banner/icon, used as tile and backdrop tint. */
    val tint: Int,
    /** Tiny version of the artwork; drawn full-screen it becomes a soft blurred backdrop. */
    val backdrop: ImageBitmap,
)

object AppRepository {

    /** Packages never shown in the launcher. */
    private val HIDDEN = setOf(
        "com.amazon.tv.launcher",
        "com.amazon.tv.settings.v2", // reachable via the settings tile
    )

    fun load(ctx: Context): List<AppEntry> {
        val pm = ctx.packageManager
        val result = LinkedHashMap<String, AppEntry>()
        val categories = listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)

        for (category in categories) {
            val query = Intent(Intent.ACTION_MAIN).addCategory(category)
            for (ri in pm.queryIntentActivities(query, 0)) {
                val pkg = ri.activityInfo.packageName
                if (pkg in result || pkg == ctx.packageName || pkg in HIDDEN) continue

                val launch = pm.getLeanbackLaunchIntentForPackage(pkg)
                    ?: pm.getLaunchIntentForPackage(pkg)
                    ?: continue

                val banner = runCatching {
                    ri.activityInfo.loadBanner(pm) ?: ri.activityInfo.applicationInfo.loadBanner(pm)
                }.getOrNull()

                val bitmap = if (banner != null) {
                    banner.toBitmap(400, 225, Bitmap.Config.ARGB_8888)
                } else {
                    ri.loadIcon(pm).toBitmap(192, 192, Bitmap.Config.ARGB_8888)
                }

                result[pkg] = AppEntry(
                    label = ri.loadLabel(pm).toString(),
                    pkg = pkg,
                    icon = bitmap.asImageBitmap(),
                    isBanner = banner != null,
                    intent = launch,
                    tint = averageColor(bitmap),
                    backdrop = if (banner != null) {
                        Bitmap.createScaledBitmap(bitmap, 128, 72, true).asImageBitmap()
                    } else {
                        Bitmap.createScaledBitmap(bitmap, 12, 12, true).asImageBitmap()
                    },
                )
            }
        }
        return result.values.sortedBy { it.label.lowercase() }
    }

    /** Opaque average colour, ignoring transparent pixels. */
    private fun averageColor(b: Bitmap): Int {
        val small = Bitmap.createScaledBitmap(b, 16, 16, true)
        var r = 0L; var g = 0L; var bl = 0L; var n = 0L
        for (x in 0 until 16) for (y in 0 until 16) {
            val p = small.getPixel(x, y)
            val a = p ushr 24
            if (a < 128) continue
            r += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; bl += p and 0xFF; n++
        }
        if (n == 0L) return 0xFF3A3F4B.toInt()
        return (0xFF shl 24) or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (bl / n).toInt()
    }
}
