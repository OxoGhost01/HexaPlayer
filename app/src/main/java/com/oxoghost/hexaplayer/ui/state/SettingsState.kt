package com.oxoghost.hexaplayer.ui.state

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.oxoghost.hexaplayer.BuildConfig
import com.oxoghost.hexaplayer.ui.app.SystemAccess
import com.oxoghost.hexaplayer.ui.app.UpdateCheck
import com.oxoghost.hexaplayer.ui.app.Updater
import com.oxoghost.hexaplayer.ui.settings.MaterialSource
import com.oxoghost.hexaplayer.ui.settings.Skin
import com.oxoghost.hexaplayer.ui.settings.UiSettings
import com.oxoghost.hexaplayer.ui.settings.UiSettingsRepository
import com.oxoghost.hexaplayer.ui.tokens.TerminalPaletteTokens
import com.oxoghost.hexaplayer.util.toast
import com.oxoghost.hexaplayer.viewmodel.MusicViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Sub screens of the material settings (the terminal shows everything in one config view). */
enum class SettingsPage(val title: String) {
    Library("Library"),
    Playback("Playback"),
    Download("Download & Sync"),
    Updates("Updates"),
    About("About"),
    Licenses("Licenses"),
    Permissions("Permissions"),
}

/** The 1.x library sort orders, as the terminal value and the material label. */
val librarySorts = listOf(
    "title_asc" to "Title A to Z",
    "title_desc" to "Title Z to A",
    "artist" to "Artist",
    "album" to "Album",
    "length_asc" to "Shortest first",
    "length_desc" to "Longest first",
    "added_desc" to "Newest first",
    "added_asc" to "Oldest first",
)

@Immutable
data class SettingsState(
    val ui: UiSettings,
    val version: String,
    val folder: String,
    val sortOrder: Int,
    val showUnknown: Boolean,
    val gapless: Boolean,
    val resumeOnSkip: Boolean,
    val crossfadeTenths: Int,
    val skipSilence: Boolean,
    val autoUpdate: Boolean,
    val updateCheck: UpdateCheck?,
    val latestVersion: String?,
    val scanning: Boolean,
    val songCount: Int,
) {
    val updateStatus: String
        get() = when (updateCheck) {
            UpdateCheck.Checking -> "checking..."
            UpdateCheck.Available -> "v$latestVersion available"
            UpdateCheck.Error -> "could not reach GitHub"
            UpdateCheck.UpToDate -> "up to date"
            null -> if (autoUpdate) "checked on launch" else "manual"
        }
}

interface SettingsActions {
    fun setSkin(skin: Skin)
    fun setTerminalPalette(name: String)
    fun setMaterialSource(source: MaterialSource)
    fun setMaterialPreset(name: String)
    fun setPureBlack(on: Boolean)
    fun setTintPlayer(on: Boolean)
    fun setPixelCovers(on: Boolean)
    fun saveFlavor(name: String, colors: TerminalPaletteTokens, previousName: String?)
    fun deleteFlavor(name: String)
    fun setFolder(path: String)
    fun chooseFolder()
    fun rescan()
    fun setSort(index: Int)
    fun setShowUnknown(on: Boolean)
    fun setGapless(on: Boolean)
    fun setResumeOnSkip(on: Boolean)
    fun setCrossfade(tenths: Int)
    fun setSkipSilence(on: Boolean)
    fun setAutoUpdate(on: Boolean)
    fun checkUpdates()
    fun openUpdate()
    fun openDonate()
    fun openGitHub()
    fun openAppInfo()
}

