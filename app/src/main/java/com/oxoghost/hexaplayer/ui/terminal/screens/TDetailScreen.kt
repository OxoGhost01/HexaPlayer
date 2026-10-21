package com.oxoghost.hexaplayer.ui.terminal.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.oxoghost.hexaplayer.data.Album
import com.oxoghost.hexaplayer.data.Playlist
import com.oxoghost.hexaplayer.repository.AudioInfo
import com.oxoghost.hexaplayer.ui.common.PixelCover
import com.oxoghost.hexaplayer.ui.common.ReorderState
import com.oxoghost.hexaplayer.ui.common.albumKey
import com.oxoghost.hexaplayer.ui.common.artModel
import com.oxoghost.hexaplayer.ui.common.format
import com.oxoghost.hexaplayer.ui.common.formatDay
import com.oxoghost.hexaplayer.ui.common.formatLengthShort
import com.oxoghost.hexaplayer.ui.common.qualityLine
import com.oxoghost.hexaplayer.ui.common.rememberReorderState
import com.oxoghost.hexaplayer.ui.common.reorderHandle
import com.oxoghost.hexaplayer.ui.common.reorderItem
import com.oxoghost.hexaplayer.ui.common.slug
import com.oxoghost.hexaplayer.ui.common.year
import com.oxoghost.hexaplayer.ui.state.DetailKey
import com.oxoghost.hexaplayer.ui.state.DetailState
import com.oxoghost.hexaplayer.ui.state.HexaActions
import com.oxoghost.hexaplayer.ui.state.PlaybackState
import com.oxoghost.hexaplayer.ui.terminal.components.TButton
import com.oxoghost.hexaplayer.ui.terminal.components.TDashedDivider
import com.oxoghost.hexaplayer.ui.terminal.components.TDivider
import com.oxoghost.hexaplayer.ui.terminal.components.THeader
import com.oxoghost.hexaplayer.ui.terminal.components.TMode
import com.oxoghost.hexaplayer.ui.terminal.components.TStatusLine
import com.oxoghost.hexaplayer.ui.terminal.components.TText
import com.oxoghost.hexaplayer.ui.terminal.theme.TerminalTheme
import com.oxoghost.hexaplayer.ui.tokens.DesignTokens.Terminal.Metrics

@Composable
fun TDetailScreen(detail: DetailState?, playback: PlaybackState, actions: HexaActions, pixelCovers: Boolean, info: AudioInfo?) {
    val c = TerminalTheme.colors
    when {
        detail == null -> TScreen(header = { THeader("~/lib/", onBack = actions::back) }, status = { TStatusLine(TMode.NOR, "not found") }) {
            TText("this item is no longer in the library", Modifier.padding(12.dp), color = c.dim)
        }
        detail.key is DetailKey.ArtistKey -> ArtistScreen(detail, playback, actions, pixelCovers)
        detail.playlist != null -> PlaylistScreen(detail, detail.playlist, playback, actions, pixelCovers)
        else -> AlbumScreen(detail, playback, actions, pixelCovers, info)
    }
}

private fun playingIndex(detail: DetailState, playback: PlaybackState) =
    detail.songs.indexOfFirst { it.id == playback.song?.id }.coerceAtLeast(0)

