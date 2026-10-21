package com.oxoghost.hexaplayer.ui.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import com.oxoghost.hexaplayer.data.Album
import com.oxoghost.hexaplayer.data.Artist
import com.oxoghost.hexaplayer.data.Playlist
import com.oxoghost.hexaplayer.data.Song
import com.oxoghost.hexaplayer.viewmodel.MusicViewModel

data class FolderEntry(val path: String, val name: String, val count: Int)

@Immutable
data class LibraryState(
    val section: LibrarySection,
    val sort: SortKey,
    val songs: List<Song>,
    val albums: List<Album>,
    val artists: List<Artist>,
    val playlists: List<Playlist>,
    val playlistSongs: Map<String, List<Song>>,
    val folderCrumbs: List<String>,
    val folders: List<FolderEntry>,
    val folderSongs: List<Song>,
    val cursor: Int,
    val loading: Boolean,
    val totalSongs: Int,
) {
    val canGoUp: Boolean get() = folderCrumbs.size > 1
    val itemCount: Int
        get() = when (section) {
            LibrarySection.Songs -> songs.size
            LibrarySection.Albums -> albums.size
            LibrarySection.Artists -> artists.size
            LibrarySection.Playlists -> playlists.size
            LibrarySection.Folders -> folders.size + folderSongs.size
        }
}

@Composable
fun rememberLibraryState(vm: MusicViewModel, ui: UiModel): LibraryState {
    val songs by vm.songs.observeAsState(emptyList())
    val albums by vm.albums.observeAsState(emptyList())
    val artists by vm.artists.observeAsState(emptyList())
    val playlists by vm.playlists.observeAsState(emptyList())
    val loading by vm.isLoading.observeAsState(false)
    val section = ui.section
    val sort = ui.sortOf(section)
    val stack = ui.folderStack.toList()

    val sortedSongs = remember(songs, ui.sortOf(LibrarySection.Songs)) { sortSongs(songs, ui.sortOf(LibrarySection.Songs)) }
    val sortedAlbums = remember(albums, ui.sortOf(LibrarySection.Albums)) {
        val real = albums.filter { it.songs.size > 1 }
        when (ui.sortOf(LibrarySection.Albums)) {
            SortKey.NameDesc -> real.sortedByDescending { it.title.lowercase() }
            SortKey.Artist -> real.sortedBy { it.artist.lowercase() }
            SortKey.SongCount -> real.sortedByDescending { it.songs.size }
            else -> real.sortedBy { it.title.lowercase() }
        }
    }
    val sortedArtists = remember(artists, ui.sortOf(LibrarySection.Artists)) {
        when (ui.sortOf(LibrarySection.Artists)) {
            SortKey.NameDesc -> artists.sortedByDescending { it.name.lowercase() }
            SortKey.SongCount -> artists.sortedByDescending { it.songs.size }
            else -> artists.sortedBy { it.name.lowercase() }
        }
    }
    val playlistSongs = remember(playlists, songs) {
        val byId = songs.associateBy { it.id }
        playlists.associate { p -> p.id to p.songIds.mapNotNull { byId[it] } }
    }
    val sortedPlaylists = remember(playlists, ui.sortOf(LibrarySection.Playlists)) {
        when (ui.sortOf(LibrarySection.Playlists)) {
            SortKey.NameAsc -> playlists.sortedBy { it.name.lowercase() }
            SortKey.NameDesc -> playlists.sortedByDescending { it.name.lowercase() }
            else -> playlists.sortedByDescending { it.createdAt }
        }
    }
    val root = remember(vm.musicFolder) {
        vm.musicFolder.trim().let { f -> if (f.isBlank()) "" else if (f.endsWith("/")) f else "$f/" }
    }
    val path = stack.lastOrNull()?.first ?: root
    val folderSort = ui.sortOf(LibrarySection.Folders)
    val (folders, folderSongs) = remember(songs, path, folderSort) {
        val (subs, direct) = vm.musicRepository.getFolderContents(songs, path)
        val entries = subs.map { sub ->
            val (s2, d2) = vm.musicRepository.getFolderContents(songs, sub)
            FolderEntry(sub, sub.removeSuffix("/").substringAfterLast('/'), s2.size + d2.size)
        }
        val desc = folderSort == SortKey.NameDesc
        val f = if (desc) entries.sortedByDescending { it.name.lowercase() } else entries.sortedBy { it.name.lowercase() }
        val d = if (desc) direct.sortedByDescending { it.title.lowercase() } else direct.sortedBy { it.title.lowercase() }
        f to d
    }
    val crumbs = buildList {
        add(root.removeSuffix("/").ifBlank { "storage" })
        stack.forEach { add(it.second) }
    }
    return LibraryState(
        section = section,
        sort = sort,
        songs = sortedSongs,
        albums = sortedAlbums,
        artists = sortedArtists,
        playlists = sortedPlaylists,
        playlistSongs = playlistSongs,
        folderCrumbs = crumbs,
        folders = folders,
        folderSongs = folderSongs,
        cursor = ui.cursors[section] ?: 0,
        loading = loading,
        totalSongs = songs.size,
    )
}

fun sortSongs(songs: List<Song>, sort: SortKey): List<Song> = when (sort) {
    SortKey.TitleDesc -> songs.sortedByDescending { it.title.lowercase() }
    SortKey.Artist -> songs.sortedWith(compareBy({ it.artist.lowercase() }, { it.title.lowercase() }))
    SortKey.Album -> songs.sortedWith(compareBy({ it.album.lowercase() }, { it.title.lowercase() }))
    SortKey.Duration -> songs.sortedByDescending { it.duration }
    SortKey.DateAdded -> songs.sortedByDescending { it.dateAdded }
    else -> songs.sortedBy { it.title.lowercase() }
}
