package com.oxoghost.hexaplayer.ui.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import com.oxoghost.hexaplayer.data.Album
import com.oxoghost.hexaplayer.data.Playlist
import com.oxoghost.hexaplayer.data.Song
import com.oxoghost.hexaplayer.ui.common.year
import com.oxoghost.hexaplayer.viewmodel.MusicViewModel

sealed interface DetailKey {
    data class AlbumKey(val id: Long) : DetailKey
    data class ArtistKey(val name: String) : DetailKey
    data class PlaylistKey(val id: String) : DetailKey
}

/** Album, artist or playlist page. [albums] are the artist's albums, newest first. */
@Immutable
data class DetailState(
    val key: DetailKey,
    val kind: String,
    val title: String,
    val subtitle: String,
    val songs: List<Song>,
    val album: Album? = null,
    val playlist: Playlist? = null,
    val albums: List<Album> = emptyList(),
) {
    val durationMs: Long get() = songs.sumOf { it.duration }
    val source: String get() = "$kind · $title"
    val year: Int get() = songs.maxOfOrNull { it.year } ?: 0
}

@Composable
fun rememberDetailState(vm: MusicViewModel, key: DetailKey): DetailState? {
    val songs by vm.songs.observeAsState(emptyList())
    val albums by vm.albums.observeAsState(emptyList())
    val artists by vm.artists.observeAsState(emptyList())
    val playlists by vm.playlists.observeAsState(emptyList())
    return when (key) {
        is DetailKey.AlbumKey -> albums.firstOrNull { it.id == key.id }?.let {
            DetailState(key, "album", it.title, it.artist, it.songs, album = it)
        }
        is DetailKey.ArtistKey -> artists.firstOrNull { it.name == key.name }?.let {
            DetailState(
                key, "artist", it.name, "${it.albums.size} albums · ${it.songs.size} songs", it.songs,
                albums = it.albums.sortedWith(compareByDescending<Album> { a -> a.year }.thenBy { a -> a.title }),
            )
        }
        is DetailKey.PlaylistKey -> playlists.firstOrNull { it.id == key.id }?.let { p ->
            val byId = songs.associateBy { it.id }
            val list = p.songIds.mapNotNull { byId[it] }
            DetailState(key, "playlist", p.name, "${list.size} songs", list, playlist = p)
        }
    }
}
