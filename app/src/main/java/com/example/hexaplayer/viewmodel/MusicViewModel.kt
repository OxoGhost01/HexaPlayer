package com.example.hexaplayer.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.hexaplayer.data.Album
import com.example.hexaplayer.data.Artist
import com.example.hexaplayer.data.LyricsState
import com.example.hexaplayer.data.Playlist
import com.example.hexaplayer.data.Song
import com.example.hexaplayer.repository.LyricsRepository
import com.example.hexaplayer.repository.MusicRepository
import com.example.hexaplayer.repository.PlaylistRepository
import com.example.hexaplayer.service.MusicService
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    val musicRepository = MusicRepository(application)
    val playlistRepository = PlaylistRepository(application)
    private val lyricsRepository = LyricsRepository()

    // Lyrics cache + state
    private val lyricsCache = mutableMapOf<Long, LyricsState>()
    private val _lyricsState = MutableLiveData<LyricsState>(LyricsState.NotFound)
    val lyricsState: LiveData<LyricsState> = _lyricsState

    private val prefs = application.getSharedPreferences("hexa_prefs", Context.MODE_PRIVATE)

    private var controller: MediaController? = null
    private var queue: List<Song> = emptyList()

    private val _currentQueue = MutableLiveData<List<Song>>(emptyList())
    val currentQueue: LiveData<List<Song>> = _currentQueue

    private val _currentQueueIndex = MutableLiveData(0)
    val currentQueueIndex: LiveData<Int> = _currentQueueIndex

    private fun updateQueue(newQueue: List<Song>) {
        queue = newQueue
        _currentQueue.postValue(newQueue)
    }

    private val _songs = MutableLiveData<List<Song>>(emptyList())
    val songs: LiveData<List<Song>> = _songs

    private val _albums = MutableLiveData<List<Album>>(emptyList())
    val albums: LiveData<List<Album>> = _albums

    private val _artists = MutableLiveData<List<Artist>>(emptyList())
    val artists: LiveData<List<Artist>> = _artists

    private val _playlists = MutableLiveData<List<Playlist>>(emptyList())
    val playlists: LiveData<List<Playlist>> = _playlists

    private val _currentSong = MutableLiveData<Song?>(null)
    val currentSong: LiveData<Song?> = _currentSong

    private val _isPlaying = MutableLiveData(false)
    val isPlaying: LiveData<Boolean> = _isPlaying

    private val _currentPosition = MutableLiveData(0L)
    val currentPosition: LiveData<Long> = _currentPosition

    private val _duration = MutableLiveData(0L)
    val duration: LiveData<Long> = _duration

    private val _shuffleMode = MutableLiveData(false)
    val shuffleMode: LiveData<Boolean> = _shuffleMode

    private val _repeatMode = MutableLiveData(Player.REPEAT_MODE_OFF)
    val repeatMode: LiveData<Int> = _repeatMode

    private val _sleepTimerActive = MutableLiveData(false)
    val sleepTimerActive: LiveData<Boolean> = _sleepTimerActive

    private val sleepTimerHandler = Handler(Looper.getMainLooper())
    private var sleepTimerRunnable: Runnable? = null

    var musicFolder: String
        get() = prefs.getString("music_folder", "") ?: ""
        set(v) { prefs.edit().putString("music_folder", v).apply() }

    /** 0=Title↑ 1=Title↓ 2=Artist↑ 3=Album↑ 4=Duration↑ 5=Duration↓ 6=Date↓ 7=Date↑ */
    var sortOrder: Int
        get() = prefs.getInt("sort_order", 0)
        set(v) { prefs.edit().putInt("sort_order", v).apply() }

    var showUnknownSongs: Boolean
        get() = prefs.getBoolean("show_unknown", true)
        set(v) { prefs.edit().putBoolean("show_unknown", v).apply() }

    var skipSilence: Boolean
        get() = prefs.getBoolean("skip_silence", false)
        set(v) { prefs.edit().putBoolean("skip_silence", v).apply() }

    var gaplessPlayback: Boolean
        get() = prefs.getBoolean("gapless", true)
        set(v) { prefs.edit().putBoolean("gapless", v).apply() }

    /** Crossfade duration in tenths of a second (0 = off, 10 = 1s, 50 = 5s). */
    var crossfadeTenths: Int
        get() = prefs.getInt("crossfade_tenths", 0)
        set(v) { prefs.edit().putInt("crossfade_tenths", v).apply() }

    /** Index of the selected accent theme (0–5). */
    var accentIndex: Int
        get() = prefs.getInt("accent_index", 0)
        set(v) { prefs.edit().putInt("accent_index", v).apply() }

    /** Resume playback automatically when skipping while paused. */
    var resumeOnTrackChange: Boolean
        get() = prefs.getBoolean("resume_on_track_change", true)
        set(v) { prefs.edit().putBoolean("resume_on_track_change", v).apply() }

    private val handler = Handler(Looper.getMainLooper())
    private val positionRunnable = object : Runnable {
        override fun run() {
            controller?.let {
                _currentPosition.value = it.currentPosition
                _duration.value = it.duration.coerceAtLeast(0L)
            }
            handler.postDelayed(this, 200)
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.postValue(isPlaying)
            if (isPlaying) handler.post(positionRunnable)
            else handler.removeCallbacks(positionRunnable)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            syncCurrentSong()
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            _shuffleMode.postValue(shuffleModeEnabled)
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            _repeatMode.postValue(repeatMode)
        }
    }

    fun connectToService() {
        val token = SessionToken(
            getApplication(),
            ComponentName(getApplication(), MusicService::class.java)
        )
        val future = MediaController.Builder(getApplication(), token).buildAsync()
        future.addListener({
            try {
                controller = future.get()
                controller?.addListener(playerListener)
                syncCurrentSong()
            } catch (_: Exception) { }
        }, MoreExecutors.directExecutor())
    }

    fun loadLibrary() {
        viewModelScope.launch(Dispatchers.IO) {
            val filter = musicFolder.ifBlank { null }
            var songList = musicRepository.getAllSongs(filter, sortOrder)
            if (!showUnknownSongs) {
                songList = songList.filter { s ->
                    s.title.isNotBlank() && s.title != "Unknown" &&
                    s.artist.isNotBlank() && s.artist != "Unknown Artist"
                }
            }
            _songs.postValue(songList)
            _albums.postValue(musicRepository.groupByAlbums(songList))
            _artists.postValue(musicRepository.groupByArtists(songList))
        }
        refreshPlaylists()
    }

    fun refreshPlaylists() {
        _playlists.value = playlistRepository.getAll()
    }

    fun playQueue(songs: List<Song>, startIndex: Int = 0) {
        val ctrl = controller ?: return
        updateQueue(songs)
        _currentQueueIndex.value = startIndex
        val items = songs.map { it.toMediaItem() }
        ctrl.setMediaItems(items, startIndex, 0L)
        ctrl.prepare()
        ctrl.play()
        if (startIndex < songs.size) _currentSong.value = songs[startIndex]
    }

    fun playQueueShuffled(songs: List<Song>) {
        val ctrl = controller ?: return
        val shuffled = songs.shuffled()
        updateQueue(shuffled)
        _currentQueueIndex.value = 0
        val items = shuffled.map { it.toMediaItem() }
        ctrl.setMediaItems(items, 0, 0L)
        ctrl.shuffleModeEnabled = true
        ctrl.prepare()
        ctrl.play()
        _currentSong.value = shuffled.firstOrNull()
    }

    /** Insert [song] right after the currently playing item. */
    fun addToQueueNext(song: Song) {
        val ctrl = controller ?: return
        val insertAt = (ctrl.currentMediaItemIndex + 1).coerceAtMost(ctrl.mediaItemCount)
        ctrl.addMediaItem(insertAt, song.toMediaItem())
        val newQueue = queue.toMutableList().apply { add(insertAt, song) }
        updateQueue(newQueue)
    }

    /** Append [song] to the end of the queue. */
    fun addToQueueLast(song: Song) {
        val ctrl = controller ?: return
        ctrl.addMediaItem(song.toMediaItem())
        updateQueue(queue + song)
    }

    /** Remove the item at [index] from the queue. */
    fun removeFromQueue(index: Int) {
        val ctrl = controller ?: return
        if (index < 0 || index >= ctrl.mediaItemCount) return
        ctrl.removeMediaItem(index)
        val newQueue = queue.toMutableList().apply { removeAt(index) }
        updateQueue(newQueue)
    }

    /** Seek to the item at [index] in the queue. */
    fun playQueueItemAt(index: Int) {
        controller?.seekTo(index, 0L)
    }

    /** Move queue item from [from] to [to] index (drag-to-reorder). */
    fun moveQueueItem(from: Int, to: Int) {
        val ctrl = controller ?: return
        ctrl.moveMediaItem(from, to)
        val newQueue = queue.toMutableList().apply {
            add(to, removeAt(from))
        }
        queue = newQueue
        _currentQueue.postValue(newQueue)
    }

    fun togglePlayPause() {
        controller?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun playNext() {
        val ctrl = controller ?: return
        val shouldResume = resumeOnTrackChange && !ctrl.isPlaying
        ctrl.seekToNextMediaItem()
        if (shouldResume) ctrl.play()
    }

    fun playPrevious() {
        val ctrl = controller ?: return
        val shouldResume = resumeOnTrackChange && !ctrl.isPlaying
        ctrl.seekToPreviousMediaItem()
        if (shouldResume) ctrl.play()
    }

    fun seekTo(positionMs: Long) { controller?.seekTo(positionMs) }

    fun toggleShuffle() {
        val ctrl = controller ?: return
        val wasPlaying = ctrl.isPlaying
        val nowShuffled = _shuffleMode.value ?: false
        if (!nowShuffled) {
            // Turning ON: keep current song first, randomize the rest
            val currentIdx = ctrl.currentMediaItemIndex.coerceAtLeast(0)
            val currentPos = ctrl.currentPosition
            val currentSong = queue.getOrNull(currentIdx)
            val rest = queue.toMutableList()
            if (currentSong != null && currentIdx < rest.size) rest.removeAt(currentIdx)
            rest.shuffle()
            val newQueue = if (currentSong != null) listOf(currentSong) + rest else rest
            updateQueue(newQueue)
            val items = newQueue.map { it.toMediaItem() }
            ctrl.setMediaItems(items, 0, currentPos)
            ctrl.prepare()
            if (wasPlaying) ctrl.play()  // only resume if was already playing
        }
        ctrl.shuffleModeEnabled = !nowShuffled
    }

    fun toggleRepeat() {
        controller?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    fun isControllerReady() = controller != null

    fun setSleepTimer(seconds: Long) {
        sleepTimerRunnable?.let { sleepTimerHandler.removeCallbacks(it) }
        sleepTimerRunnable = null
        if (seconds <= 0) {
            _sleepTimerActive.value = false
            return
        }
        _sleepTimerActive.value = true
        val runnable = Runnable {
            controller?.pause()
            _sleepTimerActive.postValue(false)
            sleepTimerRunnable = null
        }
        sleepTimerRunnable = runnable
        sleepTimerHandler.postDelayed(runnable, seconds * 1_000L)
    }

    fun cancelSleepTimer() {
        sleepTimerRunnable?.let { sleepTimerHandler.removeCallbacks(it) }
        sleepTimerRunnable = null
        _sleepTimerActive.value = false
    }

    private fun syncCurrentSong() {
        val ctrl = controller ?: return
        val idx = ctrl.currentMediaItemIndex
        if (idx >= 0 && idx < queue.size) {
            val song = queue[idx]
            _currentSong.postValue(song)
            _duration.postValue(ctrl.duration.coerceAtLeast(0L))
            _currentQueueIndex.postValue(idx)
            fetchLyricsForSong(song)
        }
    }

    private fun fetchLyricsForSong(song: Song) {
        val cached = lyricsCache[song.id]
        if (cached != null) {
            _lyricsState.postValue(cached)
            return
        }
        _lyricsState.postValue(LyricsState.Loading)
        viewModelScope.launch {
            val result = lyricsRepository.fetchLyrics(song)
            lyricsCache[song.id] = result
            _lyricsState.postValue(result)
        }
    }

    fun createPlaylist(name: String, coverUri: String? = null): Playlist {
        val pl = Playlist(name = name, coverUri = coverUri)
        playlistRepository.save(pl)
        refreshPlaylists()
        return pl
    }

    fun deletePlaylist(playlistId: String) {
        playlistRepository.delete(playlistId)
        refreshPlaylists()
    }

    fun editPlaylist(playlistId: String, newName: String? = null, newCoverUri: String? = null) {
        val list = playlistRepository.getAll().toMutableList()
        val idx = list.indexOfFirst { it.id == playlistId }
        if (idx < 0) return
        val current = list[idx]
        list[idx] = current.copy(
            name = newName ?: current.name,
            coverUri = if (newCoverUri != null) newCoverUri else current.coverUri
        )
        playlistRepository.save(list[idx])
        refreshPlaylists()
    }

    fun addSongToPlaylist(playlistId: String, songId: Long) {
        playlistRepository.addSong(playlistId, songId)
        refreshPlaylists()
    }

    fun songsForPlaylist(playlist: Playlist): List<Song> {
        val allSongs = _songs.value ?: return emptyList()
        return playlist.songIds.mapNotNull { id -> allSongs.firstOrNull { it.id == id } }
    }

    override fun onCleared() {
        super.onCleared()
        handler.removeCallbacks(positionRunnable)
        sleepTimerRunnable?.let { sleepTimerHandler.removeCallbacks(it) }
        controller?.removeListener(playerListener)
        controller?.release()
    }
}

private fun Song.toMediaItem(): MediaItem =
    MediaItem.Builder()
        .setUri(uri)
        .setMediaId(id.toString())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .build()
        )
        .build()
