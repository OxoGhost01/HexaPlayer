package com.oxoghost.hexaplayer.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaLibraryService.LibraryParams
import com.oxoghost.hexaplayer.data.Album
import com.oxoghost.hexaplayer.data.Artist
import com.oxoghost.hexaplayer.data.Artwork
import com.oxoghost.hexaplayer.data.Playlist
import com.oxoghost.hexaplayer.data.Song
import com.oxoghost.hexaplayer.data.toMediaItem
import com.oxoghost.hexaplayer.repository.HistoryRepository
import com.oxoghost.hexaplayer.repository.MusicRepository
import com.oxoghost.hexaplayer.repository.Period
import com.oxoghost.hexaplayer.repository.PeriodKind
import com.oxoghost.hexaplayer.repository.PlaylistRepository
import com.oxoghost.hexaplayer.repository.QueueStore
import kotlinx.coroutines.runBlocking

/** A queue to play: [songs] from [start], and the label the app shows for it ("album · Showbiz"). */
class AutoQueue(val songs: List<Song>, val start: Int, val shuffled: Boolean, val source: String?)

/**
 * The Android Auto browse tree, laid out like the material skin: Home, Albums, Artists and
 * Playlists (Auto shows at most 4 tabs). Playable rows carry the list they sit in
 * ("album:12|345"), so a tap queues that list whatever Auto loaded in the background.
 * Called from the service's single IO thread. [queueLoaded] hides Resume while a queue is in the player.
 */
class AutoLibrary(private val context: Context, private val queueLoaded: () -> Boolean) {

    companion object {
        const val ROOT = "root"
        const val HOME = "home"
        const val ALBUMS = "albums"
        const val ARTISTS = "artists"
        const val PLAYLISTS = "playlists"
        const val SONGS = "songs"
        const val FOLDERS = "folders"
        const val RECENT = "recent"
        const val MOST = "most"
        const val RESUME = "resume"
        private const val SHUFFLE = "shuffle"
        private const val MESSAGE = "message"
        private const val ALBUM = "album:"
        private const val ARTIST = "artist:"
        private const val PLAYLIST = "playlist:"
        private const val FOLDER = "folder:"
        private const val LEGACY_SONG = "song:"

        val TABS = listOf(HOME, ALBUMS, ARTISTS, PLAYLISTS)

        private const val LIST = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
        private const val GRID = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
    }

    private val musicRepo = MusicRepository(context)
    private val playlistRepo = PlaylistRepository(context)
    private val history = HistoryRepository(context)
    private val queueStore = QueueStore(context)

    private class Library(val songs: List<Song>, repo: MusicRepository) {
        val byId = songs.associateBy { it.id }
        val albums = repo.groupByAlbums(songs)
        val albumsById = albums.associateBy { it.id }
        val artists = repo.groupByArtists(songs)
        val artistsByName = artists.associateBy { it.name }
    }

    @Volatile private var cached: Library? = null

    private fun library(): Library = cached ?: Library(musicRepo.loadVisibleSongs(), musicRepo).also { cached = it }

    /** The library or its settings changed. */
    fun invalidate() {
        cached = null
    }

    fun hasAccess(): Boolean = ContextCompat.checkSelfPermission(
        context,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE,
    ) == PackageManager.PERMISSION_GRANTED

    // ── Tree ──────────────────────────────────────────────────────────────────

    fun root(): MediaItem = browsable(ROOT, "HexaPlayer")

    /** Lists by default; each tab and page says when its children are a grid. */
    fun rootParams(): LibraryParams = LibraryParams.Builder()
        .setExtras(styleExtras(browsable = LIST, playable = LIST))
        .build()

