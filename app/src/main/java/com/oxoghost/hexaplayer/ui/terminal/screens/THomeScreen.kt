package com.oxoghost.hexaplayer.ui.terminal.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.oxoghost.hexaplayer.ui.common.formatDuration
import com.oxoghost.hexaplayer.ui.state.HexaActions
import com.oxoghost.hexaplayer.ui.state.HomeState
import com.oxoghost.hexaplayer.ui.state.PlaybackState
import com.oxoghost.hexaplayer.ui.state.Tab
import com.oxoghost.hexaplayer.ui.terminal.components.TButton
import com.oxoghost.hexaplayer.ui.terminal.components.THeader
import com.oxoghost.hexaplayer.ui.terminal.components.TListRow
import com.oxoghost.hexaplayer.ui.terminal.components.TMode
import com.oxoghost.hexaplayer.ui.terminal.components.TSectionBox
import com.oxoghost.hexaplayer.ui.terminal.components.TStatusLine
import com.oxoghost.hexaplayer.ui.terminal.components.TText
import com.oxoghost.hexaplayer.ui.terminal.theme.TerminalTheme

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun THomeScreen(home: HomeState, playback: PlaybackState, actions: HexaActions) {
    val c = TerminalTheme.colors
    TScreen(
        header = { THeader("~/home", action = "[:set]", actionDescription = "Settings", onAction = actions::openSettings) },
        status = { TStatusLine(TMode.NOR, "home", position = "${home.songCount} songs · ${home.albumCount} albums") },
    ) {
        LazyColumn(
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 21.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            if (home.recentSongs.isNotEmpty()) {
                item {
                    TSectionBox("recently added", contentPadding = PaddingValues(top = 12.dp, bottom = 6.dp)) {
                        home.recentSongs.forEachIndexed { i, song ->
                            val playing = playback.song?.id == song.id
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .combinedClickable(
                                        onClick = { actions.play(home.recentSongs, i, "recently added") },
                                        onLongClick = { actions.songMenu(song) },
                                    )
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                TText("%02d".format(i + 1), color = c.dim)
                                TText(
                                    buildAnnotatedString {
                                        withStyle(SpanStyle(color = if (playing) c.accent else c.foreground)) { append(song.title) }
                                        withStyle(SpanStyle(color = c.dim)) { append(" ${song.artist}") }
                                    },
                                    Modifier.weight(1f),
                                )
                                TText(formatDuration(song.duration), color = c.dim)
                            }
                        }
                    }
                }
            }
            item {
                TSectionBox("library", titleColor = c.accent2) {
                    TText("${home.songCount} songs · ${home.albumCount} albums · ${home.artistCount} artists", color = c.dim)
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TButton("▶︎ play all", { actions.play(home.allSongs, 0, "library") }, filled = true)
                        TButton("⇄ shuffle", { actions.shuffle(home.allSongs, "library") })
                        TButton("lib →", { actions.openTab(Tab.Library) }, color = c.accent2)
                    }
                }
            }
            if (home.recentAlbums.isNotEmpty()) {
                item {
                    TSectionBox("albums · recently added", contentPadding = PaddingValues(top = 12.dp, bottom = 6.dp)) {
                        home.recentAlbums.forEach { album ->
                            TListRow(
                                title = album.title,
                                trailing = "${album.songs.size}",
                                directory = true,
                                onClick = { actions.openAlbum(album) },
                                onLongClick = { actions.addToPlaylist(album.songs) },
                            )
                        }
                    }
                }
            }
        }
    }
}
