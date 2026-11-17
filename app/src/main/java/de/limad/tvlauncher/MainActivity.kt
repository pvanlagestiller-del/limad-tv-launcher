package de.limad.tvlauncher

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val apps = mutableStateOf<List<AppEntry>>(emptyList())
    private val favorites = mutableStateOf<List<String>>(emptyList())
    private val weather = mutableStateOf("")
    private val lastOpened = mutableStateOf<String?>(null)
    private var allApps: List<AppEntry> = emptyList()
    private lateinit var favStore: Favorites

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        favStore = Favorites(this)
        handleIntent(intent)

        // A launcher must not close on Back.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })

        setContent {
            LauncherScreen(
                apps = apps.value,
                favorites = favorites.value,
                lastOpened = lastOpened.value,
                weather = weather.value,
                onOpen = ::openApp,
                onMenu = ::showMenu,
                onSettings = ::openSettings,
                onMeteo = ::openMeteo,
                onWeatherMenu = ::askPlace,
                onManage = ::manageApps,
            )
        }

        // Refresh weather every 30 minutes while visible.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    refreshWeather()
                    delay(30 * 60 * 1000L)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Setup wizard sends the TMDB token: am start -n de.limad.tvlauncher/.MainActivity --es tmdb_token … */
    private fun handleIntent(intent: Intent?) {
        val token = intent?.getStringExtra("tmdb_token")?.trim()
        if (!token.isNullOrEmpty()) {
            Tmdb.saveToken(this, token)
            Toast.makeText(this, "Titelbilder aktiviert", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun reload() {
        val all = AppRepository.load(this)
        allApps = all
        val hidden = favStore.hidden(all)
        val visible = all.filter { it.pkg !in hidden }
        apps.value = visible
        favorites.value = favStore.get(visible)
        if (lastOpened.value == null) lastOpened.value = favStore.lastOpened ?: favorites.value.firstOrNull()
    }

    private suspend fun refreshWeather() {
        val place = Weather.savedPlace(this)
        if (place == null) {
            weather.value = "Ort festlegen"
            return
        }
        weather.value = runCatching { "${Weather.current(place)}  ${place.name}" }
            .getOrElse { place.name }
    }

    private fun start(intent: Intent) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Toast.makeText(this, "Konnte nicht geöffnet werden", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openApp(app: AppEntry) {
        favStore.lastOpened = app.pkg
        lastOpened.value = app.pkg
        start(Intent(app.intent))
    }

    /** Multi-choice list of all installed apps; checked = shown. */
    private fun manageApps() {
        val all = allApps
        val hidden = favStore.hidden(all).toMutableSet()
        val labels = all.map { it.label }.toTypedArray()
        val shown = all.map { it.pkg !in hidden }.toBooleanArray()
        AlertDialog.Builder(this)
            .setTitle("Angezeigte Apps")
            .setMultiChoiceItems(labels, shown) { _, which, checked -> shown[which] = checked }
            .setPositiveButton("Speichern") { _, _ ->
                favStore.setHidden(all.filterIndexed { i, _ -> !shown[i] }.map { it.pkg }.toSet())
                reload()
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun openSettings() = start(Intent(Settings.ACTION_SETTINGS))

    private fun openMeteo() {
        if (Weather.savedPlace(this) == null) askPlace() else start(Intent(this, MeteoActivity::class.java))
    }

    private fun askPlace() {
        val input = EditText(this).apply {
            hint = "Stadt oder PLZ"
            inputType = InputType.TYPE_CLASS_TEXT
            setText(Weather.savedPlace(this@MainActivity)?.name ?: "")
        }
        AlertDialog.Builder(this)
            .setTitle("Ort für das Wetter")
            .setView(input)
            .setPositiveButton("Speichern") { _, _ ->
                val query = input.text.toString()
                if (query.isBlank()) return@setPositiveButton
                lifecycleScope.launch {
                    val place = runCatching { Weather.geocode(query) }.getOrNull()
                    if (place == null) {
                        Toast.makeText(this@MainActivity, "Ort nicht gefunden", Toast.LENGTH_SHORT).show()
                    } else {
                        Weather.savePlace(this@MainActivity, place)
                        refreshWeather()
                    }
                }
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun showMenu(app: AppEntry) {
        val favs = favorites.value.toMutableList()
        val isFav = app.pkg in favs
        val options = if (isFav) {
            arrayOf(MOVE_LEFT, MOVE_RIGHT, REMOVE_FAV, HIDE, APP_INFO)
        } else {
            arrayOf(ADD_FAV, HIDE, APP_INFO)
        }

        AlertDialog.Builder(this)
            .setTitle(app.label)
            .setItems(options) { _, which ->
                val i = favs.indexOf(app.pkg)
                when (options[which]) {
                    MOVE_LEFT -> if (i > 0) { favs.removeAt(i); favs.add(i - 1, app.pkg) }
                    MOVE_RIGHT -> if (i in 0 until favs.size - 1) { favs.removeAt(i); favs.add(i + 1, app.pkg) }
                    REMOVE_FAV -> favs.remove(app.pkg)
                    ADD_FAV -> favs.add(app.pkg)
                    HIDE -> {
                        favStore.setHidden(favStore.hidden(allApps) + app.pkg)
                        favs.remove(app.pkg)
                        favStore.set(favs)
                        reload()
                        return@setItems
                    }
                    APP_INFO -> {
                        start(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.pkg}")))
                        return@setItems
                    }
                }
                favStore.set(favs)
                favorites.value = favs
            }
            .show()
    }

    private companion object {
        const val MOVE_LEFT = "Nach links"
        const val MOVE_RIGHT = "Nach rechts"
        const val REMOVE_FAV = "Aus Favoriten entfernen"
        const val ADD_FAV = "Zu Favoriten hinzufügen"
        const val APP_INFO = "App-Info"
        const val HIDE = "Ausblenden"
    }
}
