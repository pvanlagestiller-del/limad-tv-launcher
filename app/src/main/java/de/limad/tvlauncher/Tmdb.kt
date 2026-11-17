package de.limad.tvlauncher

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/** A film or series from TMDB. [service] = label of the installed app it was found on. */
data class Title(
    val id: Int,
    val type: String, // "movie" | "tv"
    val title: String,
    val overview: String,
    val backdrop: String?,
    val year: String,
    val rating: Double,
    val service: String? = null,
) {
    fun backdropUrl(size: String = "w780") = backdrop?.let { "https://image.tmdb.org/t/p/$size$it" }
    val key get() = "$type:$id"
}

data class Details(
    val title: Title,
    val logo: String?,
    val genres: List<String>,
    val runtime: String,
    val certification: String?,
    val providerIds: List<Int>,
    val similar: List<Title>,
)

/**
 * TMDB access (films/series, backdrops, logos, availability in Germany).
 * Streaming services are matched to installed apps by name via TMDB's provider list.
 */
object Tmdb {

    private const val API = "https://api.themoviedb.org/3"
    private const val IMG = "https://image.tmdb.org/t/p"
    private const val PREFS = "limad_tv"
    private const val KEY_TOKEN = "tmdb_token"
    private const val REGION = "watch_region=DE&language=de-DE"

    private val json = ConcurrentHashMap<String, JSONObject>()
    private val logos = ConcurrentHashMap<String, String>()
    @Volatile private var providers: Map<String, Int>? = null

    // --- token -------------------------------------------------------------------------

