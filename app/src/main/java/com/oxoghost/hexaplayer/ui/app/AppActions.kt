package com.oxoghost.hexaplayer.ui.app

import android.content.Context
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import com.oxoghost.hexaplayer.R
import com.oxoghost.hexaplayer.data.Album
import com.oxoghost.hexaplayer.data.LyricsState
import com.oxoghost.hexaplayer.data.Playlist
import com.oxoghost.hexaplayer.data.Song
import com.oxoghost.hexaplayer.ui.legacy.LegacyDialogs
import com.oxoghost.hexaplayer.ui.nav.Routes
import com.oxoghost.hexaplayer.ui.state.HexaActions
import com.oxoghost.hexaplayer.ui.state.LibrarySection
import com.oxoghost.hexaplayer.ui.state.SearchScope
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
    private val dialogs: LegacyDialogs,
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

    // 1.x dialogs
    override fun songMenu(song: Song) = dialogs.songMenu(song)
    override fun addToPlaylist(songs: List<Song>) = dialogs.addToPlaylist(songs)
    override fun createPlaylist() = dialogs.createPlaylist()
    override fun playlistMenu(playlist: Playlist) = dialogs.playlistMenu(playlist)
    override fun addSongsTo(playlist: Playlist) = dialogs.addSongsTo(playlist)
    override fun sleepTimer() = dialogs.sleepTimer()

    // Library
    override fun selectSection(section: LibrarySection) {
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