/** Settings over the 1.x preferences (MusicViewModel) and the UI settings (DataStore). */
@Stable
class SettingsModel(
    private val context: Context,
    private val vm: MusicViewModel,
    private val repo: UiSettingsRepository,
    private val updater: Updater,
    private val system: SystemAccess,
    private val scope: CoroutineScope,
) : SettingsActions {

    // The 1.x preferences are plain SharedPreferences, so a counter tells Compose they changed.
    private var revision by mutableIntStateOf(0)

    fun state(ui: UiSettings, scanning: Boolean, songCount: Int): SettingsState {
        revision
        return SettingsState(
            ui = ui,
            version = BuildConfig.VERSION_NAME,
            folder = vm.musicFolder,
            sortOrder = vm.sortOrder,
            showUnknown = vm.showUnknownSongs,
            gapless = vm.gaplessPlayback,
            resumeOnSkip = vm.resumeOnTrackChange,
            crossfadeTenths = vm.crossfadeTenths,
            skipSilence = vm.skipSilence,
            autoUpdate = updater.auto,
            updateCheck = updater.lastCheck,
            latestVersion = updater.info?.latestVersion,
            scanning = scanning,
            songCount = songCount,
        )
    }

    private fun changed() {
        revision++
    }

    override fun setSkin(skin: Skin) {
        scope.launch { repo.setSkin(skin) }
    }

    override fun setTerminalPalette(name: String) {
        scope.launch { repo.setTerminalPalette(name) }
    }

    override fun setMaterialSource(source: MaterialSource) {
        scope.launch { repo.setMaterialSource(source) }
    }

    override fun setMaterialPreset(name: String) {
        scope.launch {
            repo.setMaterialSource(MaterialSource.Preset)
            repo.setMaterialPreset(name)
        }
    }

    override fun setPureBlack(on: Boolean) {
        scope.launch { repo.setPureBlack(on) }
    }

    override fun setTintPlayer(on: Boolean) {
        scope.launch { repo.setTintPlayerWithCover(on) }
    }

    override fun setPixelCovers(on: Boolean) {
        scope.launch { repo.setPixelCovers(on) }
    }

    override fun saveFlavor(name: String, colors: TerminalPaletteTokens, previousName: String?) {
        scope.launch { repo.saveFlavor(name, colors, previousName) }
    }

    override fun deleteFlavor(name: String) {
        scope.launch { repo.deleteFlavor(name) }
    }

    override fun setFolder(path: String) {
        vm.musicFolder = path.trim().trim('/')
        changed()
        vm.loadLibrary()
    }

    override fun chooseFolder() = system.pickFolder { setFolder(it) }

    override fun rescan() = vm.loadLibrary()

    override fun setSort(index: Int) {
        if (vm.sortOrder == index) return
        vm.sortOrder = index
        changed()
        vm.loadLibrary()
    }

    override fun setShowUnknown(on: Boolean) {
        vm.showUnknownSongs = on
        changed()
        vm.loadLibrary()
    }

    override fun setGapless(on: Boolean) {
        vm.gaplessPlayback = on
        changed()
    }

    override fun setResumeOnSkip(on: Boolean) {
        vm.resumeOnTrackChange = on
        changed()
    }

    override fun setCrossfade(tenths: Int) {
        vm.crossfadeTenths = tenths.coerceIn(0, 50)
        changed()
    }

    override fun setSkipSilence(on: Boolean) {
        vm.skipSilence = on
        changed()
    }

    override fun setAutoUpdate(on: Boolean) = updater.setAutoCheck(on)
    override fun checkUpdates() = updater.check(showDialog = true)
    override fun openUpdate() = updater.showDialog()
    override fun openDonate() = open("https://github.com/sponsors/OxoGhost01")
    override fun openGitHub() = open("https://github.com/OxoGhost01/HexaPlayer")

    override fun openAppInfo() {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private fun open(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            context.toast(url, long = true)
        }
    }
}

@Composable
fun rememberSettingsState(ui: UiSettings, vm: MusicViewModel, model: SettingsModel): SettingsState {
    val loading by vm.isLoading.observeAsState(false)
    val songs by vm.songs.observeAsState(emptyList())
    return model.state(ui, loading, songs.size)
}

/** Open source libraries shipped in the app, with their licenses. */
val licenses = listOf(
    "Jetpack Compose, AndroidX" to "Apache 2.0",
    "Media3 ExoPlayer" to "Apache 2.0",
    "Kotlin coroutines" to "Apache 2.0",
    "Coil" to "Apache 2.0",
    "Gson" to "Apache 2.0",
    "Material Color Utilities (materialkolor)" to "Apache 2.0",
    "TagLib for Android (Kyant0)" to "Apache 2.0",
    "TagLib" to "LGPL 2.1 / MPL 1.1",
    "Material Symbols" to "Apache 2.0",
    "JetBrains Mono" to "SIL OFL 1.1",
    "LRCLIB (lyrics service)" to "free API",
    "spotDL (runs in Termux)" to "MIT",
)

/** What each permission is for, as in the README. */
val permissionNotes = listOf(
    Triple("Music and audio", "Find and play the songs on your phone", "First launch"),
    Triple("Notifications", "Playback controls and update alerts", "First launch"),
    Triple("Run in the background", "Keep the music playing with the screen off", "Granted automatically"),
    Triple("Internet", "Lyrics from LRCLIB, update checks, the downloader on your phone", "Granted automatically"),
    Triple("Install apps", "Install HexaPlayer updates from the app", "When you tap Install"),
    Triple("Run commands in Termux", "Start the downloader for you", "If you use Download & Sync"),
    Triple("Edit audio files", "Write the tags you change into the file", "Each time you save"),
)
