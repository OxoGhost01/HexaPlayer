package com.oxoghost.hexaplayer.ui.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.oxoghost.hexaplayer.BuildConfig
import com.oxoghost.hexaplayer.ui.common.rememberAlbumSeed
import com.oxoghost.hexaplayer.ui.material.screens.LocalChromePadding
import com.oxoghost.hexaplayer.ui.material.screens.MAppChrome
import com.oxoghost.hexaplayer.ui.material.screens.MOnboarding
import com.oxoghost.hexaplayer.ui.material.screens.MSelectionTopBar
import com.oxoghost.hexaplayer.ui.material.screens.MUpdateDialog
import com.oxoghost.hexaplayer.ui.material.screens.chromePadding
import com.oxoghost.hexaplayer.ui.material.screens.showsMaterialChrome
import com.oxoghost.hexaplayer.ui.nav.HexaNavHost
import com.oxoghost.hexaplayer.ui.nav.Routes
import com.oxoghost.hexaplayer.ui.settings.MaterialSource
import com.oxoghost.hexaplayer.ui.settings.Skin
import com.oxoghost.hexaplayer.ui.settings.UiSettings
import com.oxoghost.hexaplayer.ui.settings.UiSettingsRepository
import com.oxoghost.hexaplayer.ui.state.DownloadModel
import com.oxoghost.hexaplayer.ui.state.LocalSelection
import com.oxoghost.hexaplayer.ui.state.OnboardingActions
import com.oxoghost.hexaplayer.ui.state.OnboardingState
import com.oxoghost.hexaplayer.ui.state.SettingsModel
import com.oxoghost.hexaplayer.ui.state.UiModel
import com.oxoghost.hexaplayer.ui.state.rememberPlaybackState
import com.oxoghost.hexaplayer.ui.state.toState
import com.oxoghost.hexaplayer.ui.terminal.screens.TAppChrome
import com.oxoghost.hexaplayer.ui.terminal.screens.TOnboarding
import com.oxoghost.hexaplayer.ui.terminal.screens.TUpdatePopup
import com.oxoghost.hexaplayer.ui.terminal.theme.TerminalTheme
import com.oxoghost.hexaplayer.util.toast
import com.oxoghost.hexaplayer.viewmodel.DownloadViewModel
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
    system: SystemAccess,
    updater: Updater,
    settingsRepo: UiSettingsRepository,
) {
    val playback = rememberPlaybackState(vm)
    val seed = rememberAlbumSeed(playback.song.takeIf { settings.materialSource == MaterialSource.AlbumArt })
    val terminal = settings.skin == Skin.Terminal

    HexaTheme(settings, seed) {
        val background = if (terminal) TerminalTheme.colors.background else MaterialTheme.colorScheme.surface
        if (!ui.onboarded || !system.hasAudio) {
            Onboarding(terminal, vm, ui, system)
            return@HexaTheme
        }
        val context = LocalContext.current
        val nav = rememberNavController()
        val actions = remember(nav) { AppActions(context, vm, ui, nav, system) }
        val route = nav.currentBackStackEntryAsState().value?.destination?.route
        val downloadVm: DownloadViewModel = viewModel()
        val scope = rememberCoroutineScope()
        val tools = remember(nav) {
            AppTools(
                downloadVm = downloadVm,
                download = DownloadModel(context, downloadVm, system, actions::openSetup),
                settings = SettingsModel(context, vm, settingsRepo, updater, system, scope),
            )
        }

        LaunchedEffect(route) {
            ui.selection.clear()
            ui.sheet = null
            if (route == Routes.DOWNLOAD || route == Routes.SETUP) downloadVm.onScreenShown()
        }
        DownloadEvents(downloadVm, vm)

        CompositionLocalProvider(LocalSelection provides ui.selection) {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(background)
                    .then(if (terminal) Modifier.systemBarsPadding() else Modifier)
            ) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    CompositionLocalProvider(LocalChromePadding provides if (terminal) 0.dp else chromePadding(route, playback)) {
                        HexaNavHost(nav, settings, vm, ui, actions, playback, tools, Modifier.fillMaxSize())
                    }
                    if (!terminal) {
                        MAppChrome(route, playback, actions, Modifier.align(Alignment.BottomCenter))
                        if (ui.selection.active && showsMaterialChrome(route)) {
                            MSelectionTopBar(ui.selection, Modifier.align(Alignment.TopCenter))
                        }
                    }
                }
                if (terminal) TAppChrome(route, playback, actions)
            }
            BackHandler(enabled = ui.selection.active) { ui.selection.clear() }
            if (terminal) {
                Box(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                    SheetHost(true, settings.pixelCovers, ui, vm, playback, actions)
                }
            } else {
                SheetHost(false, settings.pixelCovers, ui, vm, playback, actions)
            }
            if (updater.dialogVisible) {
                updater.toState()?.let { update ->
                    if (terminal) Box(Modifier.fillMaxSize().systemBarsPadding()) { TUpdatePopup(update, updater) }
                    else MUpdateDialog(update, updater)
                }
            }
        }
    }
}

/** Models of the tool screens (download, setup, settings), created once with the app. */
class AppTools(val downloadVm: DownloadViewModel, val download: DownloadModel, val settings: SettingsModel)

/** Rescans the library when the downloader added files, and shows its messages. */
@Composable
private fun DownloadEvents(downloadVm: DownloadViewModel, vm: MusicViewModel) {
    val context = LocalContext.current
    val libraryChanged by downloadVm.libraryChanged.observeAsState(false)
    val message by downloadVm.toastMessage.observeAsState()
    LaunchedEffect(libraryChanged) {
        if (libraryChanged) {
            vm.loadLibrary()
            downloadVm.consumeLibraryChanged()
        }
    }
    LaunchedEffect(message) {
        message?.let {
            context.toast(it)
            downloadVm.clearToastMessage()
        }
    }
}

@Composable
private fun Onboarding(terminal: Boolean, vm: MusicViewModel, ui: UiModel, system: SystemAccess) {
    var folder by remember { mutableStateOf(vm.musicFolder) }
    val state = OnboardingState(BuildConfig.VERSION_NAME, system.hasAudio, system.asksNotifications, system.hasNotifications, folder)
    val actions = remember {
        object : OnboardingActions {
            override fun grantAudio() = system.requestAudio()
            override fun allowNotifications() = system.requestNotifications()
            override fun chooseFolder() = system.pickFolder {
                vm.musicFolder = it
                folder = it
                if (system.hasAudio) vm.loadLibrary()
            }
            override fun start() = ui.finishOnboarding()
            override fun quit() = system.quit()
        }
    }
    if (terminal) TOnboarding(state, actions) else MOnboarding(state, actions)
}
