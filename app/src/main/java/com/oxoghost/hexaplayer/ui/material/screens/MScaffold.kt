package com.oxoghost.hexaplayer.ui.material.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.oxoghost.hexaplayer.data.Playlist
import com.oxoghost.hexaplayer.data.Song
import com.oxoghost.hexaplayer.ui.common.CoverImage
import com.oxoghost.hexaplayer.ui.common.SwipeToQueue
import com.oxoghost.hexaplayer.ui.common.Symbol
import com.oxoghost.hexaplayer.ui.common.Symbols
import com.oxoghost.hexaplayer.ui.common.albumKey
import com.oxoghost.hexaplayer.ui.common.artModel
import com.oxoghost.hexaplayer.ui.common.formatDuration
import com.oxoghost.hexaplayer.ui.common.horizontalSwipe
import com.oxoghost.hexaplayer.ui.common.rowGestures
import com.oxoghost.hexaplayer.ui.material.components.MBottomChrome
import com.oxoghost.hexaplayer.ui.material.components.MFloatingNav
import com.oxoghost.hexaplayer.ui.material.components.MListRow
import com.oxoghost.hexaplayer.ui.material.components.MMiniPlayer
import com.oxoghost.hexaplayer.ui.material.components.MNavItem
import com.oxoghost.hexaplayer.ui.material.theme.HexaShapes
import com.oxoghost.hexaplayer.ui.nav.Routes
import com.oxoghost.hexaplayer.ui.state.HexaActions
import com.oxoghost.hexaplayer.ui.state.LocalSelection
import com.oxoghost.hexaplayer.ui.state.PlaybackState
import com.oxoghost.hexaplayer.ui.state.Selection
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

/** Floating mini player and nav; while songs are selected, the selection toolbar instead. */
@Composable
fun MAppChrome(route: String?, playback: PlaybackState, actions: HexaActions, modifier: Modifier = Modifier) {
    if (!showsMaterialChrome(route)) return
    val selection = LocalSelection.current
    if (selection.active) {
        MBottomChrome(modifier) { SelectionToolbar(actions) }
        return
    }
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

@Composable
private fun SelectionToolbar(actions: HexaActions) {
    val s = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .height(Metrics.navBar.dp + 8.dp)
            .clip(HexaShapes.navBar)
            .background(s.surfaceContainerHigh)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(
            Triple(Symbols.PlaylistPlay, "Play next", actions::selectionPlayNext),
            Triple(Symbols.QueueMusic, "Queue", actions::selectionQueue),
            Triple(Symbols.PlaylistAdd, "Playlist", actions::selectionAddTo),
            Triple(Symbols.Edit, "Edit tags", actions::selectionEdit),
        ).forEach { (icon, label, onClick) ->
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(HexaShapes.card)
                    .clickable(role = Role.Button, onClick = onClick),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
            ) {
                Symbol(icon, null, tint = s.onSurface)
                Text(label, style = MaterialTheme.typography.labelLarge, color = s.onSurface)
            }
        }
    }
}

/** Replaces the top row of the current screen while songs are selected. */
@Composable
fun MSelectionTopBar(selection: Selection, modifier: Modifier = Modifier) {
    val s = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .background(s.surface)
            .statusBarsPadding()
            .padding(horizontal = Metrics.screenPaddingH.dp, vertical = 8.dp)
            .height(Metrics.searchPill.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .fillMaxHeight()
                .clip(HexaShapes.pill)
                .background(s.surfaceContainerHigh)
                .clickable(role = Role.Button, onClickLabel = "End selection", onClick = selection::clear)
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Symbol(Symbols.Close, null, tint = s.onSurface)
            Text("${selection.ids.size}", style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.weight(1f))
        Row(
            Modifier
                .fillMaxHeight()
                .clip(HexaShapes.pill)
                .background(s.surfaceContainerHigh)
                .clickable(role = Role.Button, onClick = selection::selectAll)
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Symbol(Symbols.SelectAll, null, tint = s.onSurface)
            Text("Select all", style = MaterialTheme.typography.titleSmall)
        }
    }
}

/**
 * Song row of a list that supports multi-select: tap plays (or toggles while selecting), long
 * press opens the song actions, long press and drag selects a range of [pool].
 */
@Composable
fun MSongRow(
    song: Song,
    pool: List<Song>,
    index: Int,
    playback: PlaybackState,
    actions: HexaActions,
    onPlay: () -> Unit,
    subtitle: String = "${song.artist} · ${formatDuration(song.duration)}",
    playlist: Playlist? = null,
) {
    val selection = LocalSelection.current
    val selecting = selection.active && selection.pool == pool
    SwipeToQueue(onSwipe = { actions.addToQueue(song) }, color = MaterialTheme.colorScheme.primary) {
        MListRow(
            title = song.title,
            subtitle = subtitle,
            playing = playback.song?.id == song.id,
            onMore = { actions.songMenu(song, playlist) },
            selected = if (selecting) selection.isSelected(song) else null,
            gestures = Modifier.rowGestures(
                onClick = { if (selecting) selection.toggle(pool, song) else onPlay() },
                onLongClick = { if (selecting) selection.toggle(pool, song) else actions.songMenu(song, playlist) },
                onRangeStart = { selection.startRange(pool, index) },
                onRange = { selection.extendRange(index + it) },
            ),
        ) { CoverImage(song.artModel, song.albumKey, Modifier.fillMaxSize(), HexaShapes.coverRow) }
    }
}
