package com.oxoghost.hexaplayer.ui.nav

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.oxoghost.hexaplayer.repository.Period
import com.oxoghost.hexaplayer.repository.PeriodKind
import com.oxoghost.hexaplayer.ui.app.AppTools
import com.oxoghost.hexaplayer.ui.common.rememberAlbumSeed
import com.oxoghost.hexaplayer.ui.material.screens.MAppearanceScreen
import com.oxoghost.hexaplayer.ui.material.screens.MDetailScreen
import com.oxoghost.hexaplayer.ui.material.screens.MDownloadScreen
import com.oxoghost.hexaplayer.ui.material.screens.MEditScreen
import com.oxoghost.hexaplayer.ui.material.screens.MEqualizerScreen
import com.oxoghost.hexaplayer.ui.material.screens.MHomeScreen
import com.oxoghost.hexaplayer.ui.material.screens.MLibraryScreen
import com.oxoghost.hexaplayer.ui.material.screens.MLyricsScreen
import com.oxoghost.hexaplayer.ui.material.screens.MPlayerScreen
import com.oxoghost.hexaplayer.ui.material.screens.MQueueScreen
import com.oxoghost.hexaplayer.ui.material.screens.MSearchScreen
import com.oxoghost.hexaplayer.ui.material.screens.MSettingsPageScreen
import com.oxoghost.hexaplayer.ui.material.screens.MSettingsScreen
import com.oxoghost.hexaplayer.ui.material.screens.MSetupScreen
import com.oxoghost.hexaplayer.ui.material.screens.MWrappedScreen
import com.oxoghost.hexaplayer.ui.material.theme.HexaMaterialTheme
import com.oxoghost.hexaplayer.ui.settings.MaterialSource
import com.oxoghost.hexaplayer.ui.settings.Skin
import com.oxoghost.hexaplayer.ui.settings.UiSettings
import com.oxoghost.hexaplayer.ui.state.DetailKey
import com.oxoghost.hexaplayer.ui.state.HexaActions
import com.oxoghost.hexaplayer.ui.state.LibrarySection
import com.oxoghost.hexaplayer.ui.state.PlaybackState
import com.oxoghost.hexaplayer.ui.state.SettingsPage
import com.oxoghost.hexaplayer.ui.state.SortKey
import com.oxoghost.hexaplayer.ui.state.Tab
import com.oxoghost.hexaplayer.ui.state.UiModel
import com.oxoghost.hexaplayer.ui.state.WrappedActions
import com.oxoghost.hexaplayer.ui.state.rememberAudioInfo
import com.oxoghost.hexaplayer.ui.state.rememberDetailState
import com.oxoghost.hexaplayer.ui.state.rememberDownloadState
import com.oxoghost.hexaplayer.ui.state.rememberEditState
import com.oxoghost.hexaplayer.ui.state.rememberEqualizerModel
import com.oxoghost.hexaplayer.ui.state.rememberHomeState
import com.oxoghost.hexaplayer.ui.state.rememberLibraryState
import com.oxoghost.hexaplayer.ui.state.rememberLyricsState
import com.oxoghost.hexaplayer.ui.state.rememberQueueState
import com.oxoghost.hexaplayer.ui.state.rememberSearchState
import com.oxoghost.hexaplayer.ui.state.rememberSettingsState
import com.oxoghost.hexaplayer.ui.state.rememberSetupState
import com.oxoghost.hexaplayer.ui.state.rememberWrappedPeriod
import com.oxoghost.hexaplayer.ui.state.rememberWrappedState
import com.oxoghost.hexaplayer.ui.terminal.screens.TAppearanceScreen
import com.oxoghost.hexaplayer.ui.terminal.screens.TDetailScreen
import com.oxoghost.hexaplayer.ui.terminal.screens.TDownloadScreen
import com.oxoghost.hexaplayer.ui.terminal.screens.TEditScreen
import com.oxoghost.hexaplayer.ui.terminal.screens.TEqualizerScreen
import com.oxoghost.hexaplayer.ui.terminal.screens.TFlavorEditorScreen
import com.oxoghost.hexaplayer.ui.terminal.screens.THomeScreen
import com.oxoghost.hexaplayer.ui.terminal.screens.TLibraryScreen
import com.oxoghost.hexaplayer.ui.terminal.screens.TLyricsScreen
import com.oxoghost.hexaplayer.ui.terminal.screens.TPlayerScreen
import com.oxoghost.hexaplayer.ui.terminal.screens.TQueueScreen
import com.oxoghost.hexaplayer.ui.terminal.screens.TSearchScreen
import com.oxoghost.hexaplayer.ui.terminal.screens.TSettingsScreen
import com.oxoghost.hexaplayer.ui.terminal.screens.TSetupScreen
import com.oxoghost.hexaplayer.ui.terminal.screens.TWrappedScreen
import com.oxoghost.hexaplayer.ui.terminal.theme.TerminalPalettes
import com.oxoghost.hexaplayer.util.toast
import com.oxoghost.hexaplayer.viewmodel.MusicViewModel

