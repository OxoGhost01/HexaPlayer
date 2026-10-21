package com.oxoghost.hexaplayer.ui.terminal.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.oxoghost.hexaplayer.ui.common.PixelCover
import com.oxoghost.hexaplayer.ui.common.SwipeToQueue
import com.oxoghost.hexaplayer.ui.common.albumKey
import com.oxoghost.hexaplayer.ui.common.artModel
import com.oxoghost.hexaplayer.ui.common.format
import com.oxoghost.hexaplayer.ui.common.formatDuration
import com.oxoghost.hexaplayer.ui.state.DetailState
import com.oxoghost.hexaplayer.ui.state.HexaActions
import com.oxoghost.hexaplayer.ui.state.PlaybackState
import com.oxoghost.hexaplayer.ui.terminal.components.TButton
import com.oxoghost.hexaplayer.ui.terminal.components.TDivider
import com.oxoghost.hexaplayer.ui.terminal.components.THeader
import com.oxoghost.hexaplayer.ui.terminal.components.TListRow
import com.oxoghost.hexaplayer.ui.terminal.components.TMode
import com.oxoghost.hexaplayer.ui.terminal.components.TStatusLine
import com.oxoghost.hexaplayer.ui.terminal.components.TText
import com.oxoghost.hexaplayer.ui.terminal.theme.TerminalTheme

@Composable
fun TDetailScreen(detail: DetailState?, playback: PlaybackState, actions: HexaActions, pixelCovers: Boolean) {
    val c = TerminalTheme.colors
    if (detail == null) {
        TScreen(header = { THeader("~/lib/", onBack = actions::back) }, status = { TStatusLine(TMode.NOR, "not found") }) {
            TText("this item is no longer in the library", Modifier.padding(12.dp), color = c.dim)
        }
        return
    }
    val playlist = detail.playlist
    TScreen(
        header = {
            THeader(
                "~/lib/${detail.kind}s/", current = detail.title, onBack = actions::back,
                action = if (playlist != null) "[e]dit" else null, actionDescription = "Edit playlist",
                onAction = { playlist?.let(actions::playlistMenu) },
            )
        },
        status = { TStatusLine(TMode.NOR, "${detail.kind} · ${detail.songs.size} songs", position = formatDuration(detail.durationMs)) },
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val first = detail.songs.firstOrNull()
            val art: Any? = playlist?.coverUri ?: detail.album?.artModel ?: first?.artModel
            PixelCover(art, playlist?.coverUri ?: first?.albumKey, 16, Modifier.size(96.dp), pixelCovers)
            Column {
                TText(detail.title, style = TerminalTheme.type.title)
                TText(detail.subtitle, color = c.accent2)
                TText("${detail.songs.size} songs · ${formatDuration(detail.durationMs)}", color = c.dim)
                first?.format?.takeIf { it.isNotEmpty() }?.let { TText(it, color = c.dim) }
            }
        }
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TButton("▶︎ play", { actions.play(detail.songs, 0, detail.source) }, filled = true)
            TButton("⇄", { actions.shuffle(detail.songs, detail.source) })
            TButton("+p", { actions.addToPlaylist(detail.songs) })
            TButton("+q", { actions.addAllToQueue(detail.songs) })
            if (playlist != null) TButton("+ songs", { actions.addSongsTo(playlist) }, color = c.accent2)
        }
        TDivider(Modifier.padding(top = 12.dp))
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp)) {
            itemsIndexed(detail.songs, key = { i, s -> "$i${s.id}" }) { i, song ->
                SwipeToQueue(onSwipe = { actions.addToQueue(song) }, color = c.accent) {
                    TListRow(
                        title = if (detail.album != null) "%02d  %s".format(i + 1, song.title) else song.title,
                        subtitle = if (detail.album != null) null else song.artist,
                        trailing = formatDuration(song.duration),
                        playing = playback.song?.id == song.id,
                        onClick = { actions.play(detail.songs, i, detail.source) },
                        onLongClick = { actions.songMenu(song) },
                    )
                }
            }
        }
    }
}
