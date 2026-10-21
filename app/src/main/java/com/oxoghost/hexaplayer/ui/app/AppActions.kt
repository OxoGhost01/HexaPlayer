package com.oxoghost.hexaplayer.ui.app

import android.content.Context
import android.net.Uri
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import com.oxoghost.hexaplayer.R
import com.oxoghost.hexaplayer.data.Album
import com.oxoghost.hexaplayer.data.LyricsState
import com.oxoghost.hexaplayer.data.Playlist
import com.oxoghost.hexaplayer.data.Song
import com.oxoghost.hexaplayer.repository.CoverChange
import com.oxoghost.hexaplayer.ui.nav.Routes
import com.oxoghost.hexaplayer.ui.state.EditState
import com.oxoghost.hexaplayer.ui.state.HexaActions
import com.oxoghost.hexaplayer.ui.state.LibrarySection
import com.oxoghost.hexaplayer.ui.state.SearchScope
import com.oxoghost.hexaplayer.ui.state.Sheet
import com.oxoghost.hexaplayer.ui.state.SongAction
import com.oxoghost.hexaplayer.ui.state.Tab
import com.oxoghost.hexaplayer.ui.state.UiModel
import com.oxoghost.hexaplayer.util.toast
import com.oxoghost.hexaplayer.viewmodel.MusicViewModel

