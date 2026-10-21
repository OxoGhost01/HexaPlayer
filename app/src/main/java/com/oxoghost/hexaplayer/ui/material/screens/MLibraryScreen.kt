package com.oxoghost.hexaplayer.ui.material.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oxoghost.hexaplayer.data.Song
import com.oxoghost.hexaplayer.ui.common.CoverImage
import com.oxoghost.hexaplayer.ui.common.SwipeToQueue
import com.oxoghost.hexaplayer.ui.common.Symbol
import com.oxoghost.hexaplayer.ui.common.Symbols
import com.oxoghost.hexaplayer.ui.common.albumKey
import com.oxoghost.hexaplayer.ui.common.artModel
import com.oxoghost.hexaplayer.ui.common.formatDuration
import com.oxoghost.hexaplayer.ui.common.formatSize
import com.oxoghost.hexaplayer.ui.material.components.MCircleButton
import com.oxoghost.hexaplayer.ui.material.components.MIconButton
import com.oxoghost.hexaplayer.ui.material.components.MListRow
import com.oxoghost.hexaplayer.ui.material.components.MSegment
import com.oxoghost.hexaplayer.ui.material.components.MTabChips
import com.oxoghost.hexaplayer.ui.material.components.MTopRow
import com.oxoghost.hexaplayer.ui.material.theme.HexaShapes
import com.oxoghost.hexaplayer.ui.state.HexaActions
import com.oxoghost.hexaplayer.ui.state.LibrarySection
import com.oxoghost.hexaplayer.ui.state.LibraryState
import com.oxoghost.hexaplayer.ui.state.PlaybackState

private const val COLS = 6
private val tabs = listOf("Songs", "Albums", "Artists", "Playlists", "Folders")

