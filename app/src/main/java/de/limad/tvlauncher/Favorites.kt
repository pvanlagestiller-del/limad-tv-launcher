package de.limad.tvlauncher

import android.content.Context

/** Favourites order and hidden apps, stored in SharedPreferences. */
class Favorites(ctx: Context) {

    private val prefs = ctx.getSharedPreferences("limad_tv", Context.MODE_PRIVATE)

    /** Returns the ordered favorite packages that are currently installed. */
    fun get(all: List<AppEntry>): List<String> {
        val installed = all.map { it.pkg }.toSet()
        val stored = prefs.getString(KEY_FAV, null)
        if (stored != null) {
            return stored.split(',').filter { it.isNotBlank() && it in installed }
        }
        // First run: pick defaults by label so package names need not be known in advance.
        return DEFAULT_KEYWORDS.mapNotNull { keyword ->
            all.firstOrNull { it.label.contains(keyword, ignoreCase = true) }?.pkg
        }.distinct()
    }

    fun set(packages: List<String>) {
        prefs.edit().putString(KEY_FAV, packages.joinToString(",")).apply()
    }

    /** Hidden apps. Until the user changes it, Amazon promo and system apps are hidden. */
    fun hidden(all: List<AppEntry>): Set<String> {
        val stored = prefs.getString(KEY_HIDDEN, null)
        if (stored != null) return stored.split(',').filter { it.isNotBlank() }.toSet()
        return all.filter(::isJunk).map { it.pkg }.toSet()
    }

    fun setHidden(packages: Set<String>) {
        prefs.edit().putString(KEY_HIDDEN, packages.joinToString(",")).apply()
    }

    var lastOpened: String?
        get() = prefs.getString(KEY_LAST, null)
        set(v) = prefs.edit().putString(KEY_LAST, v).apply()

    private fun isJunk(app: AppEntry): Boolean {
        val systemPkg = JUNK_PREFIXES.any { app.pkg.startsWith(it) }
        val keep = KEEP_LABELS.any { app.label.contains(it, ignoreCase = true) }
        return systemPkg && !keep
    }

    companion object {
        private const val KEY_FAV = "favorites"
        private const val KEY_HIDDEN = "hidden"
        private const val KEY_LAST = "last_opened"
        private val DEFAULT_KEYWORDS = listOf("JW", "LiMusic", "Netflix", "Prime Video", "YouTube", "Disney")
        private val JUNK_PREFIXES = listOf("com.amazon.", "com.android.", "com.fireos.", "amazon.")
        private val KEEP_LABELS = listOf("Prime Video", "Amazon Music", "Appstore")
    }
}
