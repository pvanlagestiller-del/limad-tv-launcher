package de.limad.tvlauncher

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.roundToInt

data class Place(val name: String, val lat: Double, val lon: Double)

/** Current weather from DWD data via Bright Sky, places via Open-Meteo geocoding. */
object Weather {

    private const val PREFS = "limad_tv"

    fun savedPlace(ctx: Context): Place? {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val name = p.getString("place_name", null) ?: return null
        return Place(name, p.getFloat("place_lat", 0f).toDouble(), p.getFloat("place_lon", 0f).toDouble())
    }

    fun savePlace(ctx: Context, place: Place) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("place_name", place.name)
            .putFloat("place_lat", place.lat.toFloat())
            .putFloat("place_lon", place.lon.toFloat())
            .apply()
    }

    suspend fun geocode(query: String): Place? = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val json = get("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=1&language=de&countryCode=DE")
        val results = json.optJSONArray("results") ?: return@withContext null
        if (results.length() == 0) return@withContext null
        val r = results.getJSONObject(0)
        Place(r.getString("name"), r.getDouble("latitude"), r.getDouble("longitude"))
    }

    /** Returns e.g. "⛅ 7°" */
    suspend fun current(place: Place): String = withContext(Dispatchers.IO) {
        val json = get("https://api.brightsky.dev/current_weather?lat=${place.lat}&lon=${place.lon}")
        val w = json.getJSONObject("weather")
        val temp = w.optDouble("temperature", Double.NaN)
        val icon = symbol(w.optString("icon", ""))
        if (temp.isNaN()) icon else "$icon ${temp.roundToInt()}°"
    }

    private fun symbol(icon: String): String = when {
        icon.startsWith("clear-day") -> "☀"
        icon.startsWith("clear-night") -> "☾"
        icon.startsWith("partly-cloudy") -> "⛅"
        icon == "cloudy" -> "☁"
        icon == "fog" -> "≋"
        icon == "wind" -> "༄"
        icon == "rain" -> "☂"
        icon == "sleet" || icon == "hail" -> "☂"
        icon == "snow" -> "❄"
        icon == "thunderstorm" -> "⚡"
        else -> "☁"
    }

    private fun get(url: String): JSONObject {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.setRequestProperty("User-Agent", "LiMaD-TV/1.1")
        try {
            return JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }
}