    /** Children of [parentId], or null when the id is unknown. */
    fun children(parentId: String): List<MediaItem>? {
        if (parentId == ROOT) return tabs()
        if (parentId == MESSAGE) return emptyList()
        if (!hasAccess()) return listOf(message("Open HexaPlayer on your phone", "to let it read your music"))
        val lib = library()
        if (lib.songs.isEmpty() && parentId in TABS) return listOf(message("No songs yet", "Add music or download some in HexaPlayer"))
        return when {
            parentId == HOME -> home(lib)
            parentId == ALBUMS -> lib.albums.map(::albumItem)
            parentId == ARTISTS -> lib.artists.map(::artistItem)
            parentId == PLAYLISTS -> playlistRepo.getAll().map(::playlistItem)
            parentId == SONGS -> lib.songs.map { songRow(SONGS, it) }
            parentId == FOLDERS -> musicRepo.getTopLevelFolders(lib.songs).map(::folderItem)
            parentId.startsWith(ALBUM) -> lib.albumsById[parentId.removePrefix(ALBUM).toLongOrNull()]?.let { album ->
                listOf(shuffleRow(parentId)) + album.songs.map { songRow(parentId, it) }
            }
            parentId.startsWith(ARTIST) -> lib.artistsByName[parentId.removePrefix(ARTIST)]?.let { artistPage(parentId, it) }
            parentId.startsWith(PLAYLIST) -> playlistSongs(parentId, lib)?.let { songs ->
                listOf(shuffleRow(parentId)) + songs.map { songRow(parentId, it) }
            }
            parentId.startsWith(FOLDER) -> {
                val (subfolders, songs) = musicRepo.getFolderContents(lib.songs, parentId.removePrefix(FOLDER))
                subfolders.map(::folderItem) + songs.map { songRow(parentId, it) }
            }
            else -> null
        }
    }

    /** A single item by id, for Auto coming back to a page after the service restarted. */
    fun item(id: String): MediaItem? {
        if (id == ROOT) return root()
        tabs().firstOrNull { it.mediaId == id }?.let { return it }
        if (!hasAccess()) return null
        val lib = library()
        return when {
            id == SONGS || id == FOLDERS -> home(lib).firstOrNull { it.mediaId == id }
            id.startsWith(LEGACY_SONG) -> lib.byId[id.removePrefix(LEGACY_SONG).toLongOrNull()]?.toMediaItem(id)
            '|' in id -> {
                val context = id.substringBeforeLast('|')
                lib.byId[id.substringAfterLast('|').toLongOrNull()]?.let { songRow(context, it) }
            }
            id.startsWith(ALBUM) -> lib.albumsById[id.removePrefix(ALBUM).toLongOrNull()]?.let(::albumItem)
            id.startsWith(ARTIST) -> lib.artistsByName[id.removePrefix(ARTIST)]?.let(::artistItem)
            id.startsWith(PLAYLIST) -> playlistRepo.getAll().firstOrNull { it.id == id.removePrefix(PLAYLIST) }?.let(::playlistItem)
            id.startsWith(FOLDER) -> folderItem(id.removePrefix(FOLDER))
            else -> null
        }
    }

    private fun tabs() = listOf(
        tab(HOME, "Home", "ic_auto_home", browsableStyle = LIST),
        tab(ALBUMS, "Albums", "ic_auto_album", browsableStyle = GRID),
        tab(ARTISTS, "Artists", "ic_auto_artist", browsableStyle = LIST),
        tab(PLAYLISTS, "Playlists", "ic_auto_playlist", browsableStyle = GRID),
    )

    /** Material Home: resume, shuffle, recently played, most played this month, the rest of the library. */
    private fun home(lib: Library): List<MediaItem> = buildList {
        if (!queueLoaded()) queueStore.load()?.current?.let { current ->
            add(
                playable(RESUME, "Resume", "${current.title} · ${current.artist}",
                    art = if (current.songId >= 0) Artwork.song(current.songId) else icon("ic_auto_resume"))
            )
        }
        add(playable("$SHUFFLE|$SONGS", "Shuffle all", "${lib.songs.size} songs", art = icon("ic_auto_shuffle")))
        recent(lib).forEach { add(songRow(RECENT, it, group = "Recently played")) }
        mostPlayed(lib).forEach { add(songRow(MOST, it, group = "Most played this month")) }
        add(grouped(browsable(SONGS, "Songs", "${lib.songs.size} songs", art = icon("ic_auto_song")), "Library"))
        add(grouped(browsable(FOLDERS, "Folders", art = icon("ic_auto_folder")), "Library"))
    }

