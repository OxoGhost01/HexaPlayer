package com.oxoghost.hexaplayer.ui.terminal.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.oxoghost.hexaplayer.data.Song
import com.oxoghost.hexaplayer.ui.common.PixelCover
import com.oxoghost.hexaplayer.ui.common.SwipeToQueue
import com.oxoghost.hexaplayer.ui.common.albumKey
import com.oxoghost.hexaplayer.ui.common.artModel
import com.oxoghost.hexaplayer.ui.common.durationMs
import com.oxoghost.hexaplayer.ui.common.format
import com.oxoghost.hexaplayer.ui.common.formatDuration
import com.oxoghost.hexaplayer.ui.common.formatSize
import com.oxoghost.hexaplayer.ui.state.HexaActions
import com.oxoghost.hexaplayer.ui.state.LibrarySection
import com.oxoghost.hexaplayer.ui.state.LibraryState
import com.oxoghost.hexaplayer.ui.state.PlaybackState
import com.oxoghost.hexaplayer.ui.terminal.components.TDivider
import com.oxoghost.hexaplayer.ui.terminal.components.THeader
import com.oxoghost.hexaplayer.ui.terminal.components.TListRow
import com.oxoghost.hexaplayer.ui.terminal.components.TMode
import com.oxoghost.hexaplayer.ui.terminal.components.TParentColumn
import com.oxoghost.hexaplayer.ui.terminal.components.TPreviewPane
import com.oxoghost.hexaplayer.ui.terminal.components.TStatusLine
import com.oxoghost.hexaplayer.ui.terminal.components.TText
import com.oxoghost.hexaplayer.ui.terminal.theme.TerminalTheme

private val sectionNames = listOf("songs", "albums", "artists", "playlists", "folders")

@Composable
fun TLibraryScreen(lib: LibraryState, playback: PlaybackState, actions: HexaActions, pixelCovers: Boolean) {
    val c = TerminalTheme.colors
    val section = lib.section
    BackHandler(enabled = section == LibrarySection.Folders && lib.canGoUp, onBack = actions::folderUp)

    TScreen(
        header = {
            THeader(
                "~/lib/", current = sectionNames[section.ordinal],
                action = "[:set]", actionDescription = "Settings", onAction = actions::openSettings,
            )
        },
        status = { TStatusLine(TMode.NOR, statusContext(lib), position = "${(lib.cursor + 1).coerceAtMost(lib.itemCount)}/${lib.itemCount}") },
    ) {
        Toolbar(lib, actions)
        TDivider()
        Row(Modifier.weight(1f).fillMaxWidth()) {
            TParentColumn(sectionNames, section.ordinal, { actions.selectSection(LibrarySection.entries[it]) })
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Box(Modifier.weight(1f).fillMaxWidth()) { SectionList(lib, playback, actions) }
                Preview(lib, actions, pixelCovers)
            }
        }
    }
}

@Composable
private fun Toolbar(lib: LibraryState, actions: HexaActions) {
    val c = TerminalTheme.colors
    Row(
        Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (lib.section == LibrarySection.Folders) {
            TText(
                buildAnnotatedString {
                    val crumbs = lib.folderCrumbs
                    withStyle(SpanStyle(color = c.dim)) { append("/" + crumbs.dropLast(1).joinToString("") { "$it/" }) }
                    withStyle(SpanStyle(color = c.accent2)) { append(crumbs.last() + "/") }
                },
                Modifier.weight(1f),
            )
        } else {
            TText(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = c.dim)) { append("sort ") }
                    append(lib.sort.terminal)
                },
                Modifier.weight(1f).clickable(role = Role.Button, onClickLabel = "Change sort") { actions.cycleSort(lib.section) },
            )
        }
        when (lib.section) {
            LibrarySection.Songs -> {
                ToolAction("▶︎ all") { actions.play(lib.songs, 0, "library") }
                ToolAction("⇄ shuffle") { actions.shuffle(lib.songs, "library") }
            }
            LibrarySection.Albums -> TText("singles hidden", color = c.dim)
            LibrarySection.Playlists -> ToolAction("+ new", actions::createPlaylist)
            else -> {}
        }
    }
}

@Composable
private fun ToolAction(text: String, onClick: () -> Unit) {
    Box(Modifier.height(44.dp).clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        TText(text, color = TerminalTheme.colors.accent)
    }
}

