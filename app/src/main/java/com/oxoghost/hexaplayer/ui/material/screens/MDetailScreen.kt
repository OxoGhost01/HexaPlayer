package com.oxoghost.hexaplayer.ui.material.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oxoghost.hexaplayer.data.Playlist
import com.oxoghost.hexaplayer.data.Song
import com.oxoghost.hexaplayer.ui.common.CoverImage
import com.oxoghost.hexaplayer.ui.common.ReorderState
import com.oxoghost.hexaplayer.ui.common.SwipeToQueue
import com.oxoghost.hexaplayer.ui.common.Symbol
import com.oxoghost.hexaplayer.ui.common.Symbols
import com.oxoghost.hexaplayer.ui.common.albumKey
import com.oxoghost.hexaplayer.ui.common.artModel
import com.oxoghost.hexaplayer.ui.common.formatDay
import com.oxoghost.hexaplayer.ui.common.formatDuration
import com.oxoghost.hexaplayer.ui.common.formatLengthLong
import com.oxoghost.hexaplayer.ui.common.plural
import com.oxoghost.hexaplayer.ui.common.rememberReorderState
import com.oxoghost.hexaplayer.ui.common.reorderHandle
import com.oxoghost.hexaplayer.ui.common.reorderItem
import com.oxoghost.hexaplayer.ui.common.rowGestures
import com.oxoghost.hexaplayer.ui.common.year
import com.oxoghost.hexaplayer.ui.material.components.MButtonSize
import com.oxoghost.hexaplayer.ui.material.components.MButtonStyle
import com.oxoghost.hexaplayer.ui.material.components.MCircleButton
import com.oxoghost.hexaplayer.ui.material.components.MIconButton
import com.oxoghost.hexaplayer.ui.material.components.MPillButton
import com.oxoghost.hexaplayer.ui.material.components.MSegment
import com.oxoghost.hexaplayer.ui.material.components.MSegmentedGroup
import com.oxoghost.hexaplayer.ui.material.components.MSelectionCircle
import com.oxoghost.hexaplayer.ui.material.theme.HexaShapes
import com.oxoghost.hexaplayer.ui.state.DetailKey
import com.oxoghost.hexaplayer.ui.state.DetailState
import com.oxoghost.hexaplayer.ui.state.HexaActions
import com.oxoghost.hexaplayer.ui.state.LocalSelection
import com.oxoghost.hexaplayer.ui.state.PlaybackState

@Composable
fun MDetailScreen(detail: DetailState?, playback: PlaybackState, actions: HexaActions) {
    val s = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            MCircleButton(Symbols.ArrowBack, "Back", actions::back, container = s.surfaceContainerHigh, content = s.onSurface, size = 48.dp)
            Box(Modifier.weight(1f))
            detail?.playlist?.let { p ->
                MPillButton("Edit", { actions.editPlaylist(p) }, style = MButtonStyle.Tonal, size = MButtonSize.M, icon = Symbols.Edit)
            }
        }
        when {
            detail == null -> Text("This item is no longer in your library", Modifier.padding(24.dp), color = s.onSurfaceVariant)
            detail.key is DetailKey.ArtistKey -> ArtistContent(detail, playback, actions)
            detail.playlist != null -> PlaylistContent(detail, detail.playlist, playback, actions)
            else -> AlbumContent(detail, playback, actions)
        }
    }
}

