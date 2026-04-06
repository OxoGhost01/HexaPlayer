package com.oxoghost.hexaplayer.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.media.MediaScannerConnection
import android.content.ContentUris
import android.net.Uri
import android.os.Bundle
import android.os.Environment
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
import com.oxoghost.hexaplayer.data.Album
import com.oxoghost.hexaplayer.data.Artist
import com.oxoghost.hexaplayer.data.LyricsState
import com.oxoghost.hexaplayer.data.Playlist
import com.oxoghost.hexaplayer.data.Song
import com.oxoghost.hexaplayer.repository.CoverRepository
import com.oxoghost.hexaplayer.repository.LyricsRepository
import com.oxoghost.hexaplayer.repository.MusicRepository
import com.oxoghost.hexaplayer.repository.PlaylistRepository
import com.oxoghost.hexaplayer.service.MusicService
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    val musicRepository = MusicRepository(application)
    val playlistRepository = PlaylistRepository(application)
    val coverRepository = CoverRepository(application)
    private val lyricsRepository = LyricsRepository()

    private val lyricsCache = mutableMapOf<Long, LyricsState>()
    private val _lyricsState = MutableLiveData<LyricsState>(LyricsState.NotFound)
    val lyricsState: LiveData<LyricsState> = _lyricsState

    private val prefs = application.getSharedPreferences("hexa_prefs", Context.MODE_PRIVATE)

    private var controller: MediaController? = null

    private var contextQueue: List<Song> = emptyList()
    private var contextCurrentIndex: Int = 0

    private data class UQEntry(val song: Song, val mediaId: String)
    private val uqEntries = mutableListOf<UQEntry>()

    private val _isPlayingFromUserQueue = MutableLiveData(false)
    val isPlayingFromUserQueue: LiveData<Boolean> = _isPlayingFromUserQueue

    private val _contextQueueLive = MutableLiveData<List<Song>>(emptyList())
    val currentQueue: LiveData<List<Song>> = _contextQueueLive

    private val _currentQueueIndex = MutableLiveData(0)
    val currentQueueIndex: LiveData<Int> = _currentQueueIndex

    private val _userQueueLive = MutableLiveData<List<Song>>(emptyList())
    val userQueueLive: LiveData<List<Song>> = _userQueueLive

    private var lastPlayedMediaId: String = ""

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

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

    var crossfadeTenths: Int
        get() = prefs.getInt("crossfade_tenths", 0)
        set(v) { prefs.edit().putInt("crossfade_tenths", v).apply() }

    var accentIndex: Int
        get() = prefs.getInt("accent_index", 0)
        set(v) { prefs.edit().putInt("accent_index", v).apply() }

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
            val prevId = lastPlayedMediaId
            val newId = mediaItem?.mediaId ?: ""
            lastPlayedMediaId = newId

            if (prevId.startsWith("uq:") && uqEntries.any { it.mediaId == prevId }) {
                uqEntries.removeAll { it.mediaId == prevId }
                _userQueueLive.postValue(uqEntries.map { it.song })
            }

            syncCurrentSong()
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

    fun setCustomCover(songId: Long, uri: String) {
        coverRepository.setOverride(songId, uri)
        loadLibrary()
    }

    fun clearCustomCover(songId: Long) {
        coverRepository.clearOverride(songId)
        loadLibrary()
    }

    fun loadLibrary() {
        _isLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val scanDirs = buildList {
                add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).absolutePath)
                val folder = musicFolder.ifBlank { null }
                if (folder != null) add(folder)
            }.distinct().toTypedArray()
            scanDirectories(scanDirs)

            val filter = musicFolder.ifBlank { null }
            var songList = musicRepository.getAllSongs(filter, sortOrder)
            if (!showUnknownSongs) {
                songList = songList.filter { s ->
                    s.title.isNotBlank() && s.title != "Unknown" &&
                    s.artist.isNotBlank() && s.artist != "Unknown Artist"
                }
            }
            songList = songList.map { song ->
                val override = coverRepository.getOverride(song.id)
                if (override != null) song.copy(customCoverUri = override) else song
            }
            _songs.postValue(songList)
            _albums.postValue(musicRepository.groupByAlbums(songList))
            _artists.postValue(musicRepository.groupByArtists(songList))
            _isLoading.postValue(false)
            handler.post { syncCurrentSong() }
        }
        refreshPlaylists()
    }

    private suspend fun scanDirectories(paths: Array<String>) =
        suspendCancellableCoroutine { cont ->
            var remaining = paths.size
            MediaScannerConnection.scanFile(getApplication(), paths, null) { _, _ ->
                if (--remaining == 0) cont.resume(Unit)
            }
        }

    fun refreshPlaylists() {
        _playlists.value = playlistRepository.getAll()
    }

    fun playQueue(songs: List<Song>, startIndex: Int = 0) {
        val ctrl = controller ?: return
        contextQueue = songs
        contextCurrentIndex = startIndex
        uqEntries.clear()
        lastPlayedMediaId = ""
        _userQueueLive.postValue(emptyList())
        _isPlayingFromUserQueue.value = false
        _shuffleMode.value = false

        val items = songs.mapIndexed { idx, song -> song.toCtxMediaItem(idx) }
        ctrl.setMediaItems(items, startIndex, 0L)
        ctrl.shuffleModeEnabled = false
        ctrl.prepare()
        ctrl.play()

        _contextQueueLive.postValue(songs)
        _currentQueueIndex.value = startIndex
        if (startIndex < songs.size) _currentSong.value = songs[startIndex]
    }

    fun playQueueShuffled(songs: List<Song>) {
        val ctrl = controller ?: return
        val shuffled = songs.shuffled()
        contextQueue = shuffled
        contextCurrentIndex = 0
        uqEntries.clear()
        lastPlayedMediaId = ""
        _userQueueLive.postValue(emptyList())
        _isPlayingFromUserQueue.value = false
        _shuffleMode.value = true

        val items = shuffled.mapIndexed { idx, song -> song.toCtxMediaItem(idx) }
        ctrl.setMediaItems(items, 0, 0L)
        ctrl.shuffleModeEnabled = false
        ctrl.prepare()
        ctrl.play()

        _contextQueueLive.postValue(shuffled)
        _currentQueueIndex.value = 0
        _currentSong.value = shuffled.firstOrNull()
    }

    fun addToQueueNext(song: Song) {
        val ctrl = controller ?: return
        val uid = "uq:${System.nanoTime()}"
        val insertAt = ctrl.currentMediaItemIndex + 1
        ctrl.addMediaItem(insertAt, song.toUqMediaItem(uid))
        uqEntries.add(0, UQEntry(song, uid))
        _userQueueLive.postValue(uqEntries.map { it.song })
    }

    fun addToQueueLast(song: Song) {
        val ctrl = controller ?: return
        val uid = "uq:${System.nanoTime()}"
        val insertAt = ctrl.currentMediaItemIndex + 1 + uqEntries.size
        ctrl.addMediaItem(insertAt, song.toUqMediaItem(uid))
        uqEntries.add(UQEntry(song, uid))
        _userQueueLive.postValue(uqEntries.map { it.song })
    }

    fun removeFromQueue(exoIndex: Int) {
        val ctrl = controller ?: return
        if (exoIndex < 0 || exoIndex >= ctrl.mediaItemCount) return
        val mediaId = ctrl.getMediaItemAt(exoIndex).mediaId

        var uqBefore = 0
        for (i in 0 until exoIndex) {
            if (ctrl.getMediaItemAt(i).mediaId.startsWith("uq:")) uqBefore++
        }

        ctrl.removeMediaItem(exoIndex)

        if (mediaId.startsWith("uq:")) {
            uqEntries.removeAll { it.mediaId == mediaId }
            _userQueueLive.postValue(uqEntries.map { it.song })
        } else {
            val ctxIdx = exoIndex - uqBefore
            if (ctxIdx in contextQueue.indices) {
                val newCtx = contextQueue.toMutableList().apply { removeAt(ctxIdx) }
                contextQueue = newCtx
                if (ctxIdx < contextCurrentIndex) contextCurrentIndex--
                _contextQueueLive.postValue(contextQueue)
            }
        }
    }

    fun playQueueItemAt(exoIndex: Int) {
        controller?.seekTo(exoIndex, 0L)
    }

    fun moveQueueItem(from: Int, to: Int) {
        val ctrl = controller ?: return
        val fromId = ctrl.getMediaItemAt(from).mediaId
        val toId   = ctrl.getMediaItemAt(to).mediaId
        ctrl.moveMediaItem(from, to)

        val fromIsUq = fromId.startsWith("uq:")
        val toIsUq   = toId.startsWith("uq:")

        when {
            fromIsUq && toIsUq -> {
                val uqFrom = uqEntries.indexOfFirst { it.mediaId == fromId }
                val uqTo   = uqEntries.indexOfFirst { it.mediaId == toId }
                if (uqFrom >= 0 && uqTo >= 0) {
                    val entry = uqEntries.removeAt(uqFrom)
                    uqEntries.add(uqTo, entry)
                    _userQueueLive.postValue(uqEntries.map { it.song })
                }
            }
            !fromIsUq && !toIsUq -> {
                var uqBeforeFrom = 0
                var uqBeforeTo   = 0
                for (i in 0 until ctrl.mediaItemCount) {
                    val id = ctrl.getMediaItemAt(i).mediaId
                    if (!id.startsWith("uq:")) continue
                    if (i < from) uqBeforeFrom++
                    if (i < to)   uqBeforeTo++
                }
                val ctxFrom = from - uqBeforeFrom
                val ctxTo   = to   - uqBeforeTo
                if (ctxFrom in contextQueue.indices && ctxTo in contextQueue.indices) {
                    val newCtx = contextQueue.toMutableList().apply { add(ctxTo, removeAt(ctxFrom)) }
                    contextQueue = newCtx
                    _contextQueueLive.postValue(contextQueue)
                }
            }
        }
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
        if (ctrl.currentPosition > 5_000L) {
            ctrl.seekTo(0L)
        } else {
            ctrl.seekToPreviousMediaItem()
            if (shouldResume) ctrl.play()
        }
    }

    fun seekTo(positionMs: Long) { controller?.seekTo(positionMs) }

    fun toggleShuffle() {
        val ctrl = controller ?: return
        val isShuffled = _shuffleMode.value ?: false

        if (!isShuffled) {
            val remaining = contextQueue.drop(contextCurrentIndex + 1).toMutableList()
            remaining.shuffle()
            contextQueue = contextQueue.take(contextCurrentIndex + 1) + remaining
            _contextQueueLive.postValue(contextQueue)

            val removeFrom = contextCurrentIndex + 1 + uqEntries.size
            val totalCount = ctrl.mediaItemCount
            if (removeFrom < totalCount) {
                ctrl.removeMediaItems(removeFrom, totalCount)
            }

            val newCtxItems = remaining.mapIndexed { i, s ->
                s.toCtxMediaItem(contextCurrentIndex + 1 + i)
            }
            if (newCtxItems.isNotEmpty()) {
                ctrl.addMediaItems(ctrl.mediaItemCount, newCtxItems)
            }

            _shuffleMode.postValue(true)
        } else {
            _shuffleMode.postValue(false)
        }
    }

    fun toggleRepeat() {
        controller?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF  -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL  -> Player.REPEAT_MODE_ONE
                else                    -> Player.REPEAT_MODE_OFF
            }
        }
    }

    fun isControllerReady() = controller != null

    fun setSleepTimer(seconds: Long) {
        sleepTimerRunnable?.let { sleepTimerHandler.removeCallbacks(it) }
        sleepTimerRunnable = null
        if (seconds <= 0) { _sleepTimerActive.value = false; return }
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
        val exoIdx = ctrl.currentMediaItemIndex
        if (exoIdx < 0) return
        val mediaId = ctrl.currentMediaItem?.mediaId ?: return

        val song: Song?
        if (mediaId.startsWith("uq:")) {
            _isPlayingFromUserQueue.postValue(true)
            song = uqEntries.firstOrNull { it.mediaId == mediaId }?.song
        } else {
            _isPlayingFromUserQueue.postValue(false)
            var uqBefore = 0
            for (i in 0 until exoIdx) {
                if (i < ctrl.mediaItemCount &&
                    ctrl.getMediaItemAt(i).mediaId.startsWith("uq:")) uqBefore++
            }
            val ctxIdx = (exoIdx - uqBefore).coerceAtLeast(0)
            contextCurrentIndex = ctxIdx
            _currentQueueIndex.postValue(ctxIdx)
            song = contextQueue.getOrNull(ctxIdx) ?: findSongFromMetadata(ctrl)
        }

        if (song != null) {
            _currentSong.postValue(song)
            _duration.postValue(ctrl.duration.coerceAtLeast(0L))
            fetchLyricsForSong(song)
        }
    }

    private fun findSongFromMetadata(ctrl: MediaController): Song? {
        val metadata = ctrl.mediaMetadata
        val title = metadata.title?.toString()?.takeIf { it.isNotBlank() } ?: return null

        val artist = metadata.artist?.toString() ?: ""
        val found = _songs.value?.firstOrNull { it.title == title && it.artist == artist }
            ?: _songs.value?.firstOrNull { it.title == title }
        if (found != null) return found

        val uri = ctrl.currentMediaItem?.localConfiguration?.uri ?: return null
        return Song(
            id           = -1L,
            uri          = uri,
            title        = title,
            artist       = artist,
            album        = metadata.albumTitle?.toString() ?: "",
            albumId      = 0L,
            duration     = ctrl.duration.coerceAtLeast(0L),
            relativePath = "",
            displayName  = title
        )
    }

    private fun fetchLyricsForSong(song: Song) {
        if (song.id < 0) return
        val cached = lyricsCache[song.id]
        if (cached != null) { _lyricsState.postValue(cached); return }
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

private fun Song.albumArtUri(): Uri = ContentUris.withAppendedId(
    Uri.parse("content://media/external/audio/albumart"), albumId
)

private fun Song.toCtxMediaItem(contextIdx: Int): MediaItem =
    MediaItem.Builder()
        .setUri(uri)
        .setMediaId("ctx:$contextIdx")
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(albumArtUri())
                .setExtras(Bundle().apply { putLong("albumId", albumId) })
                .build()
        )
        .build()

private fun Song.toUqMediaItem(uid: String): MediaItem =
    MediaItem.Builder()
        .setUri(uri)
        .setMediaId(uid)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(albumArtUri())
                .setExtras(Bundle().apply { putLong("albumId", albumId) })
                .build()
        )
        .build()