    fun saveToken(ctx: Context, token: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_TOKEN, token.trim()).apply()
        json.clear()
        logos.clear()
        providers = null
    }

    private fun token(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TOKEN, null)

    fun hasToken(ctx: Context) = !token(ctx).isNullOrBlank()

    // --- services ----------------------------------------------------------------------

    private fun norm(s: String) = s.lowercase().replace("+", "plus").filter { it.isLetterOrDigit() }

    private val ALIASES = mapOf(
        "primevideo" to "amazonprimevideo",
        "appletv" to "appletvplus",
        "ardmediathek" to "ardmediathek",
        "zdf" to "zdfmediathek",
    )

    /** TMDB provider id for an installed app label, or null if it is not a film/series service. */
    suspend fun providerId(ctx: Context, label: String): Int? = withContext(Dispatchers.IO) {
        val map = providers ?: runCatching {
            val all = HashMap<String, Int>()
            for (type in listOf("tv", "movie")) {
                val res = get(ctx, "/watch/providers/$type?$REGION")?.optJSONArray("results") ?: continue
                for (i in 0 until res.length()) {
                    val p = res.getJSONObject(i)
                    all.putIfAbsent(norm(p.getString("provider_name")), p.getInt("provider_id"))
                }
            }
            all.takeIf { it.isNotEmpty() }
        }.getOrNull()?.also { providers = it } ?: return@withContext null

        val l = norm(label)
        if (l.length < 2) return@withContext null
        ALIASES[l]?.let { a -> map[a]?.let { return@withContext it } }
        map[l] ?: map.entries
            .filter { (name, _) -> (name.startsWith(l) || l.startsWith(name)) && minOf(name.length, l.length) >= 3 }
            .minByOrNull { it.key.length }
            ?.value
    }

    // --- lists ------------------------------------------------------------------------------

    suspend fun discover(
        ctx: Context,
        type: String,
        providerIds: List<Int>,
        sort: String = "popularity.desc",
        minVotes: Int = 0,
        service: String? = null,
    ): List<Title> = withContext(Dispatchers.IO) {
        if (providerIds.isEmpty()) return@withContext emptyList()
        val ids = providerIds.joinToString("%7C")
        val path = "/discover/$type?$REGION&with_watch_providers=$ids" +
            "&with_watch_monetization_types=flatrate%7Cfree%7Cads&sort_by=$sort&vote_count.gte=$minVotes&page=1"
        parse(get(ctx, path), type, service)
    }

    /** Films and series on one service, merged by popularity. */
    suspend fun popularOn(ctx: Context, providerId: Int, service: String): List<Title> {
        val tv = discover(ctx, "tv", listOf(providerId), service = service)
        val mv = discover(ctx, "movie", listOf(providerId), service = service)
        return interleave(tv, mv)
    }

    suspend fun trending(ctx: Context, type: String): List<Title> = withContext(Dispatchers.IO) {
        parse(get(ctx, "/trending/$type/week?language=de-DE"), if (type == "all") null else type, null)
    }

    suspend fun search(ctx: Context, q: String): List<Title> = withContext(Dispatchers.IO) {
        val enc = URLEncoder.encode(q, "UTF-8")
        parse(get(ctx, "/search/multi?language=de-DE&include_adult=false&query=$enc"), null, null)
    }

    fun <T> interleave(vararg lists: List<T>): List<T> {
        val out = ArrayList<T>()
        val max = lists.maxOfOrNull { it.size } ?: 0
        for (i in 0 until max) for (l in lists) l.getOrNull(i)?.let { out += it }
        return out
    }

    // --- title artwork / details ------------------------------------------------------------

    suspend fun logo(ctx: Context, t: Title): String? = withContext(Dispatchers.IO) {
        logos[t.key]?.let { return@withContext it.ifEmpty { null } }
        val res = get(ctx, "/${t.type}/${t.id}/images?include_image_language=de,en,null")
        val url = res?.optJSONArray("logos")?.let(::bestLogo)
        logos[t.key] = url ?: ""
        url
    }

    suspend fun details(ctx: Context, t: Title): Details? = withContext(Dispatchers.IO) {
        val extra = if (t.type == "movie") "release_dates" else "content_ratings"
        val o = get(
            ctx,
            "/${t.type}/${t.id}?language=de-DE&include_image_language=de,en,null" +
                "&append_to_response=images,similar,watch/providers,$extra",
        ) ?: return@withContext null

        val genres = o.optJSONArray("genres")?.let { a -> (0 until a.length()).map { a.getJSONObject(it).getString("name") } } ?: emptyList()
        val runtime = if (t.type == "movie") {
            val m = o.optInt("runtime", 0)
            if (m > 0) (if (m >= 60) "${m / 60} h ${m % 60} min" else "$m min") else ""
        } else {
            val s = o.optInt("number_of_seasons", 0)
            if (s > 0) (if (s == 1) "1 Staffel" else "$s Staffeln") else ""
        }
        val cert = if (t.type == "movie") {
            o.optJSONObject("release_dates")?.optJSONArray("results")?.let { arr ->
                (0 until arr.length()).map { arr.getJSONObject(it) }.firstOrNull { it.optString("iso_3166_1") == "DE" }
                    ?.optJSONArray("release_dates")?.let { rd ->
                        (0 until rd.length()).map { rd.getJSONObject(it).optString("certification") }.firstOrNull { it.isNotBlank() }
                    }
            }
        } else {
            o.optJSONObject("content_ratings")?.optJSONArray("results")?.let { arr ->
                (0 until arr.length()).map { arr.getJSONObject(it) }.firstOrNull { it.optString("iso_3166_1") == "DE" }
                    ?.optString("rating")?.takeIf { it.isNotBlank() }
            }
        }
        val de = o.optJSONObject("watch/providers")?.optJSONObject("results")?.optJSONObject("DE")
        val providerIds = listOf("flatrate", "free", "ads").flatMap { k ->
            de?.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.getJSONObject(it).getInt("provider_id") } } ?: emptyList()
        }.distinct()
        val logo = o.optJSONObject("images")?.optJSONArray("logos")?.let(::bestLogo)
        logo?.let { logos[t.key] = it }

        val full = t.copy(
            title = o.optString(if (t.type == "tv") "name" else "title", t.title),
            overview = o.optString("overview").ifBlank { t.overview },
            backdrop = o.optString("backdrop_path").takeIf { it.isNotBlank() && it != "null" } ?: t.backdrop,
        )
        Details(
            title = full,
            logo = logo,
            genres = genres,
            runtime = runtime,
            certification = cert?.let { if (it.all(Char::isDigit)) "FSK $it" else it },
            providerIds = providerIds,
            similar = parse(o.optJSONObject("similar"), t.type, null),
        )
    }

    // --- internals ---------------------------------------------------------------------------

    private fun bestLogo(arr: JSONArray): String? {
        var best: JSONObject? = null
        var rank = Int.MAX_VALUE
        for (i in 0 until arr.length()) {
            val l = arr.getJSONObject(i)
            if (l.optString("file_path").endsWith(".svg")) continue
            val r = when (l.optString("iso_639_1")) { "de" -> 0; "en" -> 1; else -> 2 }
            if (r < rank) { best = l; rank = r }
        }
        return best?.optString("file_path")?.takeIf { it.isNotBlank() }?.let { "$IMG/w500$it" }
    }

    private fun parse(o: JSONObject?, forcedType: String?, service: String?): List<Title> {
        val res = o?.optJSONArray("results") ?: return emptyList()
        val out = ArrayList<Title>()
        for (i in 0 until res.length()) {
            val r = res.getJSONObject(i)
            val type = forcedType ?: r.optString("media_type")
            if (type != "movie" && type != "tv") continue
            val backdrop = r.optString("backdrop_path").takeIf { it.isNotBlank() && it != "null" } ?: continue
            val date = r.optString(if (type == "tv") "first_air_date" else "release_date")
            out += Title(
                id = r.getInt("id"),
                type = type,
                title = r.optString(if (type == "tv") "name" else "title"),
                overview = r.optString("overview"),
                backdrop = backdrop,
                year = date.take(4),
                rating = r.optDouble("vote_average", 0.0),
                service = service,
            )
        }
        return out
    }

    private fun get(ctx: Context, path: String): JSONObject? {
        json[path]?.let { return it }
        val token = token(ctx) ?: return null
        return runCatching {
            val conn = URL(API + path).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 12000
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Accept", "application/json")
            try {
                JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            } finally {
                conn.disconnect()
            }
        }.getOrNull()?.also { json[path] = it }
    }
}
