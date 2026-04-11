package com.oxoghost.hexaplayer.service

import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.oxoghost.hexaplayer.R
import com.oxoghost.hexaplayer.repository.MusicRepository
import com.oxoghost.hexaplayer.repository.PlaylistRepository
import com.oxoghost.hexaplayer.widget.WidgetController
import java.util.concurrent.Executors

@UnstableApi
class MusicService : MediaLibraryService() {

    private var mediaLibrarySession: MediaLibrarySession? = null
    private val musicRepo: MusicRepository by lazy { MusicRepository(this) }
    private val playlistRepo: PlaylistRepository by lazy { PlaylistRepository(this) }
    private val ioExecutor = Executors.newSingleThreadExecutor()

    // Tracks the last browsed context so onSetMediaItems can expand a single tapped song
    // to its full context queue (album / artist / playlist / folder).
    // Only set for "leaf" browse nodes (not for TAB_SONGS / TAB_ALBUMS / etc.).
    private var lastBrowseParentId: String = ""
    private var lastBrowseItems: List<String> = emptyList()

    companion object {
        var audioSessionId: Int = 0
            private set

        // Root / tab IDs for Android Auto browsing
        const val ROOT_ID       = "__root__"
        const val TAB_SONGS     = "__songs__"
        const val TAB_ALBUMS    = "__albums__"
        const val TAB_ARTISTS   = "__artists__"
        const val TAB_PLAYLISTS = "__playlists__"
        const val TAB_FOLDERS   = "__folders__"
    }