/** The one implementation of every screen action, shared by both skins. */
class AppActions(
    private val context: Context,
    private val vm: MusicViewModel,
    private val ui: UiModel,
    private val nav: NavHostController,
    private val system: SystemAccess,
) : HexaActions {

    // Playback
    override fun togglePlayPause() = vm.togglePlayPause()
    override fun next() = vm.playNext()
    override fun previous() = vm.playPrevious()
    override fun seekTo(positionMs: Long) = vm.seekTo(positionMs)
    override fun toggleShuffle() = vm.toggleShuffle()
    override fun cycleRepeat() = vm.toggleRepeat()
    override fun play(songs: List<Song>, index: Int, source: String?) = vm.playQueue(songs, index.coerceAtLeast(0), source)
    override fun shuffle(songs: List<Song>, source: String?) = vm.playQueueShuffled(songs, source)

    override fun addToQueue(song: Song) {
        vm.addToQueueLast(song)
        context.toast(context.getString(R.string.added_to_queue))
    }

    override fun addAllToQueue(songs: List<Song>) {
        songs.forEach { vm.addToQueueLast(it) }
        if (songs.isNotEmpty()) context.toast(context.getString(R.string.added_to_queue))
    }

    /** Each song goes right after the current one, so the list is added backwards to keep its order. */
    override fun playNext(songs: List<Song>) {
        songs.asReversed().forEach { vm.addToQueueNext(it) }
        if (songs.isNotEmpty()) context.toast(context.getString(R.string.plays_next))
    }

    // Navigation
    override fun openTab(tab: Tab) {
        nav.navigate(Routes.of(tab)) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    override fun openPlayer() = go(Routes.PLAYER)
    override fun openLyrics() = go(Routes.LYRICS)
    override fun openQueue() = go(Routes.QUEUE)
    override fun openSettings() = go(Routes.SETTINGS)
    override fun openEqualizer() = go(Routes.EQUALIZER)
    override fun openSearch() = openTab(Tab.Search)
    override fun openAlbum(album: Album) = go(Routes.album(album.id))
    override fun openArtist(name: String) = go(Routes.artist(name))
    override fun openPlaylist(playlist: Playlist) = go(Routes.playlist(playlist.id))
    override fun back() {
        nav.navigateUp()
    }

    private fun go(route: String) = nav.navigate(route) { launchSingleTop = true }

    // Sheets and playlists
    override fun songMenu(song: Song, playlist: Playlist?) {
        ui.sheet = Sheet.SongActions(song, playlist)
    }

    override fun songAction(song: Song, action: SongAction, playlist: Playlist?) {
        closeSheet()
        when (action) {
            SongAction.PlayNext -> playNext(listOf(song))
            SongAction.Queue -> addToQueue(song)
            SongAction.Playlist -> addToPlaylist(listOf(song))
            SongAction.Album -> vm.albums.value?.firstOrNull { it.id == song.albumId }?.let(::openAlbum)
            SongAction.Artist -> openArtist(song.artist)
            SongAction.Edit -> editSongs(listOf(song))
            SongAction.Remove -> playlist?.let { removeFromPlaylist(it, song) }
            SongAction.Details -> ui.sheet = Sheet.FileDetails(song)
        }
    }

    override fun addToPlaylist(songs: List<Song>, label: String?) {
        if (songs.isNotEmpty()) ui.sheet = Sheet.AddTo(songs, label)
    }

    override fun addToPlaylists(songs: List<Song>, playlistIds: List<String>, newName: String?) {
        val ids = songs.map { it.id }
        val targets = playlistIds.toMutableList()
        newName?.trim()?.takeIf { it.isNotEmpty() }?.let { targets += vm.createPlaylist(it).id }
        targets.forEach { vm.addSongsToPlaylist(it, ids) }
        closeSheet()
        ui.selection.clear()
        if (targets.isEmpty()) return
        context.toast(
            when {
                targets.size > 1 -> "${ids.size} songs added to ${targets.size} playlists"
                ids.size == 1 -> context.getString(R.string.song_added)
                else -> context.getString(R.string.songs_added_to, ids.size, vm.playlists.value?.firstOrNull { it.id == targets.first() }?.name.orEmpty())
            }
        )
    }

    override fun createPlaylist() {
        ui.sheet = Sheet.PlaylistEditor(null)
    }

    override fun editPlaylist(playlist: Playlist) {
        ui.sheet = Sheet.PlaylistEditor(playlist)
    }

    override fun savePlaylist(playlist: Playlist?, name: String, cover: CoverChange) {
        val trimmed = name.trim()
        val coverUri = (cover as? CoverChange.Set)?.uri?.toString()
        if (playlist == null) {
            if (trimmed.isEmpty()) return
            vm.createPlaylist(trimmed, coverUri)
            context.toast(context.getString(R.string.playlist_created))
        } else {
            vm.editPlaylist(playlist.id, trimmed.ifEmpty { null }, coverUri, clearCover = cover == CoverChange.Remove)
            context.toast(context.getString(R.string.playlist_updated))
        }
        closeSheet()
    }

    override fun deletePlaylist(playlist: Playlist) {
        vm.deletePlaylist(playlist.id)
        closeSheet()
        if (nav.currentBackStackEntry?.destination?.route == Routes.PLAYLIST) back()
        context.toast(context.getString(R.string.playlist_deleted))
    }

    override fun pickPlaylistCover(playlist: Playlist) =
        system.pickImage { vm.editPlaylist(playlist.id, newCoverUri = it.toString()) }

    override fun pickImage(onPicked: (Uri) -> Unit) = system.pickImage(onPicked)

    override fun addSongsTo(playlist: Playlist) {
        ui.sheet = Sheet.AddSongs(playlist)
    }

    override fun removeFromPlaylist(playlist: Playlist, song: Song) {
        vm.removeFromPlaylist(playlist.id, song.id)
        context.toast("Removed from ${playlist.name}")
    }

    override fun movePlaylistSong(playlist: Playlist, from: Song, to: Song) = vm.movePlaylistSong(playlist.id, from.id, to.id)

    override fun sleepTimer() {
        ui.sheet = Sheet.Sleep
    }

    override fun setSleepTimer(seconds: Long) {
        vm.setSleepTimer(seconds)
        closeSheet()
    }

    override fun cancelSleepTimer() {
        vm.cancelSleepTimer()
        closeSheet()
    }

    override fun editSongs(songs: List<Song>) {
        if (songs.isEmpty()) return
        closeSheet()
        ui.selection.clear()
        go(Routes.edit(songs.map { it.id }))
    }

    override fun saveTags(edit: EditState) {
        if (!edit.dirty || edit.saving) return
        edit.saving = true
        system.requestWrite(edit.songs.map { it.uri }) { granted ->
            if (!granted) {
                edit.saving = false
                context.toast(context.getString(R.string.metadata_permission_needed))
                return@requestWrite
            }
            vm.saveTags(edit.songs, edit.changes, edit.cover) { written ->
                edit.saving = false
                when {
                    written == edit.songs.size -> {
                        context.toast(context.getString(R.string.song_info_updated))
                        back()
                    }
                    written == 0 -> context.toast("Could not write the tags into this file")
                    else -> context.toast("$written of ${edit.songs.size} files updated")
                }
            }
        }
    }

    override fun closeSheet() {
        ui.sheet = null
    }

    // Multi-select
    override fun selectionPlayNext() {
        playNext(ui.selection.songs)
        ui.selection.clear()
    }

    override fun selectionQueue() {
        addAllToQueue(ui.selection.songs)
        ui.selection.clear()
    }

    override fun selectionAddTo() = addToPlaylist(ui.selection.songs)
    override fun selectionEdit() = editSongs(ui.selection.songs)

    // Empty library and first launch
    override fun rescan() = vm.loadLibrary()

    override fun chooseFolder() = system.pickFolder { path ->
        vm.musicFolder = path
        vm.loadLibrary()
    }

    override fun scanAllAudio() {
        vm.musicFolder = ""
        vm.loadLibrary()
    }

    // Library
    override fun selectSection(section: LibrarySection) {
        ui.selection.clear()
        ui.section = section
    }

    override fun cycleSort(section: LibrarySection) = ui.cycleSort(section)
    override fun setCursor(section: LibrarySection, index: Int) {
        ui.cursors[section] = index
    }

    override fun openFolder(path: String, name: String) {
        ui.folderStack.add(path to name)
        ui.cursors[LibrarySection.Folders] = 0
    }

    override fun folderUp() {
        if (ui.folderStack.isNotEmpty()) ui.folderStack.removeAt(ui.folderStack.lastIndex)
        ui.cursors[LibrarySection.Folders] = 0
    }

    // Search
    override fun setQuery(query: String) {
        ui.searchQuery = query
    }

    override fun setScope(scope: SearchScope) {
        ui.searchScope = scope
    }

    override fun commitSearch() = ui.rememberSearch(ui.searchQuery)
    override fun clearHistory() = ui.clearSearchHistory()

    // Queue
    override fun playAt(exoIndex: Int) = vm.playQueueItemAt(exoIndex)
    override fun remove(exoIndex: Int) = vm.removeFromQueue(exoIndex)
    override fun move(fromExo: Int, toExo: Int) = vm.moveQueueItem(fromExo, toExo)
    override fun clear() = vm.clearUpcoming()

    // Lyrics
    override fun seekToLine(index: Int) {
        val ts = (vm.lyricsState.value as? LyricsState.Found)?.timestamps ?: return
        val t = ts.getOrNull(index) ?: return
        vm.seekTo((t + (vm.lyricsOffsetMs.value ?: 0L)).coerceAtLeast(0L))
    }

    override fun shiftOffset(deltaMs: Long) {
        val song = vm.currentSong.value ?: return
        vm.setLyricsOffset(song.id, (vm.lyricsOffsetMs.value ?: 0L) + deltaMs)
    }
}
