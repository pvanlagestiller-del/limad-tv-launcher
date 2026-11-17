package de.limad.tvlauncher

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.Inet4Address

class MainActivity : ComponentActivity(), Actions {

    private val ui = UiState()
    private lateinit var favStore: Favorites
    private lateinit var user: UserStore
    private var allApps: List<AppEntry> = emptyList()
    private var lastInput = SystemClock.elapsedRealtime()
    private var sleepJob: Job? = null
    private var askedProfile = false

    override val ctx: Context get() = this

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        favStore = Favorites(this)
        user = UserStore(this)
        ui.paletteIndex = user.paletteIndex
        ui.saverMode = user.saverMode
        ui.saverMinutes = user.saverMinutes
        loadProfiles()
        handleIntent(intent)

        // Lowest-priority back handler: a launcher never closes itself.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })

        setContent { LimadRoot(ui, this) }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    refreshWeather()
                    delay(30 * 60 * 1000L)
                }
            }
        }
        // Idle watcher → screensaver.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    delay(15_000)
                    ui.wifi = networkInfo()
                    val idle = SystemClock.elapsedRealtime() - lastInput
                    if (!ui.screensaver && !ui.dnd && ui.saverMode != 2 && idle > ui.saverMinutes * 60_000L) startSaver()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
        if (intent.getStringExtra("tmdb_token") == null) {
            // Home pressed: back to Start.
            ui.detail = null
            ui.settingsOpen = false
            ui.controlOpen = false
            ui.screensaver = false
            ui.tab = Tab.START
        }
    }

    override fun onResume() {
        super.onResume()
        lastInput = SystemClock.elapsedRealtime()
        ui.screensaver = false
        ui.wifi = networkInfo()
        reload()
        if (!askedProfile && user.askProfileOnStart && ui.profiles.size > 1) ui.profilePicker = true
        askedProfile = true
    }

    /** Every key resets the idle timer; any key ends the screensaver; ☰ opens the control center. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        lastInput = SystemClock.elapsedRealtime()
        if (ui.screensaver) {
            if (event.action == KeyEvent.ACTION_UP) ui.screensaver = false
            return true
        }
        if (event.keyCode == KeyEvent.KEYCODE_MENU && event.action == KeyEvent.ACTION_UP) {
            if (ui.controlOpen) closeOverlays() else openControl()
            return true
        }
        if (event.keyCode == KeyEvent.KEYCODE_MENU) return true
        return super.dispatchKeyEvent(event)
    }

    /** Setup wizard sends the TMDB token: am start -n de.limad.tvlauncher/.MainActivity --es tmdb_token … */
    private fun handleIntent(intent: Intent?) {
        val token = intent?.getStringExtra("tmdb_token")?.trim()
        if (!token.isNullOrEmpty()) {
            Tmdb.saveToken(this, token)
            Toast.makeText(this, "Titelbilder aktiviert", Toast.LENGTH_SHORT).show()
            reload()
        }
    }

    private fun loadProfiles() {
        ui.profiles = user.profiles()
        ui.currentProfile = user.current()
    }

    private fun reload() {
        val all = AppRepository.load(this)
        allApps = all
        val hidden = favStore.hidden(all)
        val visible = all.filter { it.pkg !in hidden }
        ui.apps = visible
        val byPkg = visible.associateBy { it.pkg }
        ui.favorites = favStore.get(visible).mapNotNull { byPkg[it] }
        ui.settingsTick++
        lifecycleScope.launch {
            val ordered = ui.favorites + visible.filter { v -> ui.favorites.none { it.pkg == v.pkg } }
            val found = ArrayList<Pair<AppEntry, Int>>()
            for (app in ordered) {
                val id = Tmdb.providerId(this@MainActivity, app.label) ?: continue
                if (found.none { it.second == id }) found += app to id
            }
            if (found.map { it.second } != ui.services.map { it.second }) ui.services = found
        }
    }

    private suspend fun refreshWeather() {
        val place = Weather.savedPlace(this)
        ui.weather = if (place == null) "" else runCatching { "${Weather.current(place)}  ${place.name}" }.getOrElse { place.name }
        ui.settingsTick++
    }

    private fun start(intent: Intent, fallback: Intent? = null) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            if (fallback != null) start(fallback) else toast("Konnte nicht geöffnet werden")
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    // ------------------------------------------------------------------ Apps

    override fun openApp(app: AppEntry) {
        favStore.lastOpened = app.pkg
        start(Intent(app.intent))
    }

    override fun openStore() {
        val store = packageManager.getLeanbackLaunchIntentForPackage("com.amazon.venezia")
            ?: packageManager.getLaunchIntentForPackage("com.amazon.venezia")
        if (store != null) start(store) else start(Intent(Intent.ACTION_VIEW, Uri.parse("amzn://apps/android?s=")))
    }

    override fun openMeteo() {
        if (Weather.savedPlace(this) == null) askPlace() else start(Intent(this, MeteoActivity::class.java))
    }

    override fun appMenu(app: AppEntry) {
        val favs = ui.favorites.map { it.pkg }.toMutableList()
        val isFav = app.pkg in favs
        val options = if (isFav) {
            arrayOf("Nach links", "Nach rechts", "Aus Favoriten entfernen", "Ausblenden", "App-Info")
        } else {
            arrayOf("Zu Favoriten hinzufügen", "Ausblenden", "App-Info")
        }
        AlertDialog.Builder(this)
            .setTitle(app.label)
            .setItems(options) { _, which ->
                val i = favs.indexOf(app.pkg)
                when (options[which]) {
                    "Nach links" -> if (i > 0) { favs.removeAt(i); favs.add(i - 1, app.pkg) }
                    "Nach rechts" -> if (i in 0 until favs.size - 1) { favs.removeAt(i); favs.add(i + 1, app.pkg) }
                    "Aus Favoriten entfernen" -> favs.remove(app.pkg)
                    "Zu Favoriten hinzufügen" -> favs.add(app.pkg)
                    "Ausblenden" -> {
                        favStore.setHidden(favStore.hidden(allApps) + app.pkg)
                        favs.remove(app.pkg)
                    }
                    "App-Info" -> {
                        start(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.pkg}")))
                        return@setItems
                    }
                }
                favStore.set(favs)
                reload()
            }
            .show()
    }

    private fun manageApps() {
        val all = allApps
        val hidden = favStore.hidden(all)
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

    // ------------------------------------------------------------------ Titles

    override fun openDetail(t: Title) {
        ui.detail = t
    }

    /** Opens the app of a service that has the title; prefers the service it was found on. */
    override fun play(t: Title, providerIds: List<Int>) {
        lifecycleScope.launch {
            val ids = providerIds.ifEmpty { Tmdb.details(this@MainActivity, t)?.providerIds ?: emptyList() }
            val app = ui.services.firstOrNull { it.first.label == t.service && (ids.isEmpty() || it.second in ids) }?.first
                ?: ui.services.firstOrNull { it.second in ids }?.first
                ?: ui.services.firstOrNull { it.first.label == t.service }?.first
            if (app != null) {
                toast("„${t.title}“ in ${app.label} öffnen …")
                openApp(app)
            } else {
                toast("„${t.title}“ ist in keiner deiner Apps verfügbar")
            }
        }
    }

    override fun toggleWatch(t: Title): Boolean = user.toggleWatch(t).also { ui.watchTick++ }
    override fun inWatch(t: Title) = user.inWatchlist(t)
    override fun toggleLike(t: Title) = user.toggleLike(t)
    override fun liked(t: Title) = user.liked(t)
    override fun watchlist() = user.watchlist()

    override fun askSearch() {
        val input = EditText(this).apply {
            hint = "Film, Serie oder App"
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setText(ui.query ?: "")
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Suchen")
            .setView(input)
            .setPositiveButton("Suchen") { _, _ -> runSearch(input.text.toString()) }
            .setNegativeButton("Abbrechen", null)
            .create()
        input.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_SEARCH) {
                dialog.dismiss()
                runSearch(input.text.toString())
                true
            } else {
                false
            }
        }
        dialog.show()
    }

    private fun runSearch(q: String) {
        if (q.isBlank()) return
        ui.query = q.trim()
        ui.tab = Tab.SUCHE
    }

    override fun bell() {
        lifecycleScope.launch {
            val list = Tmdb.trending(this@MainActivity, "all").take(8)
            if (list.isEmpty()) {
                toast("Keine neuen Mitteilungen")
                return@launch
            }
            AlertDialog.Builder(this@MainActivity)
                .setTitle("Neu & beliebt heute")
                .setItems(list.map { "${it.title}  ·  ${if (it.type == "tv") "Serie" else "Film"}" }.toTypedArray()) { _, i -> openDetail(list[i]) }
                .show()
        }
    }

    // ------------------------------------------------------------------ Navigation

    override fun selectTab(t: Tab) {
        ui.detail = null
        ui.tab = t
        if (t == Tab.SUCHE && ui.query == null) askSearch()
    }

    override fun back() {
        when {
            ui.controlOpen -> ui.controlOpen = false
            ui.profilePicker -> ui.profilePicker = false
            ui.settingsOpen -> ui.settingsOpen = false
            ui.detail != null -> ui.detail = null
            ui.tab != Tab.START -> ui.tab = Tab.START
        }
    }

    override fun openSettings(section: Int) {
        ui.controlOpen = false
        ui.settingsSection = section
        ui.settingsOpen = true
    }

    override fun version(): String =
        runCatching { packageManager.getPackageInfo(packageName, 0).versionName ?: "" }.getOrDefault("")

    // ------------------------------------------------------------------ Control center

    override fun openControl() {
        ui.nowPlaying = Media.nowPlaying(this)
        ui.controlOpen = true
    }

    override fun closeOverlays() {
        ui.controlOpen = false
    }

    /** No app may put Fire OS to sleep; "Standby" rests the screen with the screensaver. */
    override fun standby() {
        ui.controlOpen = false
        startSaver()
        toast("Echter Standby: Home-Taste lange drücken → Ruhezustand")
    }

    private fun startSaver() {
        if (ui.saverMode == 1) {
            lifecycleScope.launch {
                val urls = (user.watchlist() + Tmdb.trending(this@MainActivity, "all")).mapNotNull { it.backdropUrl("w1280") }.distinct().take(12)
                ui.saverUrls = urls
                ui.screensaver = true
            }
        } else {
            ui.saverUrls = emptyList()
            ui.screensaver = true
        }
    }

    override fun openWifi() = start(Intent(Settings.ACTION_WIFI_SETTINGS), Intent(Settings.ACTION_SETTINGS))

    override fun toggleDnd() {
        ui.dnd = !ui.dnd
    }

    /** Sleep timer: pauses playback and starts the screensaver when it runs out. */
    override fun cycleSleep() {
        val steps = listOf(0, 15, 30, 60, 90)
        ui.sleepMinutes = steps[(steps.indexOf(ui.sleepMinutes) + 1) % steps.size]
        sleepJob?.cancel()
        if (ui.sleepMinutes > 0) {
            val minutes = ui.sleepMinutes
            sleepJob = lifecycleScope.launch {
                delay(minutes * 60_000L)
                Media.key(this@MainActivity, KeyEvent.KEYCODE_MEDIA_PAUSE)
                ui.sleepMinutes = 0
                start(Intent(this@MainActivity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                startSaver()
            }
        }
    }

    override fun openSound() = start(Intent(Settings.ACTION_SOUND_SETTINGS), Intent(Settings.ACTION_SETTINGS))

    override fun openAccessibility() = start(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), Intent(Settings.ACTION_SETTINGS))

    override fun media(keyCode: Int) {
        Media.key(this, keyCode)
        lifecycleScope.launch {
            delay(600)
            refreshNowPlaying()
        }
    }

    override fun refreshNowPlaying() {
        ui.nowPlaying = Media.nowPlaying(this)
    }

    // ------------------------------------------------------------------ Profiles & screensaver

    override fun openProfiles() {
        loadProfiles()
        ui.controlOpen = false
        ui.profilePicker = true
    }

    override fun selectProfile(p: Profile) {
        user.currentId = p.id
        loadProfiles()
        ui.watchTick++
        ui.profilePicker = false
        ui.tab = Tab.START
    }

    override fun addProfile() {
        askText("Neues Profil", "") { name ->
            val p = user.addProfile(name)
            loadProfiles()
            selectProfile(p)
        }
    }

    override fun profileMenu(p: Profile) {
        val options = if (ui.profiles.size > 1) arrayOf("Umbenennen", "Löschen") else arrayOf("Umbenennen")
        AlertDialog.Builder(this)
            .setTitle(p.name)
            .setItems(options) { _, which ->
                when (options[which]) {
                    "Umbenennen" -> askText("Name", p.name) { user.renameProfile(p.id, it); loadProfiles() }
                    "Löschen" -> AlertDialog.Builder(this)
                        .setMessage("Profil „${p.name}“ mit Merkliste löschen?")
                        .setPositiveButton("Löschen") { _, _ -> user.deleteProfile(p.id); loadProfiles(); ui.watchTick++ }
                        .setNegativeButton("Abbrechen", null)
                        .show()
                }
            }
            .show()
    }

    override fun setSaverMode(mode: Int) {
        ui.saverMode = mode
        user.saverMode = mode
        ui.settingsTick++
    }

    override fun cycleSaverMinutes() {
        val steps = listOf(2, 5, 10, 15, 30)
        ui.saverMinutes = steps[(steps.indexOf(ui.saverMinutes) + 1) % steps.size]
        user.saverMinutes = ui.saverMinutes
        ui.settingsTick++
    }

    // ------------------------------------------------------------------ Settings

    private val saverModes = listOf("Landschaften", "Titelbilder", "Aus")

    override fun settingRows(section: Int): List<SettingItem> = when (section) {
        0 -> listOf(
            SettingItem("lang", "Sprache", "Deutsch"),
            SettingItem("design", "Oberflächendesign", Palettes[ui.paletteIndex].name),
            SettingItem("home", "Startbildschirm anpassen", "Apps ein-/ausblenden"),
            SettingItem("place", "Ort für Wetter", Weather.savedPlace(this)?.name ?: "Nicht festgelegt"),
        )
        1 -> listOf(
            SettingItem("display", "Bild & Auflösung"),
            SettingItem("sound", "Ton & Audioausgabe"),
            SettingItem("dream", "Fire-TV-Bildschirmschoner"),
        )
        2 -> listOf(
            SettingItem("none", "Status", networkInfo(), chevron = false),
            SettingItem("wifi", "WLAN-Einstellungen"),
        )
        3 -> listOf(
            SettingItem("manage", "Apps verwalten", "${ui.apps.size} sichtbar"),
            SettingItem("tmdb", "Titelbilder (TMDB)", if (Tmdb.hasToken(this)) "Aktiv" else "Nicht eingerichtet"),
            SettingItem("store", "App hinzufügen", "Appstore"),
            SettingItem("fireapps", "Alle Apps verwalten (Fire OS)"),
        )
        4 -> listOf(
            SettingItem("profiles", "Profil wechseln", ui.currentProfile.name),
            SettingItem("rename", "Profil umbenennen", ui.currentProfile.name),
            SettingItem("ask_profile", "Profilauswahl beim Start", if (user.askProfileOnStart) "An" else "Aus"),
            SettingItem("clear_watch", "Merkliste leeren", "${user.watchlist().size} Titel"),
            SettingItem("clear_likes", "Bewertungen zurücksetzen"),
        )
        5 -> listOf(
            SettingItem("saver_mode", "Motiv", saverModes[ui.saverMode]),
            SettingItem("saver_minutes", "Startet nach", "${ui.saverMinutes} Minuten"),
            SettingItem("saver_now", "Jetzt starten"),
            SettingItem("none", "Als Fire-TV-Bildschirmschoner", "über „LiMaD TV Einrichtung“", chevron = false),
        )
        else -> listOf(
            SettingItem("none", "Gerät", Build.MODEL, chevron = false),
            SettingItem("none", "Fire OS", (Build.DISPLAY ?: "") + " · Android " + Build.VERSION.RELEASE, chevron = false),
            SettingItem("none", "LiMaD-TV", version(), chevron = false),
            SettingItem("updates", "System-Updates"),
            SettingItem("fire", "Alle Fire-TV-Einstellungen"),
        )
    }

    override fun setting(id: String) {
        val fire = Intent(Settings.ACTION_SETTINGS)
        when (id) {
            "lang" -> start(Intent(Settings.ACTION_LOCALE_SETTINGS), fire)
            "design" -> {
                ui.paletteIndex = (ui.paletteIndex + 1) % Palettes.size
                user.paletteIndex = ui.paletteIndex
            }
            "home", "manage" -> manageApps()
            "place" -> askPlace()
            "display" -> start(Intent(Settings.ACTION_DISPLAY_SETTINGS), fire)
            "sound" -> openSound()
            "dream" -> start(Intent(Settings.ACTION_DREAM_SETTINGS), fire)
            "wifi" -> openWifi()
            "tmdb" -> toast(if (Tmdb.hasToken(this)) "TMDB ist aktiv" else "Token über „LiMaD TV Einrichtung“ am Rechner senden")
            "store" -> openStore()
            "fireapps" -> start(Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS), fire)
            "profiles" -> { ui.settingsOpen = false; openProfiles() }
            "rename" -> askText("Profilname", ui.currentProfile.name) { user.renameProfile(ui.currentProfile.id, it); loadProfiles() }
            "ask_profile" -> user.askProfileOnStart = !user.askProfileOnStart
            "clear_watch" -> { user.clearWatchlist(); ui.watchTick++; toast("Merkliste geleert") }
            "clear_likes" -> { user.clearLikes(); toast("Bewertungen zurückgesetzt") }
            "saver_mode" -> setSaverMode((ui.saverMode + 1) % saverModes.size)
            "saver_minutes" -> cycleSaverMinutes()
            "saver_now" -> { ui.settingsOpen = false; startSaver() }
            "updates" -> start(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS), fire)
            "fire" -> start(fire)
        }
        ui.settingsTick++
    }

    private fun networkInfo(): String {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return "Unbekannt"
        val net = cm.activeNetwork ?: return "Nicht verbunden"
        val ip = cm.getLinkProperties(net)?.linkAddresses?.map { it.address }?.firstOrNull { it is Inet4Address }?.hostAddress
        return if (ip != null) "Verbunden · $ip" else "Verbunden"
    }

    private fun askText(title: String, value: String, onOk: (String) -> Unit) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setText(value)
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setPositiveButton("Speichern") { _, _ -> onOk(input.text.toString()); ui.settingsTick++ }
            .setNegativeButton("Abbrechen", null)
            .show()
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
                        toast("Ort nicht gefunden")
                    } else {
                        Weather.savePlace(this@MainActivity, place)
                        refreshWeather()
                    }
                }
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }
}