    /** Shuffle, then albums as a grid, then every song. */
    private fun artistPage(id: String, artist: Artist): List<MediaItem> =
        listOf(shuffleRow(id)) +
            artist.albums.map { grouped(albumItem(it), "Albums", style = GRID) } +
            artist.songs.map { songRow(id, it, group = "Songs") }

    private fun recent(lib: Library) = runBlocking { history.recent(10) }.mapNotNull { lib.byId[it.songId] }

    private fun mostPlayed(lib: Library) =
        runBlocking { history.mostPlayed(Period(PeriodKind.Month), 10) }.mapNotNull { lib.byId[it.songId] }

    private fun playlistSongs(id: String, lib: Library): List<Song>? =
        playlistRepo.getAll().firstOrNull { it.id == id.removePrefix(PLAYLIST) }?.songIds?.mapNotNull { lib.byId[it] }

    // ── Playing ───────────────────────────────────────────────────────────────

    /** The queue a tapped row stands for, or null when it isn't one of ours. */
    fun resolve(mediaId: String): AutoQueue? {
        if (!hasAccess()) return null
        val lib = library()
        if (mediaId.startsWith(LEGACY_SONG)) {
            return lib.byId[mediaId.removePrefix(LEGACY_SONG).toLongOrNull()]?.let { albumQueue(lib, it) }
        }
        if ('|' !in mediaId) return null
        val context = mediaId.substringBeforeLast('|')
        val target = mediaId.substringAfterLast('|')
        if (context == SHUFFLE) {
            val songs = contextSongs(target, lib)?.takeIf { it.isNotEmpty() } ?: return null
            return AutoQueue(songs.shuffled(), 0, shuffled = true, source = sourceOf(target, lib))
        }
        val song = lib.byId[target.toLongOrNull()] ?: return null
        val songs = contextSongs(context, lib).orEmpty()
        val start = songs.indexOfFirst { it.id == song.id }
        // Recently played and most played can move between showing the row and tapping it.
        if (start < 0) return albumQueue(lib, song)
        return AutoQueue(songs, start, shuffled = false, source = sourceOf(context, lib))
    }

    /** Everything, shuffled: what an empty voice request or the Home row plays. */
    fun shuffleAll(): AutoQueue? = resolve("$SHUFFLE|$SONGS")

    /**
     * "Hey Google, play X on HexaPlayer". Uses the assistant's hints (artist, album, title,
     * playlist) when it sends them, else matches the words against playlists, artists, albums
     * and songs, exact names first.
     */
    @Suppress("DEPRECATION") // EXTRA_MEDIA_PLAYLIST is still what assistants send
    fun voice(query: String, extras: Bundle?): AutoQueue? {
        if (!hasAccess()) return null
        val lib = library()
        if (lib.songs.isEmpty()) return null
        val hinted = when (extras?.getString(MediaStore.EXTRA_MEDIA_FOCUS)) {
            MediaStore.Audio.Artists.ENTRY_CONTENT_TYPE -> extras.getString(MediaStore.EXTRA_MEDIA_ARTIST)?.let { artistQueue(lib, it) }
            MediaStore.Audio.Albums.ENTRY_CONTENT_TYPE -> extras.getString(MediaStore.EXTRA_MEDIA_ALBUM)?.let { name ->
                lib.albums.firstOrNull { it.title.equals(name, ignoreCase = true) }?.let { albumQueue(lib, it.songs.first()) }
            }
            MediaStore.Audio.Media.ENTRY_CONTENT_TYPE -> extras.getString(MediaStore.EXTRA_MEDIA_TITLE)?.let { title ->
                lib.songs.firstOrNull { it.title.equals(title, ignoreCase = true) }?.let { albumQueue(lib, it) }
            }
            MediaStore.Audio.Playlists.ENTRY_CONTENT_TYPE -> extras.getString(MediaStore.EXTRA_MEDIA_PLAYLIST)?.let { playlistQueue(lib, it) }
            else -> null
        }
        if (hinted != null) return hinted
        val q = query.trim()
        if (q.isEmpty()) return null
        for (exact in listOf(true, false)) {
            fun String.hit() = if (exact) equals(q, ignoreCase = true) else contains(q, ignoreCase = true)
            playlistRepo.getAll().firstOrNull { it.name.hit() }?.let { playlistQueue(lib, it.name) }?.let { return it }
            lib.artists.firstOrNull { it.name.hit() }?.let { return artistQueue(lib, it.name) }
            lib.albums.firstOrNull { it.title.hit() }?.let { return albumQueue(lib, it.songs.first()) }
            lib.songs.firstOrNull { it.title.hit() }?.let { return albumQueue(lib, it) }
        }
        return null
    }

