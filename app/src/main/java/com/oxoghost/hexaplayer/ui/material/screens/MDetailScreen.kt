package com.oxoghost.hexaplayer.ui.material.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oxoghost.hexaplayer.ui.common.CoverImage
import com.oxoghost.hexaplayer.ui.common.SwipeToQueue
import com.oxoghost.hexaplayer.ui.common.Symbols
import com.oxoghost.hexaplayer.ui.common.albumKey
import com.oxoghost.hexaplayer.ui.common.artModel
import com.oxoghost.hexaplayer.ui.common.formatDuration
import com.oxoghost.hexaplayer.ui.material.components.MButtonSize
import com.oxoghost.hexaplayer.ui.material.components.MCircleButton
import com.oxoghost.hexaplayer.ui.material.components.MIconButton
import com.oxoghost.hexaplayer.ui.material.components.MListRow
import com.oxoghost.hexaplayer.ui.material.components.MPillButton
import com.oxoghost.hexaplayer.ui.material.components.MSubTopBar
import com.oxoghost.hexaplayer.ui.material.theme.HexaShapes
import com.oxoghost.hexaplayer.ui.state.DetailState
import com.oxoghost.hexaplayer.ui.state.HexaActions
import com.oxoghost.hexaplayer.ui.state.PlaybackState

@Composable
fun MDetailScreen(detail: DetailState?, playback: PlaybackState, actions: HexaActions) {
    val s = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        MSubTopBar(detail?.title ?: "", onBack = actions::back, large = false) {
            detail?.playlist?.let { p ->
                MCircleButton(Symbols.MoreVert, "Playlist actions", { actions.playlistMenu(p) },
                    container = s.surfaceContainerHigh, content = s.onSurface, size = 48.dp)
            }
        }
        if (detail == null) {
            Text("This item is no longer in your library", Modifier.padding(24.dp), color = s.onSurfaceVariant)
            return@Column
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = LocalChromePadding.current)) {
            item {
                Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    val first = detail.songs.firstOrNull()
                    val art: Any? = detail.playlist?.coverUri ?: detail.album?.artModel ?: first?.artModel
                    CoverImage(art, detail.playlist?.coverUri ?: first?.albumKey, Modifier.size(132.dp),
                        if (detail.kind == "artist") CircleShape else HexaShapes.coverAlbumHeader)
                    Column(Modifier.weight(1f)) {
                        Text(detail.title, style = MaterialTheme.typography.headlineMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Text(detail.subtitle, color = s.primary, style = MaterialTheme.typography.titleSmall)
                        Text("${detail.songs.size} songs · ${formatDuration(detail.durationMs)}", color = s.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item {
                Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    MPillButton("Play", { actions.play(detail.songs, 0, detail.source) }, icon = Symbols.PlayArrow, size = MButtonSize.L)
                    MCircleButton(Symbols.Shuffle, "Shuffle", { actions.shuffle(detail.songs, detail.source) }, shape = RoundedCornerShape(18.dp))
                    MCircleButton(Symbols.PlaylistAdd, "Add all to a playlist", { actions.addToPlaylist(detail.songs) },
                        container = s.surfaceContainerHigh, content = s.onSurface)
                    MCircleButton(Symbols.QueueMusic, "Add all to queue", { actions.addAllToQueue(detail.songs) },
                        container = s.surfaceContainerHigh, content = s.onSurface)
                    detail.playlist?.let { p -> MIconButton(Symbols.Add, "Add songs", { actions.addSongsTo(p) }, tint = s.primary) }
                }
            }
            itemsIndexed(detail.songs, key = { i, song -> "$i${song.id}" }) { i, song ->
                SwipeToQueue(onSwipe = { actions.addToQueue(song) }, color = s.primary) {
                    MListRow(
                        title = song.title,
                        subtitle = "${song.artist} · ${formatDuration(song.duration)}",
                        playing = playback.song?.id == song.id,
                        onClick = { actions.play(detail.songs, i, detail.source) },
                        onLongClick = { actions.songMenu(song) },
                        onMore = { actions.songMenu(song) },
                    ) { CoverImage(song.artModel, song.albumKey, Modifier.fillMaxSize(), HexaShapes.coverRow) }
                }
            }
        }
    }
}
