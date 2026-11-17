package de.limad.tvlauncher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Watchlist, ratings, profile name and design choice. */
class UserStore(ctx: Context) {

    private val sp = ctx.getSharedPreferences("limad_tv_user", Context.MODE_PRIVATE)

    var name: String
        get() = sp.getString("name", "") ?: ""
        set(v) = sp.edit().putString("name", v.trim()).apply()

    var paletteIndex: Int
        get() = sp.getInt("palette", 0).coerceIn(0, Palettes.size - 1)
        set(v) = sp.edit().putInt("palette", v).apply()

    // --- watchlist ---------------------------------------------------------------------------

    fun watchlist(): List<Title> {
        val raw = sp.getString("watchlist", null) ?: return emptyList()
        return runCatching {
            val a = JSONArray(raw)
            (0 until a.length()).map {
                val o = a.getJSONObject(it)
                Title(
                    id = o.getInt("id"),
                    type = o.getString("type"),
                    title = o.getString("title"),
                    overview = o.optString("overview"),
                    backdrop = o.optString("backdrop").ifEmpty { null },
                    year = o.optString("year"),
                    rating = o.optDouble("rating", 0.0),
                    service = o.optString("service").ifEmpty { null },
                )
            }
        }.getOrDefault(emptyList())
    }

    fun inWatchlist(t: Title) = watchlist().any { it.key == t.key }

    /** Returns true if the title is now on the list. */
    fun toggleWatch(t: Title): Boolean {
        val list = watchlist().toMutableList()
        val now = if (list.any { it.key == t.key }) {
            list.removeAll { it.key == t.key }
            false
        } else {
            list.add(0, t)
            true
        }
        val a = JSONArray()
        list.take(60).forEach {
            a.put(
                JSONObject()
                    .put("id", it.id).put("type", it.type).put("title", it.title)
                    .put("overview", it.overview).put("backdrop", it.backdrop ?: "")
                    .put("year", it.year).put("rating", it.rating).put("service", it.service ?: "")
            )
        }
        sp.edit().putString("watchlist", a.toString()).apply()
        return now
    }

    fun clearWatchlist() = sp.edit().remove("watchlist").apply()

    // --- ratings -------------------------------------------------------------------------------

    fun liked(t: Title) = sp.getStringSet("liked", emptySet())!!.contains(t.key)

    fun toggleLike(t: Title): Boolean {
        val set = sp.getStringSet("liked", emptySet())!!.toMutableSet()
        val now = if (t.key in set) { set.remove(t.key); false } else { set.add(t.key); true }
        sp.edit().putStringSet("liked", set).apply()
        return now
    }

    fun clearLikes() = sp.edit().remove("liked").apply()
}