    // ── Noisy receiver ────────────────────────────────────────────────────────
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                mediaLibrarySession?.player?.pause()
            }
        }
    }

    // ── Player listener → keep widgets in sync + update AA custom layout ─────
    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = pushWidgetState()
        override fun onMediaItemTransition(item: MediaItem?, reason: Int) = pushWidgetState()
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = updateCustomLayout()
        override fun onRepeatModeChanged(repeatMode: Int) = updateCustomLayout()
    }

    private fun pushWidgetState() {
        val player = mediaLibrarySession?.player ?: return
        val meta   = player.currentMediaItem?.mediaMetadata ?: return
        val title   = meta.title?.toString() ?: ""
        val artist  = meta.artist?.toString() ?: ""
        val albumId = meta.extras?.getLong("albumId", -1L) ?: -1L
        WidgetController.saveState(this, title, artist, albumId, player.isPlaying)
        WidgetController.updateAllWidgets(this)
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
            .setCustomLayout(buildCustomLayout(player))
            .build()
        registerReceiver(noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        mediaLibrarySession

    /** Handle widget control intents (play/pause, next, previous, repeat, shuffle). */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val result = super.onStartCommand(intent, flags, startId)
        val player = mediaLibrarySession?.player ?: return result
        when (intent?.action) {
            WidgetController.ACTION_PLAY_PAUSE -> {
                if (player.isPlaying) player.pause() else player.play()
            }
            WidgetController.ACTION_NEXT     -> player.seekToNextMediaItem()
            WidgetController.ACTION_PREVIOUS -> player.seekToPreviousMediaItem()
            WidgetController.ACTION_REPEAT   -> {
                player.repeatMode = when (player.repeatMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                    else                   -> Player.REPEAT_MODE_OFF
                }
            }
            WidgetController.ACTION_SHUFFLE  -> { /* no-op: ViewModel owns shuffle state */ }
        }
        return result
    }

    override fun onTaskRemoved(rootIntent: Intent?) = stopSelf()

    override fun onDestroy() {
        unregisterReceiver(noisyReceiver)
        mediaLibrarySession?.run {
            player.removeListener(playerListener)
            player.release()
            release()
        }
        mediaLibrarySession = null
        audioSessionId = 0
        ioExecutor.shutdown()
        super.onDestroy()
    }

    // ── Android Auto custom layout (shuffle + repeat) ─────────────────────────

    private fun buildCustomLayout(player: Player): List<CommandButton> = listOf(
        CommandButton.Builder(
            if (player.shuffleModeEnabled) CommandButton.ICON_SHUFFLE_ON
            else CommandButton.ICON_SHUFFLE_OFF
        )
            .setPlayerCommand(Player.COMMAND_SET_SHUFFLE_MODE)
            .setDisplayName("Shuffle")
            .setIconResId(R.drawable.ic_shuffle)
            .build(),
        CommandButton.Builder(
            when (player.repeatMode) {
                Player.REPEAT_MODE_ONE -> CommandButton.ICON_REPEAT_ONE
                Player.REPEAT_MODE_ALL -> CommandButton.ICON_REPEAT_ALL
                else                   -> CommandButton.ICON_REPEAT_OFF
            }
        )
            .setPlayerCommand(Player.COMMAND_SET_REPEAT_MODE)
            .setDisplayName("Repeat")
            .setIconResId(
                if (player.repeatMode == Player.REPEAT_MODE_ONE) R.drawable.ic_repeat_one
                else R.drawable.ic_repeat
            )
            .build()
    )

    private fun updateCustomLayout() {
        val session = mediaLibrarySession ?: return
        session.setCustomLayout(buildCustomLayout(session.player))
    }

    // ── Android Auto library callback ─────────────────────────────────────────
    private inner class LibraryCallback : MediaLibrarySession.Callback {

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val root = tab(ROOT_ID, "HexaPlayer")
            return Futures.immediateFuture(LibraryResult.ofItem(root, null))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
            ioExecutor.execute {
                try {
                    future.set(buildChildren(parentId, page, pageSize))
                } catch (_: Exception) {
                    future.set(LibraryResult.ofError(SessionError.ERROR_UNKNOWN))
                }
            }
            return future
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val future = SettableFuture.create<LibraryResult<MediaItem>>()
            ioExecutor.execute {
                try {
                    if (mediaId.startsWith("song:")) {
                        val id   = mediaId.removePrefix("song:").toLongOrNull()
                        val song = musicRepo.getAllSongs().firstOrNull { it.id == id }
                        if (song != null) future.set(LibraryResult.ofItem(song.toAutoItem(), null))
                        else future.set(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE))
                    } else {
                        future.set(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE))
                    }
                } catch (_: Exception) {
                    future.set(LibraryResult.ofError(SessionError.ERROR_UNKNOWN))
                }
            }
            return future
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> =
            Futures.immediateFuture(LibraryResult.ofVoid())

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
            ioExecutor.execute {
                try {
                    val q = query.lowercase().trim()
                    val matching = musicRepo.getAllSongs().filter { song ->
                        song.title.lowercase().contains(q) ||
                        song.artist.lowercase().contains(q) ||
                        song.album.lowercase().contains(q)
                    }
                    val paged = matching.page(page, pageSize)
                    future.set(
                        LibraryResult.ofItemList(
                            ImmutableList.copyOf(paged.map { it.toAutoItem() }),
                            null
                        )
                    )
                } catch (_: Exception) {
                    future.set(LibraryResult.ofError(SessionError.ERROR_UNKNOWN))
                }
            }
            return future
        }

        /**
         * Resolves media items by mediaId to playable URIs — used by voice commands and direct play.
         */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>
        ): ListenableFuture<List<MediaItem>> {
            val future = SettableFuture.create<List<MediaItem>>()
            ioExecutor.execute {
                try {
                    val allSongs by lazy { musicRepo.getAllSongs() }
                    val resolved = mediaItems.map { item ->
                        if (item.localConfiguration != null) return@map item
                        if (item.mediaId.startsWith("song:")) {
                            val id = item.mediaId.removePrefix("song:").toLongOrNull()
                            allSongs.firstOrNull { it.id == id }?.toAutoItem() ?: item
                        } else item
                    }
                    future.set(resolved)
                } catch (_: Exception) {
                    future.set(mediaItems)
                }
            }
            return future
        }

        /**
         * Intercepts play commands from Android Auto to build the correct context queue.
         *
         * When the user taps a single song in Android Auto:
         * - If it was in the last browsed context (album/artist/playlist/folder), the full
         *   context list is used as the queue, starting at the tapped song.
         * - Otherwise, the song's album is used as the queue (matching phone-app behavior).
         *
         * This ensures the queue is populated correctly after playing from Auto.
         */
        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
            startIndex: Int,
            startPositionMs: Long
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            ioExecutor.execute {
                try {
                    future.set(resolveWithContext(mediaItems, startIndex, startPositionMs))
                } catch (_: Exception) {
                    future.set(
                        MediaSession.MediaItemsWithStartPosition(
                            mediaItems, startIndex, startPositionMs
                        )
                    )
                }
            }
            return future
        }
    }

    // ── Queue context resolution ──────────────────────────────────────────────

    /**
     * Resolves a play request to a full queue.
     *
     * Single-song taps from a browsed context are expanded to the full context list.
     * Multi-item sets have their URIs resolved and are passed through as-is.
     */
    private fun resolveWithContext(
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long
    ): MediaSession.MediaItemsWithStartPosition {
        if (mediaItems.size == 1 && mediaItems[0].mediaId.startsWith("song:")) {
            val clickedId = mediaItems[0].mediaId

            // Use the last browsed context when the tapped song belongs to it
            val contextIds: List<String> = if (
                lastBrowseParentId.isNotEmpty() && clickedId in lastBrowseItems
            ) {
                lastBrowseItems
            } else {
                // Fallback: build album queue — matches phone app behavior
                val songId = clickedId.removePrefix("song:").toLongOrNull()
                val song = if (songId != null) {
                    musicRepo.getAllSongs().firstOrNull { it.id == songId }
                } else null
                if (song != null) {
                    musicRepo.getAllSongs()
                        .filter { it.albumId == song.albumId }
                        .sortedBy { it.title }
                        .map { "song:${it.id}" }
                } else {
                    listOf(clickedId)
                }
            }

            val allSongsById = musicRepo.getAllSongs().associateBy { it.id }
            val resolved = contextIds.mapNotNull { mid ->
                val id = mid.removePrefix("song:").toLongOrNull() ?: return@mapNotNull null
                allSongsById[id]?.toAutoItem()
            }
            val idx = resolved.indexOfFirst { it.mediaId == clickedId }.coerceAtLeast(0)
            return MediaSession.MediaItemsWithStartPosition(resolved, idx, startPositionMs)
        }

        // Multi-item or non-song items: resolve URIs
        val allSongsById by lazy { musicRepo.getAllSongs().associateBy { it.id } }
        val resolved = mediaItems.map { item ->
            if (item.localConfiguration != null) return@map item
            if (item.mediaId.startsWith("song:")) {
                val id = item.mediaId.removePrefix("song:").toLongOrNull()
                if (id != null) allSongsById[id]?.toAutoItem() ?: item else item
            } else item
        }
        return MediaSession.MediaItemsWithStartPosition(resolved, startIndex, startPositionMs)
    }

    // ── Library building helpers ──────────────────────────────────────────────

    private fun buildChildren(
        parentId: String,
        page: Int,
        pageSize: Int
    ): LibraryResult<ImmutableList<MediaItem>> {
        return when {
            parentId == ROOT_ID -> {
                // Reset context on returning to root
                lastBrowseParentId = ""
                lastBrowseItems = emptyList()
                val tabs = ImmutableList.of(
                    tab(TAB_SONGS,     "Songs"),
                    tab(TAB_ALBUMS,    "Albums"),
                    tab(TAB_ARTISTS,   "Artists"),
                    tab(TAB_PLAYLISTS, "Playlists"),
                    tab(TAB_FOLDERS,   "Folders")
                )
                LibraryResult.ofItemList(tabs, null)
            }

            parentId == TAB_SONGS -> {
                // Top-level song list — no context; album fallback used in onSetMediaItems
                lastBrowseParentId = ""
                lastBrowseItems = emptyList()
                val songs = musicRepo.getAllSongs()
                val paged = songs.page(page, pageSize)
                LibraryResult.ofItemList(ImmutableList.copyOf(paged.map { it.toAutoItem() }), null)
            }

            parentId == TAB_ALBUMS -> {
                lastBrowseParentId = ""
                lastBrowseItems = emptyList()
                val albums = musicRepo.groupByAlbums(musicRepo.getAllSongs())
                val items = albums.map { album ->
                    browsable(
                        id       = "album:${album.id}",
                        title    = album.title,
                        subtitle = album.artist,
                        artUri   = musicRepo.getAlbumArtUri(album.id)
                    )
                }
                LibraryResult.ofItemList(ImmutableList.copyOf(items), null)
            }

            parentId == TAB_ARTISTS -> {
                lastBrowseParentId = ""
                lastBrowseItems = emptyList()
                val artists = musicRepo.groupByArtists(musicRepo.getAllSongs())
                val items = artists.map { artist ->
                    browsable(
                        id       = "artist:${artist.name}",
                        title    = artist.name,
                        subtitle = "${artist.songs.size} songs"
                    )
                }
                LibraryResult.ofItemList(ImmutableList.copyOf(items), null)
            }

            parentId == TAB_PLAYLISTS -> {
                lastBrowseParentId = ""
                lastBrowseItems = emptyList()
                val playlists = playlistRepo.getAll()
                val items = playlists.map { pl ->
                    val artUri = pl.coverUri?.let { Uri.parse(it) }
                    browsable(
                        id       = "playlist:${pl.id}",
                        title    = pl.name,
                        subtitle = "${pl.songIds.size} songs",
                        artUri   = artUri
                    )
                }
                LibraryResult.ofItemList(ImmutableList.copyOf(items), null)
            }

            parentId == TAB_FOLDERS -> {
                lastBrowseParentId = ""
                lastBrowseItems = emptyList()
                val songs = musicRepo.getAllSongs()
                val folders = musicRepo.getTopLevelFolders(songs)
                val items = folders.map { path ->
                    val name = path.trimEnd('/').substringAfterLast('/').ifBlank { path }
                    browsable(id = "folder:$path", title = name, subtitle = path)
                }
                LibraryResult.ofItemList(ImmutableList.copyOf(items), null)
            }

            parentId.startsWith("album:") -> {
                val albumId = parentId.removePrefix("album:").toLongOrNull()
                    ?: return LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
                val songs = musicRepo.getAllSongs().filter { it.albumId == albumId }
                // Track context so onSetMediaItems can build the full album queue
                lastBrowseParentId = parentId
                lastBrowseItems = songs.map { "song:${it.id}" }
                LibraryResult.ofItemList(ImmutableList.copyOf(songs.map { it.toAutoItem() }), null)
            }

            parentId.startsWith("artist:") -> {
                val name  = parentId.removePrefix("artist:")
                val songs = musicRepo.getAllSongs().filter { it.artist == name }
                lastBrowseParentId = parentId
                lastBrowseItems = songs.map { "song:${it.id}" }
                LibraryResult.ofItemList(ImmutableList.copyOf(songs.map { it.toAutoItem() }), null)
            }

            parentId.startsWith("playlist:") -> {
                val playlistId = parentId.removePrefix("playlist:")
                val playlist = playlistRepo.getAll().firstOrNull { it.id == playlistId }
                    ?: return LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
                val allSongsById = musicRepo.getAllSongs().associateBy { it.id }
                val songs = playlist.songIds.mapNotNull { allSongsById[it] }
                lastBrowseParentId = parentId
                lastBrowseItems = songs.map { "song:${it.id}" }
                LibraryResult.ofItemList(ImmutableList.copyOf(songs.map { it.toAutoItem() }), null)
            }

            parentId.startsWith("folder:") -> {
                val folderPath = parentId.removePrefix("folder:")
                val allSongs = musicRepo.getAllSongs()
                val (subfolders, directSongs) = musicRepo.getFolderContents(allSongs, folderPath)

                val items = mutableListOf<MediaItem>()
                // Subfolder nodes (browsable) come first
                subfolders.forEach { path ->
                    val name = path.trimEnd('/').substringAfterLast('/').ifBlank { path }
                    items.add(browsable(id = "folder:$path", title = name, subtitle = path))
                }
                // Songs directly inside this folder
                directSongs.forEach { items.add(it.toAutoItem()) }

                // Track only the direct songs (not subfolders) as the playable context
                lastBrowseParentId = parentId
                lastBrowseItems = directSongs.map { "song:${it.id}" }

                LibraryResult.ofItemList(ImmutableList.copyOf(items), null)
            }

            else -> LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
        }
    }

    // ── MediaItem factory helpers ─────────────────────────────────────────────

    /** Browsable (non-playable) tab item. */
    private fun tab(id: String, title: String): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .build()
            )
            .build()

    /** Browsable album / artist / playlist / folder node. */
    private fun browsable(
        id: String,
        title: String,
        subtitle: String = "",
        artUri: Uri? = null
    ): MediaItem {
        val meta = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(subtitle)
            .setIsBrowsable(true)
            .setIsPlayable(false)
        if (artUri != null) meta.setArtworkUri(artUri)
        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(meta.build())
            .build()
    }

    // ── List paging helper ────────────────────────────────────────────────────
    private fun <T> List<T>.page(page: Int, size: Int): List<T> {
        if (size <= 0) return this
        val from = page * size
        if (from >= this.size) return emptyList()
        return subList(from, minOf(from + size, this.size))
    }
}

// ── Song → Auto MediaItem ─────────────────────────────────────────────────────
private fun com.oxoghost.hexaplayer.data.Song.toAutoItem(): MediaItem {
    val artUri = ContentUris.withAppendedId(
        Uri.parse("content://media/external/audio/albumart"), albumId
    )
    return MediaItem.Builder()
        .setMediaId("song:$id")
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(artUri)
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .build()
        )
        .build()
}
