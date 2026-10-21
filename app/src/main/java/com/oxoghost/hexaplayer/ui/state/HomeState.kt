package com.oxoghost.hexaplayer.ui.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import com.oxoghost.hexaplayer.data.Album
import com.oxoghost.hexaplayer.data.Song
import com.oxoghost.hexaplayer.viewmodel.MusicViewModel

/** Home until play history exists (phase 5): recently added songs and albums, library counts. */
@Immutable
data class HomeState(
    val allSongs: List<Song>,
    val recentSongs: List<Song>,
    val recentAlbums: List<Album>,
    val songCount: Int,
    val albumCount: Int,
    val artistCount: Int,
)

@Composable
fun rememberHomeState(vm: MusicViewModel): HomeState {
    val songs by vm.songs.observeAsState(emptyList())
    val albums by vm.albums.observeAsState(emptyList())
    val artists by vm.artists.observeAsState(emptyList())
    return remember(songs, albums, artists) {
        val recent = songs.sortedByDescending { it.dateAdded }
        val byId = albums.associateBy { it.id }
        HomeState(
            allSongs = songs,
            recentSongs = recent.take(8),
            recentAlbums = recent.asSequence().map { it.albumId }.distinct().mapNotNull { byId[it] }.filter { it.songs.size > 1 }.take(10).toList(),
            songCount = songs.size,
            albumCount = albums.count { it.songs.size > 1 },
            artistCount = artists.size,
        )
    }
}
