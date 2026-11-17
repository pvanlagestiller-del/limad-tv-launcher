package de.limad.tvlauncher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Profile(val id: String, val name: String, val color: Int)

/** Avatar colours for profiles (gradient start colours). */
val ProfileColors = listOf(0xFFF06292, 0xFF64B5F6, 0xFF81C784, 0xFFFFD54F, 0xFFBA68C8, 0xFF4DD0E1)

/**
 * Profiles, per-profile watchlist and ratings, design and screensaver settings.
 */
class UserStore(ctx: Context) {

    private val sp = ctx.getSharedPreferences("limad_tv_user", Context.MODE_PRIVATE)

    // --- profiles ------------------------------------------------------------------------------

    fun profiles(): List<Profile> {
        val raw = sp.getString("profiles", null)
        val list = runCatching {
            val a = JSONArray(raw ?: "[]")
            (0 until a.length()).map { a.getJSONObject(it).let { o -> Profile(o.getString("id"), o.getString("name"), o.getInt("color")) } }
        }.getOrDefault(emptyList())
        if (list.isNotEmpty()) return list
        // First start: one profile, named like the old single-user name if there was one.
        val first = Profile("p1", sp.getString("name", null)?.ifBlank { null } ?: "Ich", 0)
        saveProfiles(listOf(first))
        return listOf(first)
    }

    private fun saveProfiles(list: List<Profile>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("color", it.color)) }
        sp.edit().putString("profiles", a.toString()).apply()
    }

    var currentId: String
        get() = sp.getString("current_profile", null) ?: profiles().first().id
        set(v) = sp.edit().putString("current_profile", v).apply()

    fun current(): Profile = profiles().firstOrNull { it.id == currentId } ?: profiles().first()

    fun addProfile(name: String): Profile {
        val list = profiles()
        val id = "p" + (System.currentTimeMillis() % 1_000_000)
        val p = Profile(id, name.trim().ifBlank { "Profil ${list.size + 1}" }, list.size % ProfileColors.size)
        saveProfiles(list + p)
        return p
    }

    fun renameProfile(id: String, name: String) =
        saveProfiles(profiles().map { if (it.id == id) it.copy(name = name.trim().ifBlank { it.name }) else it })

    fun deleteProfile(id: String) {
        val list = profiles().filterNot { it.id == id }
        if (list.isEmpty()) return
        saveProfiles(list)
        if (currentId == id) currentId = list.first().id
    }

    var askProfileOnStart: Boolean
        get() = sp.getBoolean("ask_profile", true)
        set(v) = sp.edit().putBoolean("ask_profile", v).apply()

    // --- design & screensaver ----------------------------------------------------------------------

    var paletteIndex: Int
        get() = sp.getInt("palette", 0).coerceIn(0, Palettes.size - 1)
        set(v) = sp.edit().putInt("palette", v).apply()

    /** 0 = Landschaften, 1 = Titelbilder, 2 = Aus */
    var saverMode: Int
        get() = sp.getInt("saver_mode", 0)
        set(v) = sp.edit().putInt("saver_mode", v).apply()

    var saverMinutes: Int
        get() = sp.getInt("saver_minutes", 5)
        set(v) = sp.edit().putInt("saver_minutes", v).apply()

    // --- per-profile watchlist ------------------------------------------------------------------------

    private fun k(name: String) = "${currentId}_$name"

    fun watchlist(): List<Title> {
        val raw = sp.getString(k("watchlist"), null) ?: return emptyList()
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
        sp.edit().putString(k("watchlist"), a.toString()).apply()
        return now
    }

    fun clearWatchlist() = sp.edit().remove(k("watchlist")).apply()

    // --- per-profile ratings ----------------------------------------------------------------------------

    fun liked(t: Title) = sp.getStringSet(k("liked"), emptySet())!!.contains(t.key)

    fun toggleLike(t: Title): Boolean {
        val set = sp.getStringSet(k("liked"), emptySet())!!.toMutableSet()
        val now = if (t.key in set) { set.remove(t.key); false } else { set.add(t.key); true }
        sp.edit().putStringSet(k("liked"), set).apply()
        return now
    }

    fun clearLikes() = sp.edit().remove(k("liked")).apply()
}
