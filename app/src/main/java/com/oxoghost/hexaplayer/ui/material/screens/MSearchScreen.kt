package com.oxoghost.hexaplayer.ui.material.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oxoghost.hexaplayer.ui.common.CoverImage
import com.oxoghost.hexaplayer.ui.common.SwipeToQueue
import com.oxoghost.hexaplayer.ui.common.Symbol
import com.oxoghost.hexaplayer.ui.common.Symbols
import com.oxoghost.hexaplayer.ui.common.albumKey
import com.oxoghost.hexaplayer.ui.common.artModel
import com.oxoghost.hexaplayer.ui.material.components.MButtonSize
import com.oxoghost.hexaplayer.ui.material.components.MButtonStyle
import com.oxoghost.hexaplayer.ui.material.components.MFilterChip
import com.oxoghost.hexaplayer.ui.material.components.MIconButton
import com.oxoghost.hexaplayer.ui.material.components.MPillButton
import com.oxoghost.hexaplayer.ui.material.components.MSectionLabel
import com.oxoghost.hexaplayer.ui.material.components.MSegment
import com.oxoghost.hexaplayer.ui.material.theme.HexaShapes
import com.oxoghost.hexaplayer.ui.state.HexaActions
import com.oxoghost.hexaplayer.ui.state.PlaybackState
import com.oxoghost.hexaplayer.ui.state.SearchScope
import com.oxoghost.hexaplayer.ui.state.SearchState
import com.oxoghost.hexaplayer.ui.state.highlight

@Composable
fun MSearchScreen(search: SearchState, playback: PlaybackState, actions: HexaActions) {
    val s = MaterialTheme.colorScheme
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (search.query.isEmpty()) focus.requestFocus() }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth()
                .height(56.dp)
                .clip(HexaShapes.pill)
                .background(s.surfaceContainerHigh)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MIconButton(Symbols.ArrowBack, "Back", actions::back, tint = s.onSurface, size = 48.dp)
            Box(Modifier.weight(1f).padding(horizontal = 8.dp), contentAlignment = Alignment.CenterStart) {
                if (search.query.isEmpty()) Text("Search your library", color = s.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                BasicTextField(
                    value = search.query,
                    onValueChange = actions::setQuery,
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = s.onSurface),
                    cursorBrush = SolidColor(s.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { actions.commitSearch() }),
                )
            }
            if (search.query.isNotEmpty()) MIconButton(Symbols.Close, "Clear", { actions.setQuery("") }, tint = s.onSurfaceVariant)
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SearchScope.entries.forEach { scope ->
                MFilterChip(scope.name, scope == search.scope, { actions.setScope(scope) })
            }
        }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = LocalChromePadding.current),
        ) {
            if (search.active) results(search, playback, actions) else history(search, actions)
        }
    }
}

private fun LazyListScope.results(search: SearchState, playback: PlaybackState, actions: HexaActions) {
    val q = search.query
    if (search.songs.isNotEmpty()) {
        item { MSectionLabel("Songs", Modifier.padding(top = 8.dp)) }
        itemsIndexed(search.songs, key = { _, song -> "s${song.id}" }) { i, song ->
            val s = MaterialTheme.colorScheme
            SwipeToQueue(onSwipe = { actions.addToQueue(song) }, color = s.primary, modifier = Modifier.padding(bottom = 2.dp)) {
                MSegment(i, search.songs.size, onClick = {
                    actions.commitSearch()
                    actions.play(search.songs, i, "search · $q")
                }, container = if (playback.song?.id == song.id) s.secondaryContainer else s.surfaceContainer) {
                    CoverImage(song.artModel, song.albumKey, Modifier.size(44.dp), HexaShapes.coverList)
                    Column(Modifier.weight(1f)) {
                        Text(highlight(song.title, q, s.primary), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(highlight(song.artist, q, s.primary), color = s.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    MIconButton(Symbols.MoreVert, "More actions for ${song.title}", { actions.songMenu(song) })
                }
            }
        }
    }
    if (search.albums.isNotEmpty()) {
        item { MSectionLabel("Albums", Modifier.padding(top = 16.dp)) }
        itemsIndexed(search.albums, key = { _, a -> "a${a.id}" }) { i, album ->
            ResultRow(i, search.albums.size, highlight(album.title, q, MaterialTheme.colorScheme.primary), "${album.artist} · ${album.songs.size} songs", {
                CoverImage(album.artModel, album.albumKey, Modifier.size(44.dp), HexaShapes.coverList)
            }) {
                actions.commitSearch()
                actions.openAlbum(album)
            }
        }
    }
    if (search.artists.isNotEmpty()) {
        item { MSectionLabel("Artists", Modifier.padding(top = 16.dp)) }
        itemsIndexed(search.artists, key = { _, a -> "r${a.name}" }) { i, artist ->
            val first = artist.songs.firstOrNull()
            ResultRow(i, search.artists.size, highlight(artist.name, q, MaterialTheme.colorScheme.primary), "${artist.songs.size} songs", {
                CoverImage(first?.artModel, first?.albumKey, Modifier.size(44.dp), CircleShape)
            }) {
                actions.commitSearch()
                actions.openArtist(artist.name)
            }
        }
    }
    if (search.hits == 0) {
        item { Text("No match for \"$q\"", Modifier.padding(vertical = 24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun ResultRow(
    index: Int,
    count: Int,
    title: androidx.compose.ui.text.AnnotatedString,
    subtitle: String,
    leading: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    val s = MaterialTheme.colorScheme
    MSegment(index, count, Modifier.padding(bottom = 2.dp), onClick = onClick) {
        leading()
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = s.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        }
        Symbol(Symbols.ChevronRight, null, tint = s.onSurfaceVariant)
    }
}

private fun LazyListScope.history(search: SearchState, actions: HexaActions) {
    if (search.history.isEmpty()) {
        item {
            Text("Search your songs, albums and artists", Modifier.padding(vertical = 24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
        return
    }
    item {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            MSectionLabel("Recent searches", Modifier.weight(1f))
            MPillButton("Clear", actions::clearHistory, style = MButtonStyle.Text, size = MButtonSize.S,
                contentColor = MaterialTheme.colorScheme.error)
        }
    }
    item {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            search.history.forEach { h ->
                MPillButton(h, { actions.setQuery(h) }, style = MButtonStyle.Tonal, size = MButtonSize.S, icon = Symbols.History)
            }
        }
    }
}
