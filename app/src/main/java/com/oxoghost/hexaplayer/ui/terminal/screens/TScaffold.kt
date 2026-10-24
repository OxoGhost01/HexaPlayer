package com.oxoghost.hexaplayer.ui.terminal.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.oxoghost.hexaplayer.data.Playlist
import com.oxoghost.hexaplayer.data.Song
import com.oxoghost.hexaplayer.ui.common.SwipeToQueue
import com.oxoghost.hexaplayer.ui.common.formatDuration
import com.oxoghost.hexaplayer.ui.common.horizontalSwipe
import com.oxoghost.hexaplayer.ui.common.rowGestures
import com.oxoghost.hexaplayer.ui.nav.Routes
import com.oxoghost.hexaplayer.ui.state.HexaActions
import com.oxoghost.hexaplayer.ui.state.LocalSelection
import com.oxoghost.hexaplayer.ui.state.PlaybackState
import com.oxoghost.hexaplayer.ui.state.Selection
import com.oxoghost.hexaplayer.ui.state.Tab
import com.oxoghost.hexaplayer.ui.terminal.components.TActionKeyBar
import com.oxoghost.hexaplayer.ui.terminal.components.THeaderButton
import com.oxoghost.hexaplayer.ui.terminal.components.TKey
import com.oxoghost.hexaplayer.ui.terminal.components.TKeyBar
import com.oxoghost.hexaplayer.ui.terminal.components.TListRow
import com.oxoghost.hexaplayer.ui.terminal.components.TMiniLine
import com.oxoghost.hexaplayer.ui.terminal.components.TMode
import com.oxoghost.hexaplayer.ui.terminal.components.TStatusLine
import com.oxoghost.hexaplayer.ui.terminal.components.TText
import com.oxoghost.hexaplayer.ui.terminal.theme.TerminalTheme
import com.oxoghost.hexaplayer.ui.tokens.DesignTokens.Terminal.Metrics

/**
 * Terminal screen frame: header, content, status line (chrome below is drawn by the app).
 * While songs are selected the header and status line switch to VIS mode.
 */
@Composable
fun TScreen(
    header: @Composable () -> Unit,
    status: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val selection = LocalSelection.current
    Column(modifier.fillMaxSize().background(TerminalTheme.colors.background)) {
        if (selection.active) TSelectionHeader(selection) else header()
        Column(Modifier.weight(1f).fillMaxWidth(), content = content)
        if (selection.active) {
            TStatusLine(
                TMode.VIS,
                "${selection.ids.size} songs · ${formatDuration(selection.durationMs)}",
                position = "${selection.ids.size}/${selection.pool.size}",
            )
        } else {
            status()
        }
    }
}

@Composable
private fun TSelectionHeader(selection: Selection) {
    val c = TerminalTheme.colors
    Row(
        Modifier.fillMaxWidth().height(Metrics.header.dp).padding(start = Metrics.screenPaddingH.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TText("${selection.ids.size} selected", color = c.accent2, style = TerminalTheme.type.body.copy(fontWeight = FontWeight.Bold))
        Spacer(Modifier.weight(1f))
        THeaderButton("all", c.accent2, "Select all", selection::selectAll)
        THeaderButton("invert", c.dim, "Invert selection", selection::invert)
        THeaderButton("[esc]", c.dim, "End selection", selection::clear)
    }
}

/**
 * Song row of a list that supports multi-select: tap plays (or toggles while selecting), long
 * press opens the song actions, long press and drag selects a range of [pool].
 */
@Composable
fun TSongRow(
    song: Song,
    pool: List<Song>,
    index: Int,
    actions: HexaActions,
    onPlay: () -> Unit,
    title: String = song.title,
    subtitle: String? = song.artist,
    trailing: String? = formatDuration(song.duration),
    cursor: Boolean = false,
    playing: Boolean = false,
    playlist: Playlist? = null,
) {
    val selection = LocalSelection.current
    val selecting = selection.active && selection.pool == pool
    SwipeToQueue(onSwipe = { actions.addToQueue(song) }, color = TerminalTheme.colors.accent) {
        TListRow(
            title = title,
            subtitle = subtitle,
            trailing = trailing,
            cursor = cursor && !selecting,
            playing = playing,
            selected = if (selecting) selection.isSelected(song) else null,
            gestures = Modifier.rowGestures(
                onClick = { if (selecting) selection.toggle(pool, song) else onPlay() },
                onLongClick = { if (selecting) selection.toggle(pool, song) else actions.songMenu(song, playlist) },
                onRangeStart = { selection.startRange(pool, index) },
                onRange = { selection.extendRange(index + it) },
            ),
        )
    }
}

private val tabKeys = listOf(TKey("1", "home"), TKey("2", "lib"), TKey("3", "find"), TKey("4", "dl"))
private val selectionKeys = listOf(TKey("n", "next"), TKey("q", "queue"), TKey("p", "playlist"), TKey("e", "edit tags"))

/**
 * Mini line and key bar under the current screen. Player, lyrics, tag editor, setup and appearance hide both,
 * the queue keeps the keys, and multi-select swaps the keys for its actions.
 */
@Composable
fun TAppChrome(route: String?, playback: PlaybackState, actions: HexaActions) {
    val selection = LocalSelection.current
    val fullScreen = route == Routes.PLAYER || route == Routes.LYRICS || route == Routes.EDIT || route == Routes.SETUP || route == Routes.APPEARANCE || route == Routes.WRAPPED || route == Routes.FLAVOR
    val song = playback.song
    Column(Modifier.fillMaxWidth()) {
        when {
            fullScreen -> {}
            selection.active -> TActionKeyBar(selectionKeys, { i ->
                when (i) {
                    0 -> actions.selectionPlayNext()
                    1 -> actions.selectionQueue()
                    2 -> actions.selectionAddTo()
                    else -> actions.selectionEdit()
                }
            })
            else -> {
                if (route != Routes.QUEUE && song != null) {
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
                TKeyBar(tabKeys, Routes.tabOf(route)?.ordinal ?: -1, { actions.openTab(Tab.entries[it]) })
            }
        }
    }
}
