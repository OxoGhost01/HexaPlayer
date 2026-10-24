package com.oxoghost.hexaplayer.ui.terminal.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.oxoghost.hexaplayer.data.Song
import com.oxoghost.hexaplayer.ui.common.formatClock
import com.oxoghost.hexaplayer.ui.common.formatDay
import com.oxoghost.hexaplayer.ui.common.formatDuration
import com.oxoghost.hexaplayer.ui.common.plural
import com.oxoghost.hexaplayer.ui.state.HexaActions
import com.oxoghost.hexaplayer.ui.state.HomeState
import com.oxoghost.hexaplayer.ui.state.PlaybackState
import com.oxoghost.hexaplayer.ui.state.ResumeInfo
import com.oxoghost.hexaplayer.ui.state.Tab
import com.oxoghost.hexaplayer.ui.terminal.components.TButton
import com.oxoghost.hexaplayer.ui.terminal.components.TCharBarRow
import com.oxoghost.hexaplayer.ui.terminal.components.TCharProgress
import com.oxoghost.hexaplayer.ui.terminal.components.THeader
import com.oxoghost.hexaplayer.ui.terminal.components.TListRow
import com.oxoghost.hexaplayer.ui.terminal.components.TMode
import com.oxoghost.hexaplayer.ui.terminal.components.TSectionBox
import com.oxoghost.hexaplayer.ui.terminal.components.TStatusLine
import com.oxoghost.hexaplayer.ui.terminal.components.TText
import com.oxoghost.hexaplayer.ui.terminal.theme.TerminalTheme

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
            home.resume?.let { resume -> item { Resume(resume, actions) } }
            home.wrapped?.let { w ->
                item {
                    TSectionBox("wrapped ${w.year}", titleColor = c.accent2) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                TText(if (w.hours > 0 || w.songs > 0) "your year so far" else "starts with your first plays")
                                TText("${w.time} · ${plural(w.songs, "song")} · ${plural(w.artists, "artist")}", color = c.dim)
                            }
                            TButton("open", actions::openWrapped, color = c.accent2)
                        }
                    }
                }
            }
            val played = home.recentlyPlayed.isNotEmpty()
            val list = if (played) home.recentlyPlayed.take(8) else home.recentSongs
            if (list.isNotEmpty()) {
                item {
                    SongBox(if (played) "recent" else "recently added", list, playback, actions, if (played) "recently played" else "recently added")
                }
            }
            if (home.mostPlayed.isNotEmpty()) {
                item {
                    TSectionBox("most played · ${home.monthLabel}") {
                        val max = home.mostPlayed.maxOf { it.plays }
                        home.mostPlayed.forEach { p ->
                            TCharBarRow(p.title, p.plays, max)
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
                                onLongClick = { actions.addToPlaylist(album.songs, album.title) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Resume(resume: ResumeInfo, actions: HexaActions) {
    val c = TerminalTheme.colors
    TSectionBox("resume", borderColor = c.accent) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (resume.savedAt > 0) {
                TText("last session · ${formatDay(resume.savedAt, lower = true)} ${formatClock(resume.savedAt)}", color = c.dim)
            }
            TText(buildAnnotatedString {
                append(resume.title)
                withStyle(SpanStyle(color = c.dim)) { append(" ${resume.artist}") }
            })
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TCharProgress(resume.fraction, Modifier.weight(1f))
                if (resume.durationMs > 0) TText("${formatDuration(resume.positionMs)}/${formatDuration(resume.durationMs)}", color = c.dim)
            }
            TText(
                "queue ${resume.position}/${resume.size}" + (resume.source?.let { " · from ${it.substringAfter(" · ", it)}" } ?: ""),
                color = c.dim,
            )
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TButton("▶︎ resume", actions::resumeRestored, filled = true, large = true)
                TButton("forget", actions::forgetRestored, large = true)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SongBox(title: String, songs: List<Song>, playback: PlaybackState, actions: HexaActions, source: String) {
    val c = TerminalTheme.colors
    TSectionBox(title, contentPadding = PaddingValues(top = 12.dp, bottom = 6.dp)) {
        songs.forEachIndexed { i, song ->
            val playing = playback.song?.id == song.id
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .combinedClickable(
                        onClick = { actions.play(songs, i, source) },
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
