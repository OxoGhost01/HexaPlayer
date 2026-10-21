package com.oxoghost.hexaplayer.ui.material.screens

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.oxoghost.hexaplayer.ui.common.CoverImage
import com.oxoghost.hexaplayer.ui.common.albumKey
import com.oxoghost.hexaplayer.ui.common.artModel
import com.oxoghost.hexaplayer.ui.common.horizontalSwipe
import com.oxoghost.hexaplayer.ui.common.Symbols
import com.oxoghost.hexaplayer.ui.material.components.MBottomChrome
import com.oxoghost.hexaplayer.ui.material.components.MFloatingNav
import com.oxoghost.hexaplayer.ui.material.components.MMiniPlayer
import com.oxoghost.hexaplayer.ui.material.components.MNavItem
import com.oxoghost.hexaplayer.ui.nav.Routes
import com.oxoghost.hexaplayer.ui.state.HexaActions
import com.oxoghost.hexaplayer.ui.state.PlaybackState
import com.oxoghost.hexaplayer.ui.state.Tab
import com.oxoghost.hexaplayer.ui.tokens.DesignTokens.Material.Metrics

/** Bottom padding lists need so their last row can scroll above the floating chrome. */
val LocalChromePadding = compositionLocalOf { 0.dp }

private val navItems = listOf(
    MNavItem("Home", Symbols.Home),
    MNavItem("Library", Symbols.LibraryMusic),
    MNavItem("Search", Symbols.Search),
    MNavItem("Download", Symbols.Download),
)

/** Routes that show the floating mini player and nav (tabs and their detail screens). */
fun showsMaterialChrome(route: String?): Boolean = Routes.tabOf(route) != null

@Composable
fun chromePadding(route: String?, playback: PlaybackState): Dp {
    if (!showsMaterialChrome(route)) return 0.dp
    val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val mini = if (playback.song != null) (Metrics.miniPlayer + Metrics.floatingGap).dp else 0.dp
    return mini + Metrics.navBar.dp + Metrics.floatingBottomInset.dp + nav + 8.dp
}

@Composable
fun MAppChrome(route: String?, playback: PlaybackState, actions: HexaActions, modifier: Modifier = Modifier) {
    if (!showsMaterialChrome(route)) return
    val song = playback.song
    MBottomChrome(
        modifier,
        miniPlayer = song?.let {
            {
                MMiniPlayer(
                    title = it.title,
                    artist = it.artist,
                    progress = playback.fraction,
                    isPlaying = playback.isPlaying,
                    onOpen = actions::openPlayer,
                    onPlayPause = actions::togglePlayPause,
                    onNext = actions::next,
                    modifier = Modifier.horizontalSwipe(onLeft = actions::next, onRight = actions::previous),
                ) { CoverImage(it.artModel, it.albumKey, Modifier.fillMaxSize()) }
            }
        },
    ) {
        MFloatingNav(navItems, Routes.tabOf(route)?.ordinal ?: -1, { actions.openTab(Tab.entries[it]) })
    }
}

