package com.oxoghost.hexaplayer.ui.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import com.oxoghost.hexaplayer.data.Album
import com.oxoghost.hexaplayer.data.Artist
import com.oxoghost.hexaplayer.data.Song
import com.oxoghost.hexaplayer.data.db.HexaDatabase
import com.oxoghost.hexaplayer.viewmodel.MusicViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Immutable
data class SearchState(
    val query: String,
    val scope: SearchScope,
    val songs: List<Song>,
    val albums: List<Album>,
    val artists: List<Artist>,
    val history: List<String>,
    val lyrics: List<LyricHit> = emptyList(),
) {
    val hits: Int get() = songs.size + albums.size + artists.size + lyrics.size
    val active: Boolean get() = query.isNotBlank()
}

@Composable
fun rememberSearchState(vm: MusicViewModel, ui: UiModel): SearchState {
    val songs by vm.songs.observeAsState(emptyList())
    val albums by vm.albums.observeAsState(emptyList())
    val artists by vm.artists.observeAsState(emptyList())
    val q = ui.searchQuery.trim()
    val scope = ui.searchScope
    val results = remember(q, scope, songs, albums, artists) {
        if (q.isEmpty()) Triple(emptyList<Song>(), emptyList<Album>(), emptyList<Artist>())
        else Triple(
            if (scope == SearchScope.All || scope == SearchScope.Songs)
                songs.filter { it.title.contains(q, true) || it.artist.contains(q, true) }.take(50) else emptyList(),
            if (scope == SearchScope.All || scope == SearchScope.Albums)
                albums.filter { it.title.contains(q, true) || it.artist.contains(q, true) }.take(30) else emptyList(),
            if (scope == SearchScope.All || scope == SearchScope.Artists)
                artists.filter { it.name.contains(q, true) }.take(30) else emptyList(),
        )
    }
    // Only lyrics already fetched once are searchable (they live in the local cache).
    val lyrics by produceState(emptyList<LyricHit>(), q, scope, songs) {
        value = if (q.length < 3 || (scope != SearchScope.All && scope != SearchScope.Lyrics)) emptyList()
        else withContext(Dispatchers.IO) {
            val byId = songs.associateBy { it.id }
            HexaDatabase.get(vm.getApplication()).lyrics().search(q, 30).mapNotNull { c ->
                val song = byId[c.songId] ?: return@mapNotNull null
                val line = c.text.lineSequence().firstOrNull { it.contains(q, ignoreCase = true) } ?: return@mapNotNull null
                LyricHit(song, line.trim())
            }
        }
    }
    return SearchState(ui.searchQuery, scope, results.first, results.second, results.third, ui.searchHistory.toList(), lyrics)
}

/** A song whose cached lyrics contain the query, with the matching line. */
@Immutable
data class LyricHit(val song: Song, val line: String)

/** [text] with the first case-insensitive match of [query] colored (and underlined when asked). */
fun highlight(text: String, query: String, color: Color, underline: Boolean = true): AnnotatedString {
    val q = query.trim()
    val i = if (q.isEmpty()) -1 else text.indexOf(q, ignoreCase = true)
    if (i < 0) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text.substring(0, i))
        withStyle(SpanStyle(color = color, textDecoration = if (underline) TextDecoration.Underline else null)) {
            append(text.substring(i, i + q.length))
        }
        append(text.substring(i + q.length))
    }
}