    /** Single songs for the rows in [mediaIds], for requests that add rather than replace. */
    fun songsFor(mediaIds: List<String>): List<Song> {
        if (!hasAccess()) return emptyList()
        val lib = library()
        return mediaIds.mapNotNull { id ->
            val songId = if (id.startsWith(LEGACY_SONG)) id.removePrefix(LEGACY_SONG) else id.substringAfterLast('|', "")
            lib.byId[songId.toLongOrNull()]
        }
    }

    private fun contextSongs(id: String, lib: Library): List<Song>? = when {
        id == SONGS -> lib.songs
        id == RECENT -> recent(lib)
        id == MOST -> mostPlayed(lib)
        id.startsWith(ALBUM) -> lib.albumsById[id.removePrefix(ALBUM).toLongOrNull()]?.songs
        id.startsWith(ARTIST) -> lib.artistsByName[id.removePrefix(ARTIST)]?.songs
        id.startsWith(PLAYLIST) -> playlistSongs(id, lib)
        id.startsWith(FOLDER) -> musicRepo.getFolderContents(lib.songs, id.removePrefix(FOLDER)).second
        else -> null
    }

    private fun sourceOf(id: String, lib: Library): String? = when {
        id == SONGS -> "all songs"
        id == RECENT -> "recently played"
        id == MOST -> "most played"
        id.startsWith(ALBUM) -> lib.albumsById[id.removePrefix(ALBUM).toLongOrNull()]?.let { "album · ${it.title}" }
        id.startsWith(ARTIST) -> "artist · ${id.removePrefix(ARTIST)}"
        id.startsWith(PLAYLIST) -> playlistRepo.getAll().firstOrNull { it.id == id.removePrefix(PLAYLIST) }?.let { "playlist · ${it.name}" }
        id.startsWith(FOLDER) -> "folder · ${folderName(id.removePrefix(FOLDER))}"
        else -> null
    }

    private fun albumQueue(lib: Library, song: Song): AutoQueue {
        val album = lib.albumsById[song.albumId]
        val songs = album?.songs ?: listOf(song)
        return AutoQueue(songs, songs.indexOfFirst { it.id == song.id }.coerceAtLeast(0), false, album?.let { "album · ${it.title}" })
    }

    private fun artistQueue(lib: Library, name: String): AutoQueue? {
        val artist = lib.artists.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: return null
        return AutoQueue(artist.songs.shuffled(), 0, shuffled = true, source = "artist · ${artist.name}")
    }

    private fun playlistQueue(lib: Library, name: String): AutoQueue? {
        val playlist = playlistRepo.getAll().firstOrNull { it.name.equals(name, ignoreCase = true) } ?: return null
        val songs = playlist.songIds.mapNotNull { lib.byId[it] }.takeIf { it.isNotEmpty() } ?: return null
        return AutoQueue(songs, 0, shuffled = false, source = "playlist · ${playlist.name}")
    }

    // ── Search ────────────────────────────────────────────────────────────────

