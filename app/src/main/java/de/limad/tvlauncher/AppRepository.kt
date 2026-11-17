package de.limad.tvlauncher

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

data class AppEntry(
    val label: String,
    val pkg: String,
    val icon: ImageBitmap,
    val isBanner: Boolean,
    val intent: Intent,
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
                    banner.toBitmap(320, 180)
                } else {
                    ri.loadIcon(pm).toBitmap(192, 192)
                }

                result[pkg] = AppEntry(
                    label = ri.loadLabel(pm).toString(),
                    pkg = pkg,
                    icon = bitmap.asImageBitmap(),
                    isBanner = banner != null,
                    intent = launch,
                )
            }
        }
        return result.values.sortedBy { it.label.lowercase() }
    }
}
