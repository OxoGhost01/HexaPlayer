package com.oxoghost.hexaplayer.service

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.oxoghost.hexaplayer.R
import com.oxoghost.hexaplayer.data.Artwork
import com.oxoghost.hexaplayer.data.db.HexaDatabase
import com.oxoghost.hexaplayer.data.toMediaItem
import com.oxoghost.hexaplayer.repository.EqualizerRepository
import com.oxoghost.hexaplayer.repository.QueueStore
import com.oxoghost.hexaplayer.repository.SavedQueue
import com.oxoghost.hexaplayer.ui.MainActivity
import com.oxoghost.hexaplayer.widget.WidgetController
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@UnstableApi
class MusicService : MediaLibraryService() {

    private var mediaLibrarySession: MediaLibrarySession? = null
    private val auto by lazy { AutoLibrary(this) { queueLoaded } }
    @Volatile private var queueLoaded = false
    private val ioExecutor = Executors.newSingleThreadExecutor()

    // Internal shuffle state: owned here so both the phone app and Android Auto
    // drive the SAME shuffler (a manual reorder of upcoming items). ExoPlayer's own
    // shuffleModeEnabled is always kept false so it never re-orders on top of ours.
    private var shuffleOn = false

    // The app puts the remembered queue back, paused, each time it opens. The widget keeps its
    // Resume view until something actually plays.
    private var playedThisSession = false

    companion object {
        var audioSessionId: Int = 0
            private set

        // Custom session commands + session-extras keys for the shared shuffler
        const val CMD_SHUFFLE          = "com.oxoghost.hexaplayer.action.SHUFFLE"
        const val CMD_REPEAT           = "com.oxoghost.hexaplayer.action.REPEAT"
        const val EXTRA_SHUFFLE_ON     = "hexa.shuffle_on"
        const val EXTRA_QUEUE_REORDERED = "hexa.queue_reordered"
        const val EXTRA_FORCE_ON       = "hexa.force_on"

        private const val POSITION_SAVE_MS = 10_000L
    }