@Composable
fun MLibraryScreen(lib: LibraryState, playback: PlaybackState, actions: HexaActions) {
    var albumList by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = lib.section == LibrarySection.Folders && lib.canGoUp, onBack = actions::folderUp)
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        MTopRow(onSearch = actions::openSearch, onIdentify = null, onSettings = actions::openSettings)
        if (lib.totalSongs == 0 && !lib.loading) {
            MEmptyLibrary(lib.folder, actions, Modifier.weight(1f).fillMaxWidth())
            return@Column
        }
        MTabChips(tabs, lib.section.ordinal, { actions.selectSection(LibrarySection.entries[it]) }, modifier = Modifier.padding(vertical = 8.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(COLS),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = LocalChromePadding.current),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            full {
                if (lib.section == LibrarySection.Folders) Breadcrumbs(lib, actions)
                else SortRow(lib, actions, albumList) { albumList = !albumList }
            }
            when (lib.section) {
                LibrarySection.Songs -> songs(lib.songs, playback, actions) { song, i -> playWithAlbum(song, i, lib, actions) }
                LibrarySection.Albums -> albums(lib, actions, albumList)
                LibrarySection.Artists -> artists(lib, actions)
                LibrarySection.Playlists -> playlists(lib, actions)
                LibrarySection.Folders -> folders(lib, playback, actions)
            }
            if (lib.itemCount == 0 && !lib.loading && lib.section != LibrarySection.Playlists) {
                full {
                    Text("Nothing here yet", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

private fun LazyGridScope.full(content: @Composable () -> Unit) = item(span = { GridItemSpan(COLS) }) { content() }

@Composable
private fun SortRow(lib: LibraryState, actions: HexaActions, albumList: Boolean, onToggleAlbums: () -> Unit) {
    val s = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .height(44.dp)
                .clip(HexaShapes.pill)
                .clickable(role = Role.Button, onClickLabel = "Change sort") { actions.cycleSort(lib.section) }
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Symbol(Symbols.Sort, null, tint = s.onSurfaceVariant)
            Text(lib.sort.material, style = MaterialTheme.typography.titleSmall, color = s.onSurfaceVariant)
        }
        if (lib.section == LibrarySection.Songs) {
            Text("  ${lib.songs.size} songs", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = s.onSurfaceVariant)
            MCircleButton(Symbols.Shuffle, "Shuffle all", { actions.shuffle(lib.songs, "library") }, size = 48.dp)
            Box(Modifier.size(8.dp))
            MCircleButton(Symbols.PlayArrow, "Play all", { actions.play(lib.songs, 0, "library") },
                container = s.primaryContainer, content = s.onPrimaryContainer, size = 48.dp,
                shape = RoundedCornerShape(16.dp), filled = true)
        } else {
            Box(Modifier.weight(1f))
            when (lib.section) {
                LibrarySection.Albums -> MIconButton(if (albumList) Symbols.Album else Symbols.ViewList,
                    if (albumList) "Show as grid" else "Show as list", onToggleAlbums)
                LibrarySection.Playlists -> MIconButton(Symbols.Add, "New playlist", actions::createPlaylist, tint = s.primary)
                else -> {}
            }
        }
    }
}

@Composable
private fun Breadcrumbs(lib: LibraryState, actions: HexaActions) {
    val s = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (lib.canGoUp) MIconButton(Symbols.ArrowUpward, "Up one folder", actions::folderUp, tint = s.onSurface)
        Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            lib.folderCrumbs.forEachIndexed { i, crumb ->
                val last = i == lib.folderCrumbs.lastIndex
                if (i > 0) Symbol(Symbols.ChevronRight, null, size = 20.dp, tint = s.onSurfaceVariant)
                Text(
                    crumb,
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (last) s.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    color = if (last) s.onSecondaryContainer else s.onSurfaceVariant,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }
    }
}

private fun LazyGridScope.songs(songs: List<Song>, playback: PlaybackState, actions: HexaActions, onPlay: (Song, Int) -> Unit) {
    itemsIndexed(songs, key = { _, s -> "s${s.id}" }, span = { _, _ -> GridItemSpan(COLS) }) { i, song ->
        MSongRow(song, songs, i, playback, actions, onPlay = { onPlay(song, i) })
    }
}

private fun playWithAlbum(song: Song, index: Int, lib: LibraryState, actions: HexaActions) {
    val album = lib.albums.firstOrNull { it.id == song.albumId }
    if (album != null) actions.play(album.songs, album.songs.indexOfFirst { it.id == song.id }, "album · ${album.title}")
    else actions.play(lib.songs, index, "library")
}

@OptIn(ExperimentalFoundationApi::class)
private fun LazyGridScope.albums(lib: LibraryState, actions: HexaActions, asList: Boolean) {
    itemsIndexed(lib.albums, key = { _, a -> "a${a.id}" }, span = { _, _ -> GridItemSpan(if (asList) COLS else 3) }) { _, album ->
        if (asList) {
            MListRow(
                title = album.title,
                subtitle = "${album.artist} · ${album.songs.size} songs",
                onClick = { actions.openAlbum(album) },
                onLongClick = { actions.addToPlaylist(album.songs, album.title) },
            ) { CoverImage(album.artModel, album.albumKey, Modifier.fillMaxSize(), HexaShapes.coverRow) }
        } else {
            GridTile(
                title = album.title,
                subtitle = album.artist,
                onClick = { actions.openAlbum(album) },
                onLongClick = { actions.addToPlaylist(album.songs, album.title) },
            ) { CoverImage(album.artModel, album.albumKey, Modifier.fillMaxSize(), HexaShapes.coverGrid) }
        }
    }
}

private fun LazyGridScope.artists(lib: LibraryState, actions: HexaActions) {
    itemsIndexed(lib.artists, key = { _, a -> "r${a.name}" }, span = { _, _ -> GridItemSpan(2) }) { _, artist ->
        val first = artist.songs.firstOrNull()
        GridTile(artist.name, "${artist.songs.size} songs", { actions.openArtist(artist.name) }, null, centered = true) {
            CoverImage(first?.artModel, first?.albumKey, Modifier.fillMaxSize(), CircleShape)
        }
    }
}

private fun LazyGridScope.playlists(lib: LibraryState, actions: HexaActions) {
    item(key = "new", span = { GridItemSpan(3) }) { NewPlaylistTile(actions::createPlaylist) }
    itemsIndexed(lib.playlists, key = { _, p -> "p${p.id}" }, span = { _, _ -> GridItemSpan(3) }) { _, playlist ->
        val songs = lib.playlistSongs[playlist.id].orEmpty()
        val first = songs.firstOrNull()
        GridTile(playlist.name, "${songs.size} songs", { actions.openPlaylist(playlist) }, { actions.editPlaylist(playlist) }) {
            CoverImage(playlist.coverUri ?: first?.artModel, playlist.coverUri ?: first?.albumKey, Modifier.fillMaxSize(), HexaShapes.coverGrid)
        }
    }
}

private fun LazyGridScope.folders(lib: LibraryState, playback: PlaybackState, actions: HexaActions) {
    val count = lib.folders.size + lib.folderSongs.size
    itemsIndexed(lib.folders, key = { _, f -> "f${f.path}" }, span = { _, _ -> GridItemSpan(COLS) }) { i, folder ->
        val s = MaterialTheme.colorScheme
        MSegment(i, count, Modifier.padding(bottom = 2.dp), onClick = { actions.openFolder(folder.path, folder.name) }) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(s.primaryContainer), contentAlignment = Alignment.Center) {
                Symbol(Symbols.Folder, null, filled = true, tint = s.onPrimaryContainer)
            }
            Column(Modifier.weight(1f)) {
                Text(folder.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${folder.count} items", color = s.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    itemsIndexed(lib.folderSongs, key = { _, song -> "fs${song.id}" }, span = { _, _ -> GridItemSpan(COLS) }) { i, song ->
        val s = MaterialTheme.colorScheme
        val index = lib.folders.size + i
        SwipeToQueue(onSwipe = { actions.addToQueue(song) }, color = s.primary, modifier = Modifier.padding(bottom = 2.dp)) {
            MSegment(index, count, onClick = { actions.play(lib.folderSongs, i, "folder · ${lib.folderCrumbs.last()}") },
                container = if (playback.song?.id == song.id) s.secondaryContainer else s.surfaceContainer) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(s.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                    Symbol(Symbols.MusicNote, null, tint = s.onSurfaceVariant)
                }
                Column(Modifier.weight(1f)) {
                    Text(song.displayName.ifBlank { song.title }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOf(formatSize(song.size), formatDuration(song.duration)).filter { it.isNotEmpty() }.joinToString(" · "),
                        color = s.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
                MIconButton(Symbols.MoreVert, "More actions for ${song.title}", { actions.songMenu(song) })
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridTile(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    centered: Boolean = false,
    cover: @Composable () -> Unit,
) {
    val s = MaterialTheme.colorScheme
    Column(
        Modifier
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) { cover() }
        Text(title, Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp), style = MaterialTheme.typography.titleSmall,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = if (centered) TextAlign.Center else TextAlign.Start)
        Text(subtitle, Modifier.padding(horizontal = 4.dp), color = s.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = if (centered) TextAlign.Center else TextAlign.Start)
    }
}

@Composable
private fun NewPlaylistTile(onClick: () -> Unit) {
    val s = MaterialTheme.colorScheme
    Box(
        Modifier
            .padding(vertical = 8.dp)
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(HexaShapes.coverGrid)
            .clickable(role = Role.Button, onClick = onClick)
            .drawBehind {
                val stroke = 2.dp.toPx()
                drawRoundRect(
                    s.outline,
                    topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
                    size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(28.dp.toPx()),
                    style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Symbol(Symbols.Add, null, size = 32.dp, tint = s.primary)
            Text("New playlist", color = s.primary, style = MaterialTheme.typography.titleSmall)
        }
    }
}
