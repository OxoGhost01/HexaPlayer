package com.oxoghost.hexaplayer.ui.nav

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.fragment.compose.AndroidFragment
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.oxoghost.hexaplayer.ui.common.rememberAlbumSeed
import com.oxoghost.hexaplayer.ui.download.DownloadFragment
import com.oxoghost.hexaplayer.ui.equalizer.EqualizerFragment
import com.oxoghost.hexaplayer.ui.material.screens.MDetailScreen
import com.oxoghost.hexaplayer.ui.material.screens.MEditScreen
import com.oxoghost.hexaplayer.ui.material.screens.MHomeScreen
import com.oxoghost.hexaplayer.ui.material.screens.MHostedScreen
import com.oxoghost.hexaplayer.ui.material.screens.MLibraryScreen
import com.oxoghost.hexaplayer.ui.material.screens.MLyricsScreen
import com.oxoghost.hexaplayer.ui.material.screens.MPlayerScreen
import com.oxoghost.hexaplayer.ui.material.screens.MQueueScreen
import com.oxoghost.hexaplayer.ui.material.screens.MSearchScreen
import com.oxoghost.hexaplayer.ui.material.theme.HexaMaterialTheme
import com.oxoghost.hexaplayer.ui.settings.MaterialSource
import com.oxoghost.hexaplayer.ui.settings.SettingsFragment
import com.oxoghost.hexaplayer.ui.settings.Skin
import com.oxoghost.hexaplayer.ui.settings.UiSettings
import com.oxoghost.hexaplayer.ui.state.DetailKey
import com.oxoghost.hexaplayer.ui.state.HexaActions
import com.oxoghost.hexaplayer.ui.state.LibrarySection
import com.oxoghost.hexaplayer.ui.state.PlaybackState
import com.oxoghost.hexaplayer.ui.state.SortKey
import com.oxoghost.hexaplayer.ui.state.Tab
import com.oxoghost.hexaplayer.ui.state.UiModel
import com.oxoghost.hexaplayer.ui.state.rememberAudioInfo
import com.oxoghost.hexaplayer.ui.state.rememberDetailState
import com.oxoghost.hexaplayer.ui.state.rememberEditState
import com.oxoghost.hexaplayer.ui.state.rememberHomeState
import com.oxoghost.hexaplayer.ui.state.rememberLibraryState
import com.oxoghost.hexaplayer.ui.state.rememberLyricsState
import com.oxoghost.hexaplayer.ui.state.rememberQueueState
import com.oxoghost.hexaplayer.ui.state.rememberSearchState
import com.oxoghost.hexaplayer.ui.terminal.components.TMode
import com.oxoghost.hexaplayer.ui.terminal.screens.TDetailScreen
import com.oxoghost.hexaplayer.ui.terminal.screens.TEditScreen
import com.oxoghost.hexaplayer.ui.terminal.screens.THomeScreen
import com.oxoghost.hexaplayer.ui.terminal.screens.THostedScreen
import com.oxoghost.hexaplayer.ui.terminal.screens.TLibraryScreen
import com.oxoghost.hexaplayer.ui.terminal.screens.TLyricsScreen
import com.oxoghost.hexaplayer.ui.terminal.screens.TPlayerScreen
import com.oxoghost.hexaplayer.ui.terminal.screens.TQueueScreen
import com.oxoghost.hexaplayer.ui.terminal.screens.TSearchScreen
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
            if (terminal) {
                THostedScreen("~/dl", TMode.DL, "spotdl via termux", onBack = null, onSettings = actions::openSettings) {
                    AndroidFragment<DownloadFragment>(Modifier.fillMaxSize())
                }
            } else {
                MHostedScreen(null, null) { AndroidFragment<DownloadFragment>(Modifier.fillMaxSize()) }
            }
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
            if (terminal) {
                THostedScreen("~/.config/hexa/", TMode.CFG, "settings", onBack = actions::back, onSettings = null) {
                    AndroidFragment<SettingsFragment>(Modifier.fillMaxSize())
                }
            } else {
                MHostedScreen("Settings", actions::back) { AndroidFragment<SettingsFragment>(Modifier.fillMaxSize()) }
            }
        }
        composable(Routes.EQUALIZER) {
            if (terminal) {
                THostedScreen("eq", TMode.EQ, "equalizer", onBack = actions::back, onSettings = null) {
                    AndroidFragment<EqualizerFragment>(Modifier.fillMaxSize())
                }
            } else {
                MHostedScreen("Equalizer", actions::back) { AndroidFragment<EqualizerFragment>(Modifier.fillMaxSize()) }
            }
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