    // ── Noisy receiver ────────────────────────────────────────────────────────
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                mediaLibrarySession?.player?.pause()
            }
        }
    }

    // ── Player listener → keep widgets in sync + update the shuffle/repeat buttons ─
    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) playedThisSession = true
            pushWidgetState()
            mediaLibrarySession?.player?.let { tracker.onIsPlayingChanged(it, isPlaying) }
            saveQueueSoon()
        }

        override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
            pushWidgetState()
            mediaLibrarySession?.player?.let { tracker.onTransition(it, item, reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) }
            saveQueueSoon()
            // Recently played on the car's Home, once the play is recorded.
            mainHandler.removeCallbacks(homeChanged)
            mainHandler.postDelayed(homeChanged, 3_000)
        }

        override fun onPlaybackStateChanged(state: Int) {
            val player = mediaLibrarySession?.player ?: return
            if (state == Player.STATE_READY) tracker.onReady(player)
            if (state == Player.STATE_ENDED) tracker.onEnded()
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            queueLoaded = !timeline.isEmpty
            saveQueueSoon()
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            updateButtons()
            saveQueueSoon()
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            updateButtons()
            saveQueueSoon()
        }

        // ExoPlayer only gets its session once audio starts; effects must attach to that one.
        override fun onAudioSessionIdChanged(id: Int) {
            audioSessionId = id
            EqualizerRepository.getInstance(this@MusicService).initEffects(id)
        }
    }

    // ── Remember queue + play history ─────────────────────────────────────────
    private val queueStore by lazy { QueueStore(this) }
    private val history by lazy { HexaDatabase.get(this).plays() }
    private val tracker = PlayTracker { event -> ioExecutor.execute { runBlocking { history.insert(event) } } }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val saveQueue = Runnable {
        mediaLibrarySession?.player?.let { queueStore.save(it, shuffleOn) }
        pushWidgetState()
        // Keep the position fresh while playing.
        if (mediaLibrarySession?.player?.isPlaying == true) saveQueueSoon(POSITION_SAVE_MS)
    }

    /** Debounced: many changes in a row (a new queue, a reorder) give one write. */
    private fun saveQueueSoon(delayMs: Long = 1_000) {
        mainHandler.removeCallbacks(saveQueue)
        mainHandler.postDelayed(saveQueue, delayMs)
    }

    // ── Library changes → Android Auto reloads its lists ──────────────────────
    private val libraryChanged = Runnable {
        auto.invalidate()
        AutoLibrary.TABS.forEach { mediaLibrarySession?.notifyChildrenChanged(it, Int.MAX_VALUE, null) }
    }

    private val homeChanged = Runnable {
        mediaLibrarySession?.notifyChildrenChanged(AutoLibrary.HOME, Int.MAX_VALUE, null)
    }

    private val mediaObserver = object : ContentObserver(mainHandler) {
        override fun onChange(selfChange: Boolean) {
            mainHandler.removeCallbacks(libraryChanged)
            mainHandler.postDelayed(libraryChanged, 2_000)
        }
    }

    // The music folder, sort order and custom covers decide what the library shows.
    private val settingsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        mediaObserver.onChange(false)
    }

    private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private fun pushWidgetState() {
        val player = mediaLibrarySession?.player ?: return
        val meta = player.currentMediaItem?.mediaMetadata
        if (meta == null || !playedThisSession) {
            WidgetController.saveIdle(this)
        } else {
            WidgetController.saveState(
                this,
                title = meta.title?.toString().orEmpty(),
                artist = meta.artist?.toString().orEmpty(),
                albumId = meta.extras?.getLong("albumId", -1L) ?: -1L,
                isPlaying = player.isPlaying,
                positionMs = player.currentPosition.coerceAtLeast(0L),
                durationMs = player.duration.coerceAtLeast(0L),
            )
        }
        widgetScope.launch { WidgetController.updateAllWidgets(this@MusicService) }
    }

    /** Widget Resume: puts the remembered queue back and plays it. */
    private fun resumeRemembered(player: Player) {
        if (player.mediaItemCount == 0) {
            val saved = queueStore.load() ?: return
            player.setMediaItems(saved.items.map { it.toMediaItem() }, saved.index, saved.positionMs)
            restoreModes(player, saved)
            player.prepare()
        }
        player.play()
    }

    /** Repeat and the service's own shuffle flag, as they were when [saved] was written. */
    private fun restoreModes(player: Player, saved: SavedQueue) {
        player.repeatMode = saved.repeat
        shuffleOn = saved.shuffle
        afterShuffleChanged(reordered = false)
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this).build().apply {
            // Explicitly request audio focus and set media content type.
            // Required for correct audio routing in Android Auto.
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true
            )
        }
        audioSessionId = player.audioSessionId
        player.addListener(playerListener)
        mediaLibrarySession = MediaLibrarySession.Builder(this, player, LibraryCallback())
            .setMediaButtonPreferences(buildButtons(player))
            .setSessionActivity(openAppIntent())
            .build()
        registerReceiver(noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        contentResolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, mediaObserver)
        listOf("hexa_prefs", "cover_overrides", "hexa_playlists").forEach {
            getSharedPreferences(it, MODE_PRIVATE).registerOnSharedPreferenceChangeListener(settingsListener)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        mediaLibrarySession

    /** Handle widget control intents (play/pause, next, previous, resume). */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val result = super.onStartCommand(intent, flags, startId)
        val player = mediaLibrarySession?.player ?: return result
        when (intent?.action) {
            WidgetController.ACTION_PLAY_PAUSE -> {
                if (player.isPlaying) player.pause() else resumeRemembered(player)
            }
            WidgetController.ACTION_NEXT     -> player.seekToNextMediaItem()
            WidgetController.ACTION_PREVIOUS -> player.seekToPreviousMediaItem()
            WidgetController.ACTION_RESUME   -> resumeRemembered(player)
        }
        return result
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val session = mediaLibrarySession
        val carConnected = session != null && session.connectedControllers.any { isCar(session, it) }
        // Swiping the app away stops the music, unless a car is still playing it
        if (!carConnected) pauseAllPlayersAndStopSelf()
    }

    override fun onDestroy() {
        unregisterReceiver(noisyReceiver)
        contentResolver.unregisterContentObserver(mediaObserver)
        listOf("hexa_prefs", "cover_overrides", "hexa_playlists").forEach {
            getSharedPreferences(it, MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(settingsListener)
        }
        mainHandler.removeCallbacks(saveQueue)
        mainHandler.removeCallbacks(libraryChanged)
        mainHandler.removeCallbacks(homeChanged)
        mediaLibrarySession?.player?.let { queueStore.save(it, shuffleOn) }
        tracker.flush()
        mediaLibrarySession?.run {
            player.removeListener(playerListener)
            player.release()
            release()
        }
        mediaLibrarySession = null
        audioSessionId = 0
        ioExecutor.shutdown()
        WidgetController.saveIdle(this)
        // The scope dies with the service, so this last refresh runs on its own.
        CoroutineScope(Dispatchers.Default).launch { WidgetController.updateAllWidgets(applicationContext) }
        widgetScope.cancel()
        super.onDestroy()
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun isCar(session: MediaSession, controller: MediaSession.ControllerInfo) =
        session.isAutoCompanionController(controller) || session.isAutomotiveController(controller)

    // ── Shuffle + repeat buttons (notification, Android Auto) ────────────────

    // Both are custom session commands: Android Auto only shows buttons backed by one, and
    // shuffle must run our shuffler instead of ExoPlayer's random order.
    private fun buildButtons(player: Player): List<CommandButton> = listOf(
        CommandButton.Builder(if (shuffleOn) CommandButton.ICON_SHUFFLE_ON else CommandButton.ICON_SHUFFLE_OFF)
            .setSessionCommand(SessionCommand(CMD_SHUFFLE, Bundle.EMPTY))
            .setDisplayName(if (shuffleOn) "Shuffle on" else "Shuffle off")
            .setCustomIconResId(R.drawable.ic_auto_shuffle)
            .build(),
        CommandButton.Builder(
            when (player.repeatMode) {
                Player.REPEAT_MODE_ONE -> CommandButton.ICON_REPEAT_ONE
                Player.REPEAT_MODE_ALL -> CommandButton.ICON_REPEAT_ALL
                else                   -> CommandButton.ICON_REPEAT_OFF
            }
        )
            .setSessionCommand(SessionCommand(CMD_REPEAT, Bundle.EMPTY))
            .setDisplayName(
                when (player.repeatMode) {
                    Player.REPEAT_MODE_ONE -> "Repeat one"
                    Player.REPEAT_MODE_ALL -> "Repeat all"
                    else                   -> "Repeat off"
                }
            )
            .setCustomIconResId(
                if (player.repeatMode == Player.REPEAT_MODE_ONE) R.drawable.ic_auto_repeat_one
                else R.drawable.ic_auto_repeat
            )
            .build()
    )

    private fun updateButtons() {
        val session = mediaLibrarySession ?: return
        session.setMediaButtonPreferences(buildButtons(session.player))
    }

    /** Off, all, one, like the app's repeat button. */
    private fun cycleRepeat(player: Player) {
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else                   -> Player.REPEAT_MODE_OFF
        }
    }

    /**
     * The internal shuffler, mirroring the phone app's behavior:
     * - Turning ON shuffles only the upcoming context items (everything after the
     *   current track and the user-queue block), leaving the current track and the
     *   user queue untouched.
     * - Turning OFF just flips the flag (it does not restore the original order),
     *   matching the existing phone-app toggle.
     *
     * ExoPlayer's own shuffleModeEnabled is forced false so it never double-shuffles.
     */
    private fun performInternalShuffle() {
        val player = mediaLibrarySession?.player
        if (player == null) return

        if (shuffleOn) {
            shuffleOn = false
            afterShuffleChanged(reordered = false)
            return
        }

        var reordered = false
        val count = player.mediaItemCount
        val cur = player.currentMediaItemIndex
        if (cur >= 0 && count > 0) {
            // Skip the contiguous user-queue block that sits right after the current item.
            var from = cur + 1
            while (from < count && player.getMediaItemAt(from).mediaId.startsWith("uq:")) from++
            if (from < count) {
                val upcoming = ArrayList<MediaItem>(count - from)
                for (i in from until count) upcoming.add(player.getMediaItemAt(i))
                upcoming.shuffle()
                player.removeMediaItems(from, count)
                player.addMediaItems(upcoming)
                reordered = true
            }
        }
        player.shuffleModeEnabled = false
        shuffleOn = true
        afterShuffleChanged(reordered)
    }

    /** Refresh the buttons and notify controllers (phone app) of the new shuffle state. */
    private fun afterShuffleChanged(reordered: Boolean) {
        updateButtons()
        mediaLibrarySession?.setSessionExtras(
            Bundle().apply {
                putBoolean(EXTRA_SHUFFLE_ON, shuffleOn)
                putBoolean(EXTRA_QUEUE_REORDERED, reordered)
            }
        )
    }

    // ── Android Auto ──────────────────────────────────────────────────────────

    /** Runs [block] on the IO thread; a crash becomes [onError]. */
    private fun <T> onIo(onError: () -> T, block: () -> T): ListenableFuture<T> {
        val future = SettableFuture.create<T>()
        ioExecutor.execute { future.set(runCatching(block).getOrElse { onError() }) }
        return future
    }

    /** A queue picked in Android Auto, with the app's own `ctx:` ids so the phone treats it like its own. */
    private fun startQueue(queue: AutoQueue, startPositionMs: Long): MediaSession.MediaItemsWithStartPosition {
        queueStore.source = queue.source
        mainHandler.post {
            shuffleOn = queue.shuffled
            afterShuffleChanged(reordered = false)
        }
        return MediaSession.MediaItemsWithStartPosition(
            queue.songs.mapIndexed { i, song -> song.toMediaItem("ctx:$i") },
            queue.start,
            if (startPositionMs == C.TIME_UNSET) 0L else startPositionMs,
        )
    }

    /** The remembered queue, or everything shuffled when there is none. */
    private fun resumeOrShuffle(): MediaSession.MediaItemsWithStartPosition? {
        queueStore.load()?.let { saved ->
            mainHandler.post { mediaLibrarySession?.player?.let { restoreModes(it, saved) } }
            return MediaSession.MediaItemsWithStartPosition(saved.items.map { it.toMediaItem() }, saved.index, saved.positionMs)
        }
        return auto.shuffleAll()?.let { startQueue(it, 0L) }
    }

    private inner class LibraryCallback : MediaLibrarySession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            if (isCar(session, controller)) {
                // Covers come from ArtworkProvider, which only the car may read.
                grantUriPermission(
                    controller.packageName, Artwork.root,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION,
                )
                auto.invalidate()
            }
            val sessionCommands =
                MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                    .add(SessionCommand(CMD_SHUFFLE, Bundle.EMPTY))
                    .add(SessionCommand(CMD_REPEAT, Bundle.EMPTY))
                    .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
                .setAvailableSessionCommands(sessionCommands)
                .setMediaButtonPreferences(buildButtons(session.player))
                .build()
        }

        override fun onPostConnect(session: MediaSession, controller: MediaSession.ControllerInfo) {
            if (!isCar(session, controller)) return
            if (auto.hasAccess()) {
                session.setPlaybackException(controller, null)
                return
            }
            // Shown by Auto with a button that opens the app on the phone.
            session.setPlaybackException(
                controller,
                PlaybackException(
                    "Open HexaPlayer on your phone to let it read your music", null,
                    PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
                    Bundle().apply {
                        putString(MediaConstants.EXTRAS_KEY_ERROR_RESOLUTION_ACTION_LABEL_COMPAT, "Open HexaPlayer")
                        putParcelable(MediaConstants.EXTRAS_KEY_ERROR_RESOLUTION_ACTION_INTENT_COMPAT, openAppIntent())
                    },
                ),
            )
        }

        // Single entry point for shuffle and repeat from ANY surface (phone app or Android Auto).
        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                CMD_SHUFFLE -> {
                    if (args.containsKey(EXTRA_FORCE_ON)) {
                        // Caller already arranged the order (e.g. "Shuffle all"); just set state.
                        shuffleOn = args.getBoolean(EXTRA_FORCE_ON)
                        afterShuffleChanged(reordered = false)
                    } else {
                        performInternalShuffle()
                    }
                }
                CMD_REPEAT -> cycleRepeat(session.player)
                else -> return super.onCustomCommand(session, controller, customCommand, args)
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofItem(auto.root(), auto.rootParams()))

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
            onIo({ LibraryResult.ofError(SessionError.ERROR_UNKNOWN) }) {
                val children = auto.children(parentId)
                if (children == null) LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
                else LibraryResult.ofItemList(ImmutableList.copyOf(children.page(page, pageSize)), params)
            }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> =
            onIo({ LibraryResult.ofError(SessionError.ERROR_UNKNOWN) }) {
                auto.item(mediaId)?.let { LibraryResult.ofItem(it, null) }
                    ?: LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
            }

        // Auto waits for notifySearchResultChanged before it asks for the results.
        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> =
            onIo({ LibraryResult.ofError(SessionError.ERROR_UNKNOWN) }) {
                val count = auto.search(query).size
                mainHandler.post { session.notifySearchResultChanged(browser, query, count, params) }
                LibraryResult.ofVoid()
            }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
            onIo({ LibraryResult.ofError(SessionError.ERROR_UNKNOWN) }) {
                LibraryResult.ofItemList(ImmutableList.copyOf(auto.search(query).page(page, pageSize)), params)
            }

        /**
         * Requests that add items: the app's own items already carry a uri; Auto rows and voice
         * requests are turned into songs.
         */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>
        ): ListenableFuture<List<MediaItem>> {
            if (mediaItems.all { it.localConfiguration != null }) return Futures.immediateFuture(mediaItems)
            return onIo({ mediaItems.filter { it.localConfiguration != null } }) {
                val query = mediaItems.singleOrNull()?.requestMetadata?.searchQuery
                if (query != null) {
                    val queue = auto.voice(query, mediaItems.single().requestMetadata.extras) ?: auto.shuffleAll()
                    queue?.songs.orEmpty().mapIndexed { i, song -> song.toMediaItem("ctx:$i") }
                } else {
                    val songs = auto.songsFor(mediaItems.filter { it.localConfiguration == null }.map { it.mediaId })
                    mediaItems.filter { it.localConfiguration != null } + songs.map { it.toMediaItem("uq:${System.nanoTime()}") }
                }
            }
        }

        /**
         * Play requests. The app sends ready items and is left alone. From Android Auto:
         * a tapped row queues the list it sits in, Resume brings back the remembered queue,
         * and a voice request plays what it names (or resumes when it names nothing).
         */
        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
            startIndex: Int,
            startPositionMs: Long
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val passThrough = MediaSession.MediaItemsWithStartPosition(mediaItems, startIndex, startPositionMs)
            if (mediaItems.all { it.localConfiguration != null }) return Futures.immediateFuture(passThrough)
            return onIo({ passThrough }) {
                val single = mediaItems.singleOrNull()
                val query = single?.requestMetadata?.searchQuery
                when {
                    single?.mediaId == AutoLibrary.RESUME -> resumeOrShuffle()
                    query != null && query.isBlank() -> resumeOrShuffle()
                    query != null -> auto.voice(query, single.requestMetadata.extras)?.let { startQueue(it, 0L) }
                    single != null -> auto.resolve(single.mediaId)?.let { startQueue(it, startPositionMs) }
                    else -> {
                        val songs = auto.songsFor(mediaItems.map { it.mediaId })
                        if (songs.isEmpty()) null
                        else startQueue(AutoQueue(songs, startIndex.coerceIn(0, songs.lastIndex), false, null), startPositionMs)
                    }
                } ?: passThrough
            }
        }

        /** Play pressed in the car or on a headset with nothing loaded: bring back the remembered queue. */
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val saved = queueStore.load()
                ?: return Futures.immediateFailedFuture(UnsupportedOperationException("Nothing to resume"))
            if (isForPlayback) restoreModes(mediaSession.player, saved)
            return Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(saved.items.map { it.toMediaItem() }, saved.index, saved.positionMs)
            )
        }
    }

    private fun <T> List<T>.page(page: Int, size: Int): List<T> {
        if (size <= 0 || size == Int.MAX_VALUE) return this
        val from = page * size
        if (from >= this.size) return emptyList()
        return subList(from, minOf(from + size, this.size))
    }
}
