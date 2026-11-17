package de.limad.tvlauncher

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** One "top shelf" picture: a current title on the focused streaming service. */
data class HeroItem(
    val title: String,
    val overview: String,
    val backdrop: String,
    val logo: String?,
)

/**
 * Backdrops of currently popular titles per streaming service, from TMDB.
 * The service is matched by the app label against TMDB's provider list for Germany,
 * so no provider IDs need to be hard-coded.
 */
object Tmdb {

    private const val API = "https://api.themoviedb.org/3"
    private const val IMG = "https://image.tmdb.org/t/p"
    private const val PREFS = "limad_tv"
    private const val KEY_TOKEN = "tmdb_token"

    private val mutex = Mutex()
    private var providers: Map<String, Int>? = null
    private val cache = HashMap<String, List<HeroItem>>()

    fun saveToken(ctx: Context, token: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_TOKEN, token.trim()).apply()
        cache.clear()
        providers = null
    }

    private fun token(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TOKEN, null)

    fun hasToken(ctx: Context) = !token(ctx).isNullOrBlank()

    suspend fun heroes(ctx: Context, appLabel: String): List<HeroItem> = withContext(Dispatchers.IO) {
        val token = token(ctx) ?: return@withContext emptyList()
        mutex.withLock {
            cache[appLabel]?.let { return@withContext it }
            val providerId = provider(token, appLabel)
            val items = if (providerId == null) emptyList() else runCatching { load(token, providerId) }.getOrDefault(emptyList())
            cache[appLabel] = items
            items
        }
    }

    /** Popular titles across several services, interleaved: (service label, title). */
    suspend fun mixed(ctx: Context, labels: List<String>, perService: Int = 4): List<Pair<String, HeroItem>> {
        val lists = labels.map { l -> l to heroes(ctx, l).take(perService) }.filter { it.second.isNotEmpty() }
        val out = ArrayList<Pair<String, HeroItem>>()
        for (i in 0 until perService) for ((l, items) in lists) items.getOrNull(i)?.let { out += l to it }
        return out
    }

    // --- provider matching ---------------------------------------------------------

    private fun norm(s: String) = s.lowercase().replace("+", "plus").filter { it.isLetterOrDigit() }

    private fun provider(token: String, label: String): Int? {
        val map = providers ?: runCatching {
            val all = HashMap<String, Int>()
            for (type in listOf("tv", "movie")) {
                val res = get(token, "/watch/providers/$type?watch_region=DE&language=de-DE").getJSONArray("results")
                for (i in 0 until res.length()) {
                    val p = res.getJSONObject(i)
                    all.putIfAbsent(norm(p.getString("provider_name")), p.getInt("provider_id"))
                }
            }
            all
        }.getOrNull()?.also { providers = it } ?: return null

        val l = norm(label)
        if (l.length < 2) return null
        ALIASES[l]?.let { alias -> map[alias]?.let { return it } }
        map[l]?.let { return it }
        // e.g. "Prime Video" -> "amazonprimevideo", "ARD Mediathek" -> "ardmediathek"
        return map.entries
            .filter { (name, _) -> name.contains(l) || l.contains(name) }
            .minByOrNull { it.key.length }
            ?.value
    }

    private val ALIASES = mapOf(
        "primevideo" to "amazonprimevideo",
        "appletv" to "appletvplus",
        "wow" to "wow",
        "rtlplus" to "rtlplus",
        "disneyplus" to "disneyplus",
        "youtube" to "youtube",
    )

    // --- titles --------------------------------------------------------------------

    private fun load(token: String, providerId: Int): List<HeroItem> {
        data class Raw(val type: String, val id: Int, val title: String, val overview: String, val backdrop: String, val pop: Double)

        val raws = ArrayList<Raw>()
        for (type in listOf("tv", "movie")) {
            val path = "/discover/$type?watch_region=DE&with_watch_providers=$providerId" +
                "&with_watch_monetization_types=flatrate%7Cfree%7Cads&sort_by=popularity.desc&language=de-DE&page=1"
            val res = get(token, path).getJSONArray("results")
            for (i in 0 until res.length()) {
                val o = res.getJSONObject(i)
                val backdrop = o.optString("backdrop_path").takeIf { it.isNotBlank() && it != "null" } ?: continue
                raws += Raw(
                    type = type,
                    id = o.getInt("id"),
                    title = o.optString(if (type == "tv") "name" else "title"),
                    overview = o.optString("overview"),
                    backdrop = "$IMG/w1280$backdrop",
                    pop = o.optDouble("popularity", 0.0),
                )
            }
        }
        return raws.sortedByDescending { it.pop }.take(8).map { r ->
            HeroItem(r.title, r.overview, r.backdrop, runCatching { logo(token, r.type, r.id) }.getOrNull())
        }
    }

    /** Title treatment (logo artwork), German first, then English, then language-neutral. */
    private fun logo(token: String, type: String, id: Int): String? {
        val logos: JSONArray = get(token, "/$type/$id/images?include_image_language=de,en,null").optJSONArray("logos") ?: return null
        var best: JSONObject? = null
        var bestRank = Int.MAX_VALUE
        for (i in 0 until logos.length()) {
            val l = logos.getJSONObject(i)
            val rank = when (l.optString("iso_639_1")) { "de" -> 0; "en" -> 1; else -> 2 }
            if (rank < bestRank) { best = l; bestRank = rank }
        }
        return best?.optString("file_path")?.takeIf { it.isNotBlank() }?.let { "$IMG/w500$it" }
    }

    private fun get(token: String, path: String): JSONObject {
        val conn = URL(API + path).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 10000
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Accept", "application/json")
        try {
            return JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }
}