@Composable
private fun AlbumScreen(detail: DetailState, playback: PlaybackState, actions: HexaActions, pixelCovers: Boolean, info: AudioInfo?) {
    val c = TerminalTheme.colors
    val album = detail.album
    TScreen(
        header = { THeader("~/lib/albums/", current = slug(detail.title), onBack = actions::back) },
        status = { TStatusLine(TMode.NOR, "album", position = "${playingIndex(detail, playback) + 1}/${detail.songs.size}") },
    ) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    val first = detail.songs.firstOrNull()
                    PixelCover(album?.artModel ?: first?.artModel, first?.albumKey, 16, Modifier.size(132.dp), pixelCovers, "Cover of ${detail.title}")
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        TText(detail.title, style = TerminalTheme.type.title, maxLines = 2)
                        TText(
                            "${detail.subtitle}/",
                            Modifier.clickable(role = Role.Button, onClickLabel = "Open artist") { actions.openArtist(detail.subtitle) },
                            color = c.accent2,
                        )
                        TText(listOfNotNull(detail.year.takeIf { it > 0 }?.toString(), "${detail.songs.size} songs").joinToString(" · "),
                            Modifier.padding(top = 6.dp), color = c.dim)
                        val quality = info?.qualityLine() ?: first?.format.orEmpty()
                        TText(listOf(formatLengthShort(detail.durationMs), quality).filter { it.isNotEmpty() }.joinToString(" · "), color = c.dim)
                    }
                }
            }
            item {
                Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TButton("▶︎ play", { actions.play(detail.songs, 0, detail.source) }, Modifier.weight(2f), filled = true, large = true)
                    TButton("⇄ shuffle", { actions.shuffle(detail.songs, detail.source) }, Modifier.weight(2f), large = true)
                    TButton("+p", { actions.addToPlaylist(detail.songs, detail.title) }, Modifier.weight(1f), large = true)
                    TButton("+q", { actions.addAllToQueue(detail.songs) }, Modifier.weight(1f), large = true)
                }
                TDivider(Modifier.padding(top = 12.dp))
            }
            itemsIndexed(detail.songs, key = { _, s -> s.id }) { i, song ->
                val number = if (song.track > 0) song.track else i + 1
                TSongRow(
                    song, detail.songs, i, actions,
                    onPlay = { actions.play(detail.songs, i, detail.source) },
                    title = "%02d  %s".format(number, song.title),
                    subtitle = null,
                    playing = playback.song?.id == song.id,
                )
            }
        }
    }
}

@Composable
private fun ArtistScreen(detail: DetailState, playback: PlaybackState, actions: HexaActions, pixelCovers: Boolean) {
    val c = TerminalTheme.colors
    TScreen(
        header = { THeader("~/lib/artists/", current = slug(detail.title), onBack = actions::back) },
        status = { TStatusLine(TMode.NOR, "artist", position = "${playingIndex(detail, playback) + 1}/${detail.songs.size}") },
    ) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp)) {
            item {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TText(detail.title, style = TerminalTheme.type.display, maxLines = 2)
                    TText(
                        "${detail.albums.size} albums · ${detail.songs.size} songs · ${formatLengthShort(detail.durationMs)}",
                        color = c.dim,
                    )
                }
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TButton("▶︎ play all", { actions.play(detail.songs, 0, detail.source) }, Modifier.weight(1f), filled = true, large = true)
                    TButton("⇄ shuffle", { actions.shuffle(detail.songs, detail.source) }, Modifier.weight(1f), large = true)
                }
            }
            if (detail.albums.isNotEmpty()) {
                item { SectionLabel("albums/") }
                albumRows(detail.albums, actions, pixelCovers)
            }
            item { SectionLabel("songs") }
            itemsIndexed(detail.songs, key = { _, s -> s.id }) { i, song ->
                TSongRow(
                    song, detail.songs, i, actions,
                    onPlay = { actions.play(detail.songs, i, detail.source) },
                    subtitle = song.album,
                    playing = playback.song?.id == song.id,
                )
            }
        }
    }
}

