package com.oxoghost.hexaplayer.ui.terminal.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.oxoghost.hexaplayer.ui.common.horizontalSwipe
import com.oxoghost.hexaplayer.ui.nav.Routes
import com.oxoghost.hexaplayer.ui.state.HexaActions
import com.oxoghost.hexaplayer.ui.state.PlaybackState
import com.oxoghost.hexaplayer.ui.state.Tab
import com.oxoghost.hexaplayer.ui.terminal.components.TKey
import com.oxoghost.hexaplayer.ui.terminal.components.TKeyBar
import com.oxoghost.hexaplayer.ui.terminal.components.TMiniLine
import com.oxoghost.hexaplayer.ui.terminal.theme.TerminalTheme

/** Terminal screen frame: header, content, status line (chrome below is drawn by the app). */
@Composable
fun TScreen(
    header: @Composable () -> Unit,
    status: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxSize().background(TerminalTheme.colors.background)) {
        header()
        Column(Modifier.weight(1f).fillMaxWidth(), content = content)
        status()
    }
}

private val tabKeys = listOf(TKey("1", "home"), TKey("2", "lib"), TKey("3", "find"), TKey("4", "dl"))

/** Mini line and key bar under the current screen. Player and lyrics hide both, queue keeps the keys. */
@Composable
fun TAppChrome(route: String?, playback: PlaybackState, actions: HexaActions) {
    val full = route != Routes.PLAYER && route != Routes.LYRICS && route != Routes.QUEUE
    val keys = route != Routes.PLAYER && route != Routes.LYRICS
    val song = playback.song
    Column(Modifier.fillMaxWidth()) {
        if (full && song != null) {
            Box(Modifier.horizontalSwipe(onLeft = actions::next, onRight = actions::previous)) {
                TMiniLine(
                    title = song.title,
                    artist = song.artist,
                    positionMs = playback.positionMs,
                    durationMs = playback.durationMs,
                    isPlaying = playback.isPlaying,
                    onOpen = actions::openPlayer,
                    onPlayPause = actions::togglePlayPause,
                    onNext = actions::next,
                )
            }
        }
        if (keys) {
            TKeyBar(tabKeys, Routes.tabOf(route)?.ordinal ?: -1, { actions.openTab(Tab.entries[it]) })
        }
    }
}