@Composable
private fun AlbumContent(detail: DetailState, playback: PlaybackState, actions: HexaActions) {
    val s = MaterialTheme.colorScheme
    val songs = detail.songs
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = LocalChromePadding.current)) {
        item {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                val first = songs.firstOrNull()
                CoverImage(detail.album?.artModel ?: first?.artModel, first?.albumKey, Modifier.size(168.dp), HexaShapes.coverAlbumHeader, "Cover of ${detail.title}")
                Column(Modifier.weight(1f)) {
                    Text(detail.title, style = MaterialTheme.typography.headlineMedium.copy(fontSize = 26.sp, fontWeight = FontWeight.Bold),
                        maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text(
                        detail.subtitle,
                        Modifier.padding(top = 4.dp).clickable(role = Role.Button, onClickLabel = "Open artist") { actions.openArtist(detail.subtitle) },
                        color = s.primary,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        listOfNotNull(
                            detail.year.takeIf { it > 0 }?.toString(), "${songs.size} songs", formatLengthLong(detail.durationMs),
                            detail.totalPlays.takeIf { it > 0 }?.let { plural(it, "play") },
                        ).joinToString(" · "),
                        color = s.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        item {
            Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                MPillButton("Play", { actions.play(songs, 0, detail.source) }, Modifier.weight(1f), icon = Symbols.PlayArrow)
                MCircleButton(Symbols.Shuffle, "Shuffle", { actions.shuffle(songs, detail.source) }, shape = RoundedCornerShape(18.dp))
                MCircleButton(Symbols.PlaylistAdd, "Add album to a playlist", { actions.addToPlaylist(songs, detail.title) },
                    container = s.surfaceContainerHigh, content = s.onSurface)
                MCircleButton(Symbols.QueueMusic, "Add album to queue", { actions.addAllToQueue(songs) },
                    container = s.surfaceContainerHigh, content = s.onSurface)
            }
        }
        itemsIndexed(songs, key = { _, song -> song.id }) { i, song ->
            TrackSegment(song, songs, i, playback, actions, number = if (song.track > 0) song.track else i + 1, onPlay = { actions.play(songs, i, detail.source) })
        }
    }
}

/** Numbered track in a segmented group: number, title, length, more (or the selection circle). */
@Composable
private fun TrackSegment(song: Song, pool: List<Song>, index: Int, playback: PlaybackState, actions: HexaActions, number: Int, onPlay: () -> Unit) {
    val s = MaterialTheme.colorScheme
    val selection = LocalSelection.current
    val selecting = selection.active && selection.pool == pool
    val selected = selecting && selection.isSelected(song)
    val playing = playback.song?.id == song.id
    SwipeToQueue(onSwipe = { actions.addToQueue(song) }, color = s.primary, modifier = Modifier.padding(bottom = 2.dp)) {
        MSegment(
            index, pool.size,
            container = when {
                selected -> s.secondaryContainer
                playing -> s.primaryContainer
                else -> s.surfaceContainer
            },
            gestures = Modifier.rowGestures(
                onClick = { if (selecting) selection.toggle(pool, song) else onPlay() },
                onLongClick = { if (selecting) selection.toggle(pool, song) else actions.songMenu(song) },
                onRangeStart = { selection.startRange(pool, index) },
                onRange = { selection.extendRange(index + it) },
            ),
        ) {
            val fg = if (playing && !selected) s.onPrimaryContainer else s.onSurface
            Text("$number", Modifier.width(32.dp), color = if (playing) fg else s.onSurfaceVariant, style = MaterialTheme.typography.titleSmall)
            Text(song.title, Modifier.weight(1f), color = fg, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(formatDuration(song.duration), color = if (playing) fg else s.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            if (selecting) MSelectionCircle(selected, null)
            else MIconButton(Symbols.MoreVert, "More actions for ${song.title}", { actions.songMenu(song) }, tint = fg)
        }
    }
}

@Composable
private fun ArtistContent(detail: DetailState, playback: PlaybackState, actions: HexaActions) {
    val s = MaterialTheme.colorScheme
    val songs = detail.songs
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = LocalChromePadding.current)) {
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val art = detail.albums.firstOrNull()
                CoverImage(art?.artModel, art?.albumKey, Modifier.size(148.dp), CircleShape)
                Text(detail.title, Modifier.padding(top = 16.dp), style = MaterialTheme.typography.headlineLarge.copy(fontSize = 30.sp, fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${detail.albums.size} albums · ${songs.size} songs · ${formatLengthLong(detail.durationMs)}",
                    color = s.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                Row(Modifier.padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MPillButton("Play all", { actions.play(songs, 0, detail.source) }, icon = Symbols.PlayArrow, size = MButtonSize.M)
                    MPillButton("Shuffle", { actions.shuffle(songs, detail.source) }, style = MButtonStyle.Tonal, size = MButtonSize.M, icon = Symbols.Shuffle)
                }
            }
        }
        if (detail.albums.isNotEmpty()) {
            item { Heading("Albums", Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(detail.albums, key = { it.id }) { album ->
                        Column(
                            Modifier
                                .width(136.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .clickable(role = Role.Button, onClickLabel = "Open album") { actions.openAlbum(album) }
                        ) {
                            CoverImage(album.artModel, album.albumKey, Modifier.size(136.dp), HexaShapes.coverGrid)
                            Text(album.title, Modifier.padding(top = 8.dp, start = 4.dp), style = MaterialTheme.typography.titleSmall,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(album.year.takeIf { it > 0 }?.toString() ?: "${album.songs.size} songs", Modifier.padding(start = 4.dp),
                                color = s.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
        if (detail.mostPlayed.isNotEmpty()) {
            item { Heading("Most played", Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp)) }
            item {
                MSegmentedGroup(Modifier.padding(horizontal = 16.dp)) {
                    detail.mostPlayed.forEachIndexed { i, (song, plays) ->
                        MSegment(i, detail.mostPlayed.size, onClick = { actions.play(detail.mostPlayed.map { it.first }, i, detail.source) }) {
                            CoverImage(song.artModel, song.albumKey, Modifier.size(44.dp), HexaShapes.coverList)
                            Column(Modifier.weight(1f)) {
                                Text(song.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(song.album, color = s.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                            }
                            Text(plural(plays, "play"), color = s.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
        item { Heading("Songs", Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp)) }
        itemsIndexed(songs, key = { _, song -> song.id }) { i, song ->
            Box(Modifier.padding(horizontal = 8.dp)) {
                MSongRow(song, songs, i, playback, actions, onPlay = { actions.play(songs, i, detail.source) },
                    subtitle = "${song.album} · ${formatDuration(song.duration)}")
            }
        }
    }
}

@Composable
private fun Heading(text: String, modifier: Modifier) {
    Text(text, modifier, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold))
}

@Composable
private fun PlaylistContent(detail: DetailState, playlist: Playlist, playback: PlaybackState, actions: HexaActions) {
    val s = MaterialTheme.colorScheme
    val songs = detail.songs
    val reorder = rememberReorderState()
    val rowPx = with(LocalDensity.current) { (64 + 2).dp.toPx() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = LocalChromePadding.current)) {
        item {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                val first = songs.firstOrNull()
                Box(Modifier.size(132.dp)) {
                    CoverImage(playlist.coverUri ?: first?.artModel, playlist.coverUri ?: first?.albumKey, Modifier.fillMaxSize(), RoundedCornerShape(32.dp))
                    MCircleButton(Symbols.Image, "Change cover", { actions.pickPlaylistCover(playlist) },
                        Modifier.align(Alignment.BottomEnd).offset((-8).dp, (-8).dp),
                        container = s.surfaceContainerHighest, content = s.onSurface, size = 44.dp, iconSize = 20.dp)
                }
                Column(Modifier.weight(1f)) {
                    Text(detail.title, style = MaterialTheme.typography.headlineLarge.copy(fontSize = 30.sp, fontWeight = FontWeight.Bold),
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${songs.size} songs · ${formatLengthLong(detail.durationMs)}", color = s.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                    Text("Edited ${formatDay(playlist.lastEdited)}", color = s.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        item {
            Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                MPillButton("Play", { actions.play(songs, 0, detail.source) }, Modifier.weight(1f), icon = Symbols.PlayArrow, enabled = songs.isNotEmpty())
                MPillButton("Shuffle", { actions.shuffle(songs, detail.source) }, Modifier.weight(1f), style = MButtonStyle.Tonal,
                    icon = Symbols.Shuffle, enabled = songs.isNotEmpty())
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .border(1.dp, s.outline, CircleShape)
                        .clickable(role = Role.Button, onClickLabel = "Add songs") { actions.addSongsTo(playlist) }
                        .semantics { contentDescription = "Add songs" },
                    contentAlignment = Alignment.Center,
                ) {
                    Symbol(Symbols.Add, null, tint = s.primary)
                }
            }
        }
        if (songs.isEmpty()) {
            item { Text("This playlist is empty. Add songs with +", Modifier.padding(vertical = 24.dp), color = s.onSurfaceVariant) }
        }
        itemsIndexed(songs, key = { i, song -> "$i:${song.id}" }) { i, _ ->
            PlaylistSegment(detail, playlist, i, playback, actions, reorder, rowPx)
        }
    }
}

@Composable
private fun PlaylistSegment(
    detail: DetailState,
    playlist: Playlist,
    index: Int,
    playback: PlaybackState,
    actions: HexaActions,
    reorder: ReorderState,
    rowPx: Float,
) {
    val s = MaterialTheme.colorScheme
    val songs = detail.songs
    val song = songs[index]
    val selection = LocalSelection.current
    val selecting = selection.active && selection.pool == songs
    val selected = selecting && selection.isSelected(song)
    val dragging = reorder.isDragging("pl", index)
    val playing = playback.song?.id == song.id
    SwipeToQueue(onSwipe = { actions.addToQueue(song) }, color = s.primary, modifier = Modifier.padding(bottom = 2.dp).reorderItem(reorder, "pl", index)) {
        MSegment(
            index, songs.size,
            modifier = if (dragging) Modifier.clip(RoundedCornerShape(20.dp)) else Modifier,
            container = when {
                dragging -> s.surfaceContainerHighest
                selected -> s.secondaryContainer
                playing -> s.surfaceContainerHigh
                else -> s.surfaceContainer
            },
            gestures = Modifier.rowGestures(
                onClick = { if (selecting) selection.toggle(songs, song) else actions.play(songs, index, detail.source) },
                onLongClick = { if (selecting) selection.toggle(songs, song) else actions.songMenu(song, playlist) },
                onRangeStart = { selection.startRange(songs, index) },
                onRange = { selection.extendRange(index + it) },
            ),
        ) {
            CoverImage(song.artModel, song.albumKey, Modifier.size(44.dp), HexaShapes.coverList)
            Column(Modifier.weight(1f)) {
                Text(song.title, color = if (playing) s.primary else s.onSurface, style = MaterialTheme.typography.titleSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist, color = s.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (selecting) {
                MSelectionCircle(selected, null)
            } else {
                Box(
                    Modifier
                        .size(44.dp)
                        .semantics { contentDescription = "Drag to reorder ${song.title}" }
                        .reorderHandle(reorder, "pl", index, songs.size, rowPx) { from, to ->
                            actions.movePlaylistSong(playlist, songs[from], songs[to])
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Symbol(Symbols.DragHandle, null, tint = if (dragging) s.primary else s.onSurfaceVariant)
                }
            }
        }
    }
}