private fun LazyListScope.albumRows(albums: List<Album>, actions: HexaActions, pixelCovers: Boolean) {
    itemsIndexed(albums, key = { _, a -> "a${a.id}" }) { _, album ->
        val c = TerminalTheme.colors
        Column(Modifier.padding(horizontal = 12.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clickable(role = Role.Button, onClickLabel = "Open album") { actions.openAlbum(album) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PixelCover(album.artModel, album.albumKey, 8, Modifier.size(44.dp), pixelCovers)
                Column(Modifier.weight(1f)) {
                    TText("${album.title}/", color = c.accent2)
                    TText(listOfNotNull(album.year.takeIf { it > 0 }?.toString(), "${album.songs.size} songs").joinToString(" · "), color = c.dim)
                }
                TText("→", color = c.dim)
            }
            TDashedDivider()
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    TText(
        text,
        Modifier.padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 6.dp),
        color = TerminalTheme.colors.accent,
        style = TerminalTheme.type.body.copy(fontWeight = FontWeight.Bold),
    )
}

@Composable
private fun PlaylistScreen(detail: DetailState, playlist: Playlist, playback: PlaybackState, actions: HexaActions, pixelCovers: Boolean) {
    val c = TerminalTheme.colors
    val reorder = rememberReorderState()
    val rowPx = with(LocalDensity.current) { Metrics.listRow2Line.dp.toPx() }
    val songs = detail.songs
    TScreen(
        header = {
            THeader(
                "~/lib/playlists/", current = slug(detail.title), onBack = actions::back,
                action = "[e]dit", actionDescription = "Edit playlist", onAction = { actions.editPlaylist(playlist) },
            )
        },
        status = {
            if (reorder.group != null && reorder.index in songs.indices) {
                val to = reorder.target(rowPx, songs.size)
                TStatusLine(TMode.MOV, "${songs[reorder.index].title} ${reorder.index + 1} → ${to + 1}", position = "${to + 1}/${songs.size}")
            } else {
                TStatusLine(TMode.NOR, "playlist", position = "${playingIndex(detail, playback) + 1}/${songs.size}")
            }
        },
    ) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    val first = songs.firstOrNull()
                    Box(Modifier.size(104.dp)) {
                        PixelCover(playlist.coverUri ?: first?.artModel, playlist.coverUri ?: first?.albumKey, 12, Modifier.fillMaxSize(), pixelCovers)
                        Box(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(26.dp)
                                .background(c.panel.copy(alpha = 0.9f))
                                .clickable(role = Role.Button, onClickLabel = "Change cover") { actions.pickPlaylistCover(playlist) },
                            contentAlignment = Alignment.Center,
                        ) {
                            TText("cover", color = c.dim, style = TerminalTheme.type.small)
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        TText(detail.title, style = TerminalTheme.type.title, maxLines = 2)
                        TText("${songs.size} songs · ${formatLengthShort(detail.durationMs)}", color = c.dim)
                        TText("edited ${formatDay(playlist.lastEdited, lower = true)}", color = c.dim)
                        TText("local playlist", color = c.dim)
                    }
                }
            }
            item {
                Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TButton("▶︎ play", { actions.play(songs, 0, detail.source) }, Modifier.weight(1f), filled = true, large = true, enabled = songs.isNotEmpty())
                    TButton("⇄ shuffle", { actions.shuffle(songs, detail.source) }, Modifier.weight(1f), large = true, enabled = songs.isNotEmpty())
                    TButton("+", { actions.addSongsTo(playlist) }, Modifier.width(64.dp).semantics { contentDescription = "Add songs" }, large = true)
                }
                TDivider(Modifier.padding(top = 12.dp))
            }
            if (songs.isEmpty()) {
                item { TText("empty playlist: + adds songs", Modifier.padding(12.dp), color = c.dim) }
            }
            itemsIndexed(songs, key = { i, s -> "$i:${s.id}" }) { i, song ->
                PlaylistRow(detail, playlist, i, playback, actions, reorder, rowPx)
            }
        }
    }
}

@Composable
private fun PlaylistRow(
    detail: DetailState,
    playlist: Playlist,
    index: Int,
    playback: PlaybackState,
    actions: HexaActions,
    reorder: ReorderState,
    rowPx: Float,
) {
    val c = TerminalTheme.colors
    val songs = detail.songs
    val song = songs[index]
    val dragging = reorder.isDragging("pl", index)
    Row(
        Modifier
            .fillMaxWidth()
            .reorderItem(reorder, "pl", index)
            .background(if (dragging) c.selection else c.background),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            TSongRow(
                song, songs, index, actions,
                onPlay = { actions.play(songs, index, detail.source) },
                title = song.title,
                subtitle = song.artist,
                playing = playback.song?.id == song.id,
                playlist = playlist,
            )
        }
        Box(
            Modifier
                .size(44.dp, Metrics.listRow2Line.dp)
                .semantics { contentDescription = "Drag to reorder ${song.title}" }
                .reorderHandle(reorder, "pl", index, songs.size, rowPx) { from, to ->
                    actions.movePlaylistSong(playlist, songs[from], songs[to])
                },
            contentAlignment = Alignment.Center,
        ) {
            TText("≡", color = if (dragging) c.warn else c.dim, style = TerminalTheme.type.body.copy(textAlign = TextAlign.Center))
        }
    }
}
