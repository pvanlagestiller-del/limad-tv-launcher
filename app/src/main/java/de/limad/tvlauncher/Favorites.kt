package de.limad.tvlauncher

import android.content.Context

class Favorites(ctx: Context) {

    private val prefs = ctx.getSharedPreferences("limad_tv", Context.MODE_PRIVATE)

    /** Returns the ordered favorite packages that are currently installed. */
    fun get(all: List<AppEntry>): List<String> {
        val installed = all.map { it.pkg }.toSet()
        val stored = prefs.getString(KEY, null)
        if (stored != null) {
            return stored.split(',').filter { it.isNotBlank() && it in installed }
        }
        // First run: pick defaults by label so package names need not be known in advance.
        return DEFAULT_KEYWORDS.mapNotNull { keyword ->
            all.firstOrNull { it.label.contains(keyword, ignoreCase = true) }?.pkg
        }.distinct()
    }

    fun set(packages: List<String>) {
        prefs.edit().putString(KEY, packages.joinToString(",")).apply()
    }

    companion object {
        private const val KEY = "favorites"
        private val DEFAULT_KEYWORDS = listOf("JW", "LiMusic", "Netflix", "Prime Video", "YouTube", "Disney")
    }
}