@Composable
private fun SectionList(lib: LibraryState, playback: PlaybackState, actions: HexaActions) {
    val c = TerminalTheme.colors
    val listState = rememberLazyListState()
    val section = lib.section
    val playingId = playback.song?.id
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 4.dp)) {
        when (section) {
            LibrarySection.Songs -> itemsIndexed(lib.songs, key = { _, s -> s.id }) { i, song ->
                SongRow(song, i == lib.cursor, song.id == playingId, trailing = formatDuration(song.duration), actions,
                    onClick = {
                        actions.setCursor(section, i)
                        playWithAlbum(song, lib, actions)
                    })
            }
            LibrarySection.Albums -> itemsIndexed(lib.albums, key = { _, a -> a.id }) { i, album ->
                TListRow(
                    title = album.title, trailing = "${album.songs.size}", directory = true, cursor = i == lib.cursor,
                    onClick = {
                        actions.setCursor(section, i)
                        actions.openAlbum(album)
                    },
                    onLongClick = { actions.addToPlaylist(album.songs) },
                )
            }
            LibrarySection.Artists -> itemsIndexed(lib.artists, key = { _, a -> a.name }) { i, artist ->
                TListRow(
                    title = artist.name, trailing = "${artist.albums.size} · ${artist.songs.size}", directory = true,
                    cursor = i == lib.cursor,
                    onClick = {
                        actions.setCursor(section, i)
                        actions.openArtist(artist.name)
                    },
                )
            }
            LibrarySection.Playlists -> itemsIndexed(lib.playlists, key = { _, p -> p.id }) { i, playlist ->
                val songs = lib.playlistSongs[playlist.id].orEmpty()
                TListRow(
                    title = playlist.name,
                    subtitle = "${songs.size} songs · ${formatDuration(songs.sumOf { it.duration })}",
                    directory = true,
                    cursor = i == lib.cursor,
                    onClick = {
                        actions.setCursor(section, i)
                        actions.openPlaylist(playlist)
                    },
                    onLongClick = { actions.playlistMenu(playlist) },
                )
            }
            LibrarySection.Folders -> {
                if (lib.canGoUp) item { TListRow("..", directory = true, onClick = actions::folderUp) }
                itemsIndexed(lib.folders, key = { _, f -> f.path }) { i, folder ->
                    TListRow(
                        title = folder.name, trailing = "${folder.count}", directory = true, cursor = i == lib.cursor,
                        onClick = {
                            actions.setCursor(section, i)
                            actions.openFolder(folder.path, folder.name)
                        },
                    )
                }
                itemsIndexed(lib.folderSongs, key = { _, s -> s.id }) { i, song ->
                    val index = lib.folders.size + i
                    SongRow(song, index == lib.cursor, song.id == playingId, trailing = formatSize(song.size, short = true), actions,
                        title = song.displayName.ifBlank { song.title }, subtitle = null,
                        onClick = {
                            actions.setCursor(section, index)
                            actions.play(lib.folderSongs, i, "folder · ${lib.folderCrumbs.last()}")
                        })
                }
            }
        }
        if (lib.itemCount == 0 && !lib.loading) {
            item { TText(if (section == LibrarySection.Playlists) "no playlists yet: + new" else "nothing here", Modifier.padding(12.dp), color = c.dim) }
        }
    }
}

@Composable
private fun SongRow(
    song: Song,
    cursor: Boolean,
    playing: Boolean,
    trailing: String,
    actions: HexaActions,
    title: String = song.title,
    subtitle: String? = song.artist,
    onClick: () -> Unit,
) {
    SwipeToQueue(onSwipe = { actions.addToQueue(song) }, color = TerminalTheme.colors.accent) {
        TListRow(
            title = title, subtitle = subtitle, trailing = trailing, cursor = cursor, playing = playing,
            onClick = onClick, onLongClick = { actions.songMenu(song) },
        )
    }
}

/** 1.x behaviour: a song from the Songs list plays with the rest of its album queued. */
private fun playWithAlbum(song: Song, lib: LibraryState, actions: HexaActions) {
    val album = lib.albums.firstOrNull { a -> a.id == song.albumId }
    if (album != null) {
        actions.play(album.songs, album.songs.indexOfFirst { it.id == song.id }, "album · ${album.title}")
    } else {
        actions.play(lib.songs, lib.songs.indexOf(song), "library")
    }
}

@Composable
private fun Preview(lib: LibraryState, actions: HexaActions, pixelCovers: Boolean) {
    val c = TerminalTheme.colors
    when (lib.section) {
        LibrarySection.Albums -> lib.albums.getOrNull(lib.cursor)?.let { album ->
            val first = album.songs.firstOrNull()
            TPreviewPane(
                lines = listOfNotNull(
                    AnnotatedString(album.artist),
                    AnnotatedString("${album.songs.size} songs"),
                    AnnotatedString(formatDuration(album.durationMs) + (first?.format?.takeIf { it.isNotEmpty() }?.let { " · $it" } ?: "")),
                ),
                onOpen = { actions.openAlbum(album) },
                cover = { PixelCover(album.artModel, album.id.toString(), 12, Modifier.fillMaxSize(), pixelCovers) },
                title = album.title,
            )
        }
        LibrarySection.Artists -> lib.artists.getOrNull(lib.cursor)?.let { artist ->
            val albums = artist.albums.take(4)
            TPreviewPane(
                lines = albums.mapIndexed { i, a ->
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = c.dim)) { append(if (i == albums.lastIndex) "└─ " else "├─ ") }
                        withStyle(SpanStyle(color = c.accent2)) { append(a.title) }
                    }
                },
                onOpen = { actions.openArtist(artist.name) },
                cover = {
                    val first = artist.songs.firstOrNull()
                    PixelCover(first?.artModel, first?.albumKey, 12, Modifier.fillMaxSize(), pixelCovers)
                },
                title = artist.name,
            )
        }
        else -> {}
    }
}

private fun statusContext(lib: LibraryState): String = when (lib.section) {
    LibrarySection.Songs -> lib.songs.getOrNull(lib.cursor)?.displayName ?: "${lib.totalSongs} songs"
    LibrarySection.Albums -> "long press: add all to playlist"
    LibrarySection.Artists -> "${lib.artists.size} artists"
    LibrarySection.Playlists -> "long press: edit or delete"
    LibrarySection.Folders -> {
        val size = lib.folderSongs.sumOf { it.size }
        "drwx · ${lib.itemCount} items" + (if (size > 0) " · ${formatSize(size)}" else "")
    }
}
