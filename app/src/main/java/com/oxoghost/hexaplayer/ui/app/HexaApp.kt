package com.oxoghost.hexaplayer.ui.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.oxoghost.hexaplayer.ui.common.rememberAlbumSeed
import com.oxoghost.hexaplayer.ui.legacy.LegacyDialogs
import com.oxoghost.hexaplayer.ui.material.screens.LocalChromePadding
import com.oxoghost.hexaplayer.ui.material.screens.MAppChrome
import com.oxoghost.hexaplayer.ui.material.screens.chromePadding
import com.oxoghost.hexaplayer.ui.nav.HexaNavHost
import com.oxoghost.hexaplayer.ui.settings.MaterialSource
import com.oxoghost.hexaplayer.ui.settings.Skin
import com.oxoghost.hexaplayer.ui.settings.UiSettings
import com.oxoghost.hexaplayer.ui.state.UiModel
import com.oxoghost.hexaplayer.ui.state.rememberPlaybackState
import com.oxoghost.hexaplayer.ui.terminal.screens.TAppChrome
import com.oxoghost.hexaplayer.ui.terminal.theme.TerminalTheme
import com.oxoghost.hexaplayer.viewmodel.MusicViewModel

/**
 * Root of the 2.0 UI. The structure is the same for both skins (the NavHost never moves),
 * so switching skin or palette recomposes in place and keeps the current screen.
 */
@Composable
fun HexaApp(
    settings: UiSettings,
    vm: MusicViewModel,
    ui: UiModel,
    dialogs: LegacyDialogs,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
) {
    val playback = rememberPlaybackState(vm)
    val seed = rememberAlbumSeed(playback.song.takeIf { settings.materialSource == MaterialSource.AlbumArt })
    val terminal = settings.skin == Skin.Terminal

    HexaTheme(settings, seed) {
        val background = if (terminal) TerminalTheme.colors.background else MaterialTheme.colorScheme.surface
        if (!hasPermission) {
            PermissionGate(terminal, onRequestPermission, Modifier.fillMaxSize().background(background))
            return@HexaTheme
        }
        val context = LocalContext.current
        val nav = rememberNavController()
        val actions = remember(nav) { AppActions(context, vm, ui, nav, dialogs) }
        val route = nav.currentBackStackEntryAsState().value?.destination?.route

        Column(
            Modifier
                .fillMaxSize()
                .background(background)
                .then(if (terminal) Modifier.systemBarsPadding() else Modifier)
        ) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                CompositionLocalProvider(LocalChromePadding provides if (terminal) 0.dp else chromePadding(route, playback)) {
                    HexaNavHost(nav, settings, vm, ui, actions, playback, Modifier.fillMaxSize())
                }
                if (!terminal) MAppChrome(route, playback, actions, Modifier.align(Alignment.BottomCenter))
            }
            if (terminal) TAppChrome(route, playback, actions)
        }
        FragmentOverlayBack()
    }
}


/**
 * Screens still shown as Fragments over the app (Termux guide, update) are closed by back
 * before the navigation gets it.
 */
@Composable
private fun FragmentOverlayBack() {
    val activity = LocalContext.current as? FragmentActivity ?: return
    val fm = activity.supportFragmentManager
    var depth by remember { mutableIntStateOf(fm.backStackEntryCount) }
    DisposableEffect(fm) {
        val listener = FragmentManager.OnBackStackChangedListener { depth = fm.backStackEntryCount }
        fm.addOnBackStackChangedListener(listener)
        onDispose { fm.removeOnBackStackChangedListener(listener) }
    }
    BackHandler(enabled = depth > 0) { fm.popBackStack() }
}
