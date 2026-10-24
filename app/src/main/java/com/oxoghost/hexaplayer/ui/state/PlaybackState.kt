package com.oxoghost.hexaplayer.ui.state

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.media3.common.Player
import com.oxoghost.hexaplayer.data.Album
import com.oxoghost.hexaplayer.data.Playlist
import com.oxoghost.hexaplayer.data.Song
import com.oxoghost.hexaplayer.repository.CoverChange
import com.oxoghost.hexaplayer.viewmodel.MusicViewModel

enum class RepeatState { Off, All, One }

@Immutable
data class PlaybackState(
    val song: Song? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeat: RepeatState = RepeatState.Off,
    val sleepEndsAt: Long? = null,
    val source: String? = null,
    val queueIndex: Int = 0,
    val queueSize: Int = 0,
    val gapless: Boolean = true,
) {
    val fraction: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

@Composable
fun rememberPlaybackState(vm: MusicViewModel): PlaybackState {
    val song by vm.currentSong.observeAsState()
    val playing by vm.isPlaying.observeAsState(false)
    val position by vm.currentPosition.observeAsState(0L)
    val duration by vm.duration.observeAsState(0L)
    val shuffle by vm.shuffleMode.observeAsState(false)
    val repeat by vm.repeatMode.observeAsState(Player.REPEAT_MODE_OFF)
    val sleep by vm.sleepTimerEndsAt.observeAsState()
    val source by vm.queueSource.observeAsState()
    val queue by vm.currentQueue.observeAsState(emptyList())
    val index by vm.currentQueueIndex.observeAsState(0)
    return PlaybackState(
        song = song,
        isPlaying = playing,
        positionMs = position,
        durationMs = duration.takeIf { it > 0 } ?: (song?.duration ?: 0L),
        shuffle = shuffle,
        repeat = when (repeat) {
            Player.REPEAT_MODE_ALL -> RepeatState.All
            Player.REPEAT_MODE_ONE -> RepeatState.One
            else -> RepeatState.Off
        },
        sleepEndsAt = sleep,
        source = source,
        queueIndex = index,
        queueSize = queue.size,
        gapless = vm.gaplessPlayback,
    )
}

interface PlaybackActions {
    fun togglePlayPause()
    fun next()
    fun previous()
    fun seekTo(positionMs: Long)
    fun toggleShuffle()
    fun cycleRepeat()
    fun play(songs: List<Song>, index: Int, source: String? = null)
    fun shuffle(songs: List<Song>, source: String? = null)
    fun addToQueue(song: Song)
    fun addAllToQueue(songs: List<Song>)
    fun playNext(songs: List<Song>)
    fun resumeRestored()
    fun forgetRestored()
}

enum class Tab { Home, Library, Search, Download }

interface NavActions {
    fun openTab(tab: Tab)
    fun openPlayer()
    fun openLyrics()
    fun openQueue()
    fun openSettings()
    fun openEqualizer()
    fun openSearch()
    fun openAlbum(album: Album)
    fun openArtist(name: String)
    fun openPlaylist(playlist: Playlist)
    fun openSetup()
    fun openAppearance()
    fun openSettingsPage(page: SettingsPage)
    fun openWrapped()
    /** [name] of a custom flavor to edit, null for a new one based on the current flavor. */
    fun openFlavorEditor(name: String?)
    fun back()
}

/** Sheets, playlists, sleep timer and tag editing. */
interface MenuActions {
    fun songMenu(song: Song, playlist: Playlist? = null)
    fun songAction(song: Song, action: SongAction, playlist: Playlist?)
    fun addToPlaylist(songs: List<Song>, label: String? = null)
    fun addToPlaylists(songs: List<Song>, playlistIds: List<String>, newName: String?)
    fun createPlaylist()
    fun editPlaylist(playlist: Playlist)
    fun savePlaylist(playlist: Playlist?, name: String, cover: CoverChange)
    fun deletePlaylist(playlist: Playlist)
    fun pickPlaylistCover(playlist: Playlist)
    fun pickImage(onPicked: (Uri) -> Unit)
    fun addSongsTo(playlist: Playlist)
    fun removeFromPlaylist(playlist: Playlist, song: Song)
    fun movePlaylistSong(playlist: Playlist, from: Song, to: Song)
    fun sleepTimer()
    fun setSleepTimer(seconds: Long)
    fun cancelSleepTimer()
    fun editSongs(songs: List<Song>)
    fun saveTags(edit: EditState)
    fun closeSheet()
}

/** Actions on the songs picked in multi-select. */
interface SelectionActions {
    fun selectionPlayNext()
    fun selectionQueue()
    fun selectionAddTo()
    fun selectionEdit()
}

/** Empty library and first launch. */
interface SetupActions {
    fun rescan()
    fun chooseFolder()
    fun scanAllAudio()
}

interface LibraryActions {
    fun selectSection(section: LibrarySection)
    fun cycleSort(section: LibrarySection)
    fun setCursor(section: LibrarySection, index: Int)
    fun openFolder(path: String, name: String)
    fun folderUp()
}

interface SearchActions {
    fun setQuery(query: String)
    fun setScope(scope: SearchScope)
    fun commitSearch()
    fun clearHistory()
}

interface QueueActions {
    fun playAt(exoIndex: Int)
    fun remove(exoIndex: Int)
    fun move(fromExo: Int, toExo: Int)
    fun clear()
}

interface LyricsActions {
    fun seekToLine(index: Int)
    fun shiftOffset(deltaMs: Long)
}

/** Everything a screen can do. Both skins call the same implementation. */
interface HexaActions : PlaybackActions, NavActions, MenuActions, SelectionActions, SetupActions, LibraryActions, SearchActions, QueueActions, LyricsActions