    /** Search results, grouped: songs, artists, albums, playlists. */
    fun search(query: String): List<MediaItem> {
        val q = query.trim()
        if (q.isEmpty() || !hasAccess()) return emptyList()
        val lib = library()
        fun String.hit() = contains(q, ignoreCase = true)
        return buildList {
            lib.songs.filter { it.title.hit() || it.artist.hit() || it.album.hit() }.take(30)
                .forEach { add(songRow("$ALBUM${it.albumId}", it, group = "Songs")) }
            lib.artists.filter { it.name.hit() }.take(10).forEach { add(grouped(artistItem(it), "Artists")) }
            lib.albums.filter { it.title.hit() || it.artist.hit() }.take(10).forEach { add(grouped(albumItem(it), "Albums")) }
            playlistRepo.getAll().filter { it.name.hit() }.forEach { add(grouped(playlistItem(it), "Playlists")) }
        }
    }

    // ── Items ─────────────────────────────────────────────────────────────────

    private fun tab(id: String, title: String, iconName: String, browsableStyle: Int) = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtworkUri(icon(iconName))
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                .setExtras(styleExtras(browsable = browsableStyle, playable = LIST))
                .build()
        )
        .build()

    private fun albumItem(album: Album) = browsable(
        "$ALBUM${album.id}", album.title, album.artist, Artwork.album(album.id), MediaMetadata.MEDIA_TYPE_ALBUM,
    )

    private fun artistItem(artist: Artist) = browsable(
        "$ARTIST${artist.name}", artist.name, songCount(artist.songs.size),
        artist.albums.firstOrNull()?.let { Artwork.album(it.id) }, MediaMetadata.MEDIA_TYPE_ARTIST,
    )

    private fun playlistItem(playlist: Playlist) = browsable(
        "$PLAYLIST${playlist.id}", playlist.name, songCount(playlist.songIds.size),
        Artwork.playlist(playlist.id), MediaMetadata.MEDIA_TYPE_PLAYLIST,
    )

    private fun folderItem(path: String) = browsable(
        "$FOLDER$path", folderName(path), path.trimEnd('/'), icon("ic_auto_folder"), MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
    )

    private fun shuffleRow(contextId: String) = playable("$SHUFFLE|$contextId", "Shuffle", art = icon("ic_auto_shuffle"))

    private fun songRow(contextId: String, song: Song, group: String? = null): MediaItem {
        val item = song.toMediaItem("$contextId|${song.id}")
        return if (group == null) item else grouped(item, group)
    }

    /** A row that only shows text, for an empty or locked library. */
    private fun message(title: String, subtitle: String) = browsable(MESSAGE, title, subtitle, icon("ic_auto_song"))

    private fun browsable(
        id: String,
        title: String,
        subtitle: String = "",
        art: Uri? = null,
        type: Int = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
    ) = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setArtist(subtitle)
                .setArtworkUri(art)
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(type)
                .build()
        )
        .build()

    private fun playable(id: String, title: String, subtitle: String = "", art: Uri? = null) = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setArtist(subtitle)
                .setArtworkUri(art)
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .build()
        )
        .build()

    /** Puts [item] under a section title; [style] is how the item itself is drawn. */
    private fun grouped(item: MediaItem, group: String, style: Int? = null): MediaItem {
        val extras = Bundle(item.mediaMetadata.extras ?: Bundle()).apply {
            putString(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE, group)
            style?.let { putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_SINGLE_ITEM, it) }
        }
        return item.buildUpon().setMediaMetadata(item.mediaMetadata.buildUpon().setExtras(extras).build()).build()
    }

    private fun styleExtras(browsable: Int, playable: Int) = Bundle().apply {
        putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, browsable)
        putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, playable)
    }

    /** Material Symbols drawables made by tools/design/symbol_drawables.py. */
    private fun icon(name: String): Uri = Uri.parse("android.resource://${context.packageName}/drawable/$name")

    private fun folderName(path: String) = path.trimEnd('/').substringAfterLast('/').ifBlank { path }

    private fun songCount(n: Int) = if (n == 1) "1 song" else "$n songs"
}