/** One graph for both skins: each route builds its shared state, then draws it in the current skin. */
@Composable
fun HexaNavHost(
    nav: NavHostController,
    settings: UiSettings,
    vm: MusicViewModel,
    ui: UiModel,
    actions: HexaActions,
    playback: PlaybackState,
    tools: AppTools,
    modifier: Modifier = Modifier,
) {
    val terminal = settings.skin == Skin.Terminal
    // Lists don't show the position: zeroing it lets them skip the 5 position updates per second.
    val listPlayback = playback.copy(positionMs = 0L)
    val openCurrentAlbum = {
        val song = playback.song
        vm.albums.value?.firstOrNull { it.id == song?.albumId }?.let(actions::openAlbum)
        Unit
    }

    NavHost(
        nav,
        startDestination = Routes.HOME,
        modifier = modifier,
        enterTransition = { fadeIn(tween(120)) },
        exitTransition = { fadeOut(tween(120)) },
    ) {
        composable(Routes.HOME) {
            val home = rememberHomeState(vm)
            if (terminal) THomeScreen(home, listPlayback, actions)
            else MHomeScreen(home, listPlayback, actions, onSeeAllRecent = {
                ui.section = LibrarySection.Songs
                ui.sorts[LibrarySection.Songs] = SortKey.DateAdded
                actions.openTab(Tab.Library)
            })
        }
        composable(Routes.LIBRARY) {
            val lib = rememberLibraryState(vm, ui)
            if (terminal) TLibraryScreen(lib, listPlayback, actions, settings.pixelCovers)
            else MLibraryScreen(lib, listPlayback, actions)
        }
        composable(Routes.SEARCH) {
            val search = rememberSearchState(vm, ui)
            if (terminal) TSearchScreen(search, listPlayback, actions)
            else MSearchScreen(search, listPlayback, actions)
        }
        composable(Routes.DOWNLOAD) {
            val context = LocalContext.current
            val state = rememberDownloadState(context, tools.downloadVm, tools.download)
            if (terminal) TDownloadScreen(state, tools.download) else MDownloadScreen(state, tools.download)
        }
        composable(Routes.SETUP) {
            val state = rememberSetupState(LocalContext.current, tools.downloadVm)
            if (terminal) TSetupScreen(state, tools.download, actions::back) else MSetupScreen(state, tools.download, actions::back)
        }
        composable(Routes.PLAYER) {
            if (terminal) {
                TPlayerScreen(playback, actions, settings.pixelCovers, onAlbum = openCurrentAlbum)
            } else {
                val seed = rememberAlbumSeed(playback.song)
                val tint = settings.tintPlayerWithCover && seed != null
                HexaMaterialTheme(if (tint) settings.copy(materialSource = MaterialSource.AlbumArt) else settings, seed) {
                    MPlayerScreen(playback, actions, onAlbum = openCurrentAlbum)
                }
            }
        }
        composable(Routes.LYRICS) {
            val lyrics = rememberLyricsState(vm, playback)
            if (terminal) TLyricsScreen(lyrics, playback, actions, settings.pixelCovers)
            else MLyricsScreen(lyrics, playback, actions)
        }
        composable(Routes.QUEUE) {
            val queue = rememberQueueState(vm, playback)
            if (terminal) TQueueScreen(queue, actions) else MQueueScreen(queue, actions)
        }
        composable(Routes.SETTINGS) {
            val state = rememberSettingsState(settings, vm, tools.settings)
            if (terminal) {
                val eq = rememberEqualizerModel()
                TSettingsScreen(state, tools.settings, eq.state.enabled, actions::back, actions::openAppearance, actions::openEqualizer, actions::openSetup)
            } else {
                MSettingsScreen(state, tools.settings, actions::back, actions::openSettingsPage, actions::openAppearance, actions::openEqualizer)
            }
        }
        composable(Routes.SETTINGS_PAGE, listOf(navArgument("page") { type = NavType.StringType })) { entry ->
            val page = SettingsPage.entries.firstOrNull { it.name == entry.arguments?.getString("page") } ?: SettingsPage.About
            val state = rememberSettingsState(settings, vm, tools.settings)
            val download = rememberDownloadState(LocalContext.current, tools.downloadVm, tools.download)
            MSettingsPageScreen(page, state, tools.settings, download, tools.download, actions::back, actions::openSettingsPage)
        }
        composable(Routes.APPEARANCE) {
            val state = rememberSettingsState(settings, vm, tools.settings)
            if (terminal) TAppearanceScreen(state, tools.settings, actions::openFlavorEditor, actions::back)
            else MAppearanceScreen(state, tools.settings, playback, actions::back)
        }
        composable(Routes.FLAVOR, listOf(navArgument("name") { type = NavType.StringType; nullable = true; defaultValue = null })) { entry ->
            val editing = entry.arguments?.getString("name")?.takeIf { it in settings.customFlavors }
            val base = remember(editing) { TerminalPalettes.get(editing ?: settings.terminalPalette, false, settings.customFlavors) }
            TFlavorEditorScreen(
                editing = editing,
                base = base,
                existing = settings.customFlavors.keys,
                onSave = { name, colors, previous ->
                    tools.settings.saveFlavor(name, colors, previous)
                    actions.back()
                },
                onDelete = { name ->
                    tools.settings.deleteFlavor(name)
                    actions.back()
                },
                onBack = actions::back,
            )
        }
        composable(Routes.WRAPPED) {
            val context = LocalContext.current
            val (period, setPeriod) = rememberWrappedPeriod()
            val state = rememberWrappedState(vm, period)
            val wrapped = object : WrappedActions {
                override fun setKind(kind: PeriodKind) = setPeriod(Period(kind, 0))
                override fun shift(delta: Int) = setPeriod(period.copy(offset = period.offset + delta))
                override fun saveTop50() = vm.saveTopSongs(period) { name, count ->
                    context.toast("Saved $count songs to $name")
                }
            }
            if (terminal) TWrappedScreen(state, wrapped, settings.pixelCovers, actions::back)
            else MWrappedScreen(state, wrapped, actions::back)
        }
        composable(Routes.EQUALIZER) {
            val eq = rememberEqualizerModel()
            if (terminal) TEqualizerScreen(eq.state, eq, actions::back) else MEqualizerScreen(eq.state, eq, actions::back)
        }
        composable(Routes.ALBUM, listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            Detail(DetailKey.AlbumKey(entry.arguments?.getLong("id") ?: -1L), terminal, settings, vm, actions, listPlayback)
        }
        composable(Routes.ARTIST, listOf(navArgument("name") { type = NavType.StringType })) { entry ->
            Detail(DetailKey.ArtistKey(entry.arguments?.getString("name").orEmpty()), terminal, settings, vm, actions, listPlayback)
        }
        composable(Routes.PLAYLIST, listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            Detail(DetailKey.PlaylistKey(entry.arguments?.getString("id").orEmpty()), terminal, settings, vm, actions, listPlayback)
        }
        composable(Routes.EDIT, listOf(navArgument("ids") { type = NavType.StringType })) { entry ->
            val ids = remember(entry) { entry.arguments?.getString("ids").orEmpty().split(',').mapNotNull { it.toLongOrNull() } }
            val edit = rememberEditState(vm, ids)
            if (terminal) {
                TEditScreen(edit, rememberAudioInfo(vm, edit?.songs?.singleOrNull()), settings.pixelCovers, actions)
            } else {
                MEditScreen(edit, actions)
            }
        }
    }
}

@Composable
private fun Detail(
    key: DetailKey,
    terminal: Boolean,
    settings: UiSettings,
    vm: MusicViewModel,
    actions: HexaActions,
    playback: PlaybackState,
) {
    val detail = rememberDetailState(vm, key)
    if (terminal) TDetailScreen(detail, playback, actions, settings.pixelCovers, rememberAudioInfo(vm, detail?.album?.let { detail.songs.firstOrNull() }))
    else MDetailScreen(detail, playback, actions)
}
