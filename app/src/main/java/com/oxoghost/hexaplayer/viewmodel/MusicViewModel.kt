package com.oxoghost.hexaplayer.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.media.MediaScannerConnection
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
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.oxoghost.hexaplayer.data.Album
import com.oxoghost.hexaplayer.data.Artist
import com.oxoghost.hexaplayer.data.LyricsState
import com.oxoghost.hexaplayer.data.Playlist
import com.oxoghost.hexaplayer.data.Song
import com.oxoghost.hexaplayer.data.db.CachedLyrics
import com.oxoghost.hexaplayer.data.db.HexaDatabase
import com.oxoghost.hexaplayer.repository.CoverChange
import com.oxoghost.hexaplayer.repository.CoverRepository
import com.oxoghost.hexaplayer.repository.HistoryRepository
import com.oxoghost.hexaplayer.repository.LyricsRepository
import com.oxoghost.hexaplayer.repository.MusicRepository
import com.oxoghost.hexaplayer.repository.Period
import com.oxoghost.hexaplayer.repository.PlaylistRepository
import com.oxoghost.hexaplayer.repository.QueueStore
import com.oxoghost.hexaplayer.repository.SavedQueue
import com.oxoghost.hexaplayer.repository.TagField
import com.oxoghost.hexaplayer.repository.TagRepository
import com.oxoghost.hexaplayer.service.MusicService
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    val musicRepository = MusicRepository(application)
    val playlistRepository = PlaylistRepository(application)
    val coverRepository = CoverRepository(application)
    val tags = TagRepository(application)
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

    /** Wall clock time (ms) when the sleep timer stops playback, null when off. */
    private val _sleepTimerEndsAt = MutableLiveData<Long?>(null)
    val sleepTimerEndsAt: LiveData<Long?> = _sleepTimerEndsAt

    /** Label of what the context queue was started from ("album · Showbiz"), null if unknown. */
    private val _queueSource = MutableLiveData<String?>(null)
    val queueSource: LiveData<String?> = _queueSource

    /** Lyrics offset of the current song in ms (positive = lyrics later). */
    /** The queue restored at start, until it plays or is forgotten (Home's Resume card). */
    private val _restored = MutableLiveData<SavedQueue?>(null)
    val restored: LiveData<SavedQueue?> = _restored

    /** Bumped when a track changes, so play history views reload. */
    private val _historyVersion = MutableLiveData(0)
    val historyVersion: LiveData<Int> = _historyVersion

    private val _playCounts = MutableLiveData<Map<Long, Int>>(emptyMap())
    val playCounts: LiveData<Map<Long, Int>> = _playCounts

    val queueStore = QueueStore(application)
    val history = HistoryRepository(application)
    private var rebuildAfterLoad = false

    private val _lyricsOffsetMs = MutableLiveData(0L)
    val lyricsOffsetMs: LiveData<Long> = _lyricsOffsetMs

    private val sleepTimerHandler = Handler(Looper.getMainLooper())
    private var sleepTimerRunnable: Runnable? = null

    /** Length of the running sleep timer, for its progress ring. */
    var sleepTimerTotalMs = 0L
        private set

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
            if (isPlaying) _restored.postValue(null)
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
            // The service records the play a moment later; reload after it did.
            handler.postDelayed({ refreshHistory() }, 1_500)
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            _repeatMode.postValue(repeatMode)
        }
    }

    // Receives session-level events from the service. The shared shuffler lives in the
    // service, so when shuffle is toggled from EITHER the phone app or Android Auto the
    // service broadcasts the new state here, keeping both surfaces in sync.
    private val controllerListener = object : MediaController.Listener {
        override fun onExtrasChanged(controller: MediaController, extras: Bundle) {
            if (!extras.containsKey(MusicService.EXTRA_SHUFFLE_ON)) return
            _shuffleMode.postValue(extras.getBoolean(MusicService.EXTRA_SHUFFLE_ON))
            if (extras.getBoolean(MusicService.EXTRA_QUEUE_REORDERED, false)) {
                rebuildQueueFromPlayer()
            }
        }
    }

    fun connectToService() {
        val token = SessionToken(
            getApplication(),
            ComponentName(getApplication(), MusicService::class.java)
        )
        val future = MediaController.Builder(getApplication(), token)
            .setListener(controllerListener)
            .buildAsync()
        future.addListener({
            try {
                controller = future.get()
                controller?.addListener(playerListener)
                restoreQueue()
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

    /**
     * Writes tags into the files (write access must already be granted), rescans them and reloads
     * the library. [onDone] gets the number of files written.
     */
    fun saveTags(songs: List<Song>, changes: Map<TagField, String>, cover: CoverChange, onDone: (Int) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val written = songs.count { tags.write(it, changes, cover) }
            when (cover) {
                is CoverChange.Set -> songs.forEach { coverRepository.setOverride(it.id, cover.uri.toString()) }
                CoverChange.Remove -> songs.forEach { coverRepository.clearOverride(it.id) }
                CoverChange.Keep -> {}
            }
            tags.rescan(songs)
            withContext(Dispatchers.Main) {
                loadLibrary()
                onDone(written)
            }
        }
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
            handler.post {
                if (rebuildAfterLoad) {
                    rebuildAfterLoad = false
                    rebuildQueueFromPlayer()
                }
                syncCurrentSong()
            }
        }
        refreshPlaylists()
        refreshHistory()
    }

    fun refreshHistory() {
        viewModelScope.launch {
            _playCounts.value = withContext(Dispatchers.IO) { history.playCounts() }
            _historyVersion.value = (_historyVersion.value ?: 0) + 1
        }
    }

    /** Puts the remembered queue back in the player, paused, when nothing is loaded yet. */
    private fun restoreQueue() {
        val ctrl = controller ?: return
        if (ctrl.mediaItemCount > 0) return
        val saved = queueStore.load() ?: return
        ctrl.setMediaItems(saved.items.map { it.toMediaItem() }, saved.index, saved.positionMs)
        ctrl.repeatMode = saved.repeat
        ctrl.prepare()
        _queueSource.value = saved.source
        _restored.value = saved
        // The position is only polled while playing, so show the restored one now.
        _currentPosition.value = saved.positionMs
        if (saved.shuffle) {
            controller?.sendCustomCommand(
                SessionCommand(MusicService.CMD_SHUFFLE, Bundle.EMPTY),
                Bundle().apply { putBoolean(MusicService.EXTRA_FORCE_ON, true) }
            )
        }
        if (_songs.value.isNullOrEmpty()) rebuildAfterLoad = true else rebuildQueueFromPlayer()
    }

    /** Wrapped: makes a playlist of the 50 most played songs of [period]. [onDone] gets its name and size. */
    fun saveTopSongs(period: Period, onDone: (String, Int) -> Unit) {
        viewModelScope.launch {
            val top = withContext(Dispatchers.IO) { history.wrapped(period, topSongs = 50).topSongs }
            val inLibrary = _songs.value.orEmpty().map { it.id }.toSet()
            val ids = top.map { it.songId }.filter { it in inLibrary }
            if (ids.isEmpty()) return@launch
            val name = "Top ${ids.size} · ${period.label}"
            val playlist = createPlaylist(name)
            addSongsToPlaylist(playlist.id, ids)
            onDone(name, ids.size)
        }
    }

    /** Resume card: play the restored queue. */
    fun resumeRestored() {
        controller?.play()
        _restored.value = null
    }

    /** Resume card: drop the restored queue. */
    fun forgetRestored() {
        controller?.run {
            stop()
            clearMediaItems()
        }
        queueStore.clear()
        contextQueue = emptyList()
        uqEntries.clear()
        _contextQueueLive.value = emptyList()
        _userQueueLive.value = emptyList()
        _currentSong.value = null
        _queueSource.value = null
        _restored.value = null
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

    fun playQueue(songs: List<Song>, startIndex: Int = 0, source: String? = null) {
        val ctrl = controller ?: return
        _queueSource.value = source
        queueStore.source = source
        _restored.value = null
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

    fun playQueueShuffled(songs: List<Song>, source: String? = null) {
        if (songs.isEmpty()) return
        // Order is randomized here; we then tell the service shuffle is ON (force flag,
        // no extra reorder) so the icon state stays consistent across phone app and AA.
        playQueue(songs.shuffled(), 0, source)
        controller?.sendCustomCommand(
            SessionCommand(MusicService.CMD_SHUFFLE, Bundle.EMPTY),
            Bundle().apply { putBoolean(MusicService.EXTRA_FORCE_ON, true) }
        )
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

    /** Removes everything queued after the current song (user queue and context). */
    fun clearUpcoming() {
        val ctrl = controller ?: return
        val cur = ctrl.currentMediaItemIndex
        if (cur < 0 || cur >= ctrl.mediaItemCount - 1) return
        val currentId = ctrl.currentMediaItem?.mediaId
        ctrl.removeMediaItems(cur + 1, ctrl.mediaItemCount)
        uqEntries.removeAll { it.mediaId != currentId }
        _userQueueLive.postValue(uqEntries.map { it.song })
        contextQueue = contextQueue.take(contextCurrentIndex + 1)
        _contextQueueLive.postValue(contextQueue)
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
        // Shuffle is owned by the service so the phone app and Android Auto share one
        // implementation. The service reorders the queue and broadcasts the new state
        // back via controllerListener.onExtrasChanged.
        controller?.sendCustomCommand(
            SessionCommand(MusicService.CMD_SHUFFLE, Bundle.EMPTY),
            Bundle.EMPTY
        )
    }

    /**
     * Rebuilds the ViewModel's queue model from the player's current timeline.
     * Called after the service shuffles the queue (from either surface) so the
     * QueueFragment and queue logic stay in sync with the real playback order.
     */
    private fun rebuildQueueFromPlayer() {
        val ctrl = controller ?: return
        val all = _songs.value
        if (all.isNullOrEmpty()) return
        val byUri = all.associateBy { it.uri }
        val byId  = all.associateBy { it.id }

        val count = ctrl.mediaItemCount
        val cur   = ctrl.currentMediaItemIndex.coerceAtLeast(0)
        val newCtx = mutableListOf<Song>()
        val newUq  = mutableListOf<UQEntry>()
        var ctxBeforeCur = 0

        for (i in 0 until count) {
            val item = ctrl.getMediaItemAt(i)
            val song = resolvePlayerSong(item, byUri, byId) ?: continue
            if (item.mediaId.startsWith("uq:")) {
                newUq.add(UQEntry(song, item.mediaId))
            } else {
                if (i < cur) ctxBeforeCur++
                newCtx.add(song)
            }
        }

        contextQueue = newCtx
        contextCurrentIndex = if (newCtx.isEmpty()) 0 else ctxBeforeCur.coerceIn(0, newCtx.size - 1)
        uqEntries.clear()
        uqEntries.addAll(newUq)

        _contextQueueLive.postValue(newCtx)
        _userQueueLive.postValue(newUq.map { it.song })
        _currentQueueIndex.postValue(contextCurrentIndex)
    }

    private fun resolvePlayerSong(
        item: MediaItem,
        byUri: Map<Uri, Song>,
        byId: Map<Long, Song>
    ): Song? {
        val mid = item.mediaId
        if (mid.startsWith("song:")) {
            mid.removePrefix("song:").toLongOrNull()?.let { id -> byId[id]?.let { return it } }
        }
        item.localConfiguration?.uri?.let { uri -> byUri[uri]?.let { return it } }
        return null
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
        if (seconds <= 0) { _sleepTimerActive.value = false; _sleepTimerEndsAt.value = null; return }
        _sleepTimerActive.value = true
        sleepTimerTotalMs = seconds * 1_000L
        _sleepTimerEndsAt.value = System.currentTimeMillis() + seconds * 1_000L
        val runnable = Runnable {
            controller?.pause()
            _sleepTimerActive.postValue(false)
            _sleepTimerEndsAt.postValue(null)
            sleepTimerRunnable = null
        }
        sleepTimerRunnable = runnable
        sleepTimerHandler.postDelayed(runnable, seconds * 1_000L)
    }

    fun cancelSleepTimer() {
        sleepTimerRunnable?.let { sleepTimerHandler.removeCallbacks(it) }
        sleepTimerRunnable = null
        _sleepTimerActive.value = false
        _sleepTimerEndsAt.value = null
    }

    fun setLyricsOffset(songId: Long, offsetMs: Long) {
        prefs.edit().putLong("lyrics_offset_$songId", offsetMs).apply()
        if (_currentSong.value?.id == songId) _lyricsOffsetMs.value = offsetMs
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
            _lyricsOffsetMs.postValue(prefs.getLong("lyrics_offset_${song.id}", 0L))
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
            val dao = HexaDatabase.get(getApplication()).lyrics()
            val stored = withContext(Dispatchers.IO) { dao.get(song.id) }
            val result = stored?.toState() ?: lyricsRepository.fetchLyrics(song).also { fetched ->
                if (fetched is LyricsState.Found) withContext(Dispatchers.IO) { dao.put(fetched.toCache(song)) }
            }
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

    fun editPlaylist(playlistId: String, newName: String? = null, newCoverUri: String? = null, clearCover: Boolean = false) {
        val list = playlistRepository.getAll().toMutableList()
        val idx = list.indexOfFirst { it.id == playlistId }
        if (idx < 0) return
        val current = list[idx]
        list[idx] = current.copy(
            name = newName ?: current.name,
            coverUri = if (clearCover) null else newCoverUri ?: current.coverUri
        )
        playlistRepository.save(list[idx])
        refreshPlaylists()
    }

    fun addSongToPlaylist(playlistId: String, songId: Long) {
        playlistRepository.addSong(playlistId, songId)
        refreshPlaylists()
    }

    fun addSongsToPlaylist(playlistId: String, songIds: List<Long>) {
        playlistRepository.addSongs(playlistId, songIds)
        refreshPlaylists()
    }

    fun removeFromPlaylist(playlistId: String, songId: Long) {
        playlistRepository.removeSong(playlistId, songId)
        refreshPlaylists()
    }

    /** Moves by song id, since the playlist can hold ids that are no longer in the library. */
    fun movePlaylistSong(playlistId: String, fromSongId: Long, toSongId: Long) {
        val ids = _playlists.value?.firstOrNull { it.id == playlistId }?.songIds ?: return
        playlistRepository.moveSong(playlistId, ids.indexOf(fromSongId), ids.indexOf(toSongId))
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

private fun CachedLyrics.toState(): LyricsState.Found {
    val times = timestamps.split(',').mapNotNull { it.trim().toLongOrNull() }
    return LyricsState.Found(text.split('\n'), times.ifEmpty { null }, times.isNotEmpty())
}

private fun LyricsState.Found.toCache(song: Song) = CachedLyrics(
    songId = song.id,
    title = song.title,
    artist = song.artist,
    text = lines.joinToString("\n"),
    timestamps = timestamps?.joinToString(",").orEmpty(),
    fetchedAt = System.currentTimeMillis(),
)

