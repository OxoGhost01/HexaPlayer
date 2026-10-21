package com.oxoghost.hexaplayer.ui.terminal.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.oxoghost.hexaplayer.ui.common.SwipeToQueue
import com.oxoghost.hexaplayer.ui.common.formatDuration
import com.oxoghost.hexaplayer.ui.state.HexaActions
import com.oxoghost.hexaplayer.ui.state.PlaybackState
import com.oxoghost.hexaplayer.ui.state.SearchScope
import com.oxoghost.hexaplayer.ui.state.SearchState
import com.oxoghost.hexaplayer.ui.state.highlight
import com.oxoghost.hexaplayer.ui.terminal.components.THeader
import com.oxoghost.hexaplayer.ui.terminal.components.TInput
import com.oxoghost.hexaplayer.ui.terminal.components.TMode
import com.oxoghost.hexaplayer.ui.terminal.components.TStatusLine
import com.oxoghost.hexaplayer.ui.terminal.components.TText
import com.oxoghost.hexaplayer.ui.terminal.theme.TerminalTheme

@Composable
fun TSearchScreen(search: SearchState, playback: PlaybackState, actions: HexaActions) {
    val c = TerminalTheme.colors
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (search.query.isEmpty()) focus.requestFocus() }
    TScreen(
        header = { THeader("~/find", action = "[:set]", actionDescription = "Settings", onAction = actions::openSettings) },
        status = {
            TStatusLine(
                TMode.FIND,
                if (search.active) "/${search.query}" else "type to search",
                position = if (search.active) "${search.hits} hits" else null,
            )
        },
    ) {
        Box(Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp)) {
            TInput(
                value = search.query,
                onValueChange = actions::setQuery,
                modifier = Modifier.focusRequester(focus),
                prompt = "/",
                placeholder = "songs, albums, artists",
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { actions.commitSearch() }),
                trailing = if (search.active) {
                    { TText("${search.hits} hits", color = c.dim) }
                } else null,
            )
        }
        ScopeRow(search.scope, actions::setScope)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
            if (!search.active) {
                history(search, actions)
            } else {
                results(search, playback, actions)
            }
        }
    }
}

@Composable
private fun ScopeRow(scope: SearchScope, onSelect: (SearchScope) -> Unit) {
    val c = TerminalTheme.colors
    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        SearchScope.entries.forEach { s ->
            val on = s == scope
            val label = s.name.lowercase()
            Box(
                Modifier
                    .height(44.dp)
                    .background(if (on) c.selection else Color.Transparent)
                    .selectable(on, role = Role.RadioButton) { onSelect(s) }
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                TText(if (on) "[$label]" else label, color = if (on) c.accent else c.foreground)
            }
        }
    }
}

private fun LazyListScope.groupTitle(name: String, count: Int) {
    item {
        TText(
            buildAnnotatedString {
                withStyle(SpanStyle(color = TerminalTheme.colors.accent, fontWeight = FontWeight.Bold)) { append(name) }
                withStyle(SpanStyle(color = TerminalTheme.colors.dim)) { append(" ($count)") }
            },
            Modifier.padding(start = 12.dp, top = 14.dp, bottom = 4.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun LazyListScope.results(search: SearchState, playback: PlaybackState, actions: HexaActions) {
    val q = search.query
    if (search.songs.isNotEmpty()) {
        groupTitle("songs", search.songs.size)
        items(search.songs, key = { "s${it.id}" }) { song ->
            val c = TerminalTheme.colors
            SwipeToQueue(onSwipe = { actions.addToQueue(song) }, color = c.accent) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .combinedClickable(
                            onClick = {
                                actions.commitSearch()
                                actions.play(search.songs, search.songs.indexOf(song), "search · $q")
                            },
                            onLongClick = { actions.songMenu(song) },
                        )
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TText(
                        buildAnnotatedString {
                            val playing = playback.song?.id == song.id
                            withStyle(SpanStyle(color = if (playing) c.accent else c.foreground)) {
                                append(highlight(song.title, q, c.accent))
                            }
                            withStyle(SpanStyle(color = c.dim)) { append(" ") }
                            append(highlight(song.artist, q, c.accent).let { a ->
                                buildAnnotatedString { withStyle(SpanStyle(color = c.dim)) { append(a) } }
                            })
                        },
                        Modifier.weight(1f),
                    )
                    TText(formatDuration(song.duration), color = c.dim)
                }
            }
        }
    }
    if (search.albums.isNotEmpty()) {
        groupTitle("albums", search.albums.size)
        items(search.albums, key = { "a${it.id}" }) { album ->
            ResultDir(highlight(album.title, q, TerminalTheme.colors.accent), "${album.songs.size}") {
                actions.commitSearch()
                actions.openAlbum(album)
            }
        }
    }
    if (search.artists.isNotEmpty()) {
        groupTitle("artists", search.artists.size)
        items(search.artists, key = { "r${it.name}" }) { artist ->
            ResultDir(highlight(artist.name, q, TerminalTheme.colors.accent), "${artist.songs.size}") {
                actions.commitSearch()
                actions.openArtist(artist.name)
            }
        }
    }
    if (search.hits == 0) {
        item { TText("no match for \"$q\"", Modifier.padding(12.dp), color = TerminalTheme.colors.dim) }
    }
}

@Composable
private fun ResultDir(title: androidx.compose.ui.text.AnnotatedString, trailing: String, onClick: () -> Unit) {
    val c = TerminalTheme.colors
    Row(
        Modifier.fillMaxWidth().height(44.dp).clickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TText(
            buildAnnotatedString {
                withStyle(SpanStyle(color = c.accent2)) { append(title) }
                withStyle(SpanStyle(color = c.accent2)) { append("/") }
            },
            Modifier.weight(1f),
        )
        TText(trailing, color = c.dim)
    }
}

private fun LazyListScope.history(search: SearchState, actions: HexaActions) {
    if (search.history.isEmpty()) {
        item { TText("search songs, albums and artists", Modifier.padding(12.dp), color = TerminalTheme.colors.dim) }
        return
    }
    item {
        val c = TerminalTheme.colors
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TText("history", Modifier.weight(1f), color = c.dim)
            Box(Modifier.height(44.dp).clickable(onClick = actions::clearHistory).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                TText("clear", color = c.error)
            }
        }
        FlowRow(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            search.history.forEach { h ->
                Box(
                    Modifier
                        .height(44.dp)
                        .border(1.dp, c.border)
                        .clickable { actions.setQuery(h) }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) { TText(h) }
            }
        }
    }
}
