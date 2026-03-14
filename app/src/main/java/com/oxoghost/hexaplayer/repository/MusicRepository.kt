package com.oxoghost.hexaplayer.repository

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.oxoghost.hexaplayer.data.Album
import com.oxoghost.hexaplayer.data.Artist
import com.oxoghost.hexaplayer.data.Song

class MusicRepository(private val context: Context) {

    fun getAllSongs(pathFilter: String? = null, sortOrderIndex: Int = 0): List<Song> {
        val songs = mutableListOf<Song>()

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ALBUM)
            add(MediaStore.Audio.Media.ALBUM_ID)
            add(MediaStore.Audio.Media.DURATION)
            add(MediaStore.Audio.Media.DISPLAY_NAME)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Audio.Media.RELATIVE_PATH)
            } else {
                @Suppress("DEPRECATION")
                add(MediaStore.Audio.Media.DATA)
            }
        }.toTypedArray()

        val selection = buildString {
            append("${MediaStore.Audio.Media.IS_MUSIC} != 0")
            if (!pathFilter.isNullOrBlank() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                append(" AND ${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?")
            }
        }

        val selectionArgs: Array<String>? =
            if (!pathFilter.isNullOrBlank() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                arrayOf("$pathFilter%")
            } else null

        val sortOrder = when (sortOrderIndex) {
            1 -> "${MediaStore.Audio.Media.TITLE} DESC"
            2 -> "${MediaStore.Audio.Media.ARTIST} ASC"
            3 -> "${MediaStore.Audio.Media.ALBUM} ASC"
            4 -> "${MediaStore.Audio.Media.DURATION} ASC"
            5 -> "${MediaStore.Audio.Media.DURATION} DESC"
            6 -> "${MediaStore.Audio.Media.DATE_ADDED} DESC"
            7 -> "${MediaStore.Audio.Media.DATE_ADDED} ASC"
            else -> "${MediaStore.Audio.Media.TITLE} ASC"
        }

        context.contentResolver.query(
            collection, projection, selection, selectionArgs, sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val displayNameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val pathColName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.RELATIVE_PATH
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Audio.Media.DATA
            }
            val pathCol = cursor.getColumnIndex(pathColName)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                )
                val relativePath = if (pathCol >= 0) {
                    val raw = cursor.getString(pathCol) ?: ""
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) raw
                    else extractRelativePath(raw)
                } else ""

                songs.add(
                    Song(
                        id = id,
                        uri = uri,
                        title = cursor.getString(titleCol) ?: "Unknown",
                        artist = cursor.getString(artistCol) ?: "Unknown Artist",
                        album = cursor.getString(albumCol) ?: "Unknown Album",
                        albumId = cursor.getLong(albumIdCol),
                        duration = cursor.getLong(durationCol),
                        relativePath = relativePath,
                        displayName = cursor.getString(displayNameCol) ?: ""
                    )
                )
            }
        }
        return songs
    }

    fun getAlbumArtUri(albumId: Long): Uri =
        ContentUris.withAppendedId(
            Uri.parse("content://media/external/audio/albumart"), albumId
        )

    fun groupByAlbums(songs: List<Song>): List<Album> {
        return songs.groupBy { it.albumId to it.album }
            .map { (key, albumSongs) ->
                Album(
                    id = key.first,
                    title = key.second,
                    artist = albumSongs.first().artist,
                    songs = albumSongs.sortedBy { it.title }
                )
            }
            .sortedBy { it.title }
    }

    fun groupByArtists(songs: List<Song>): List<Artist> {
        return songs.groupBy { it.artist }
            .map { (artistName, artistSongs) ->
                Artist(
                    name = artistName,
                    songs = artistSongs.sortedBy { it.title },
                    albums = groupByAlbums(artistSongs)
                )
            }
            .sortedBy { it.name }
    }

    fun getFolderContents(songs: List<Song>, currentPath: String): Pair<List<String>, List<Song>> {
        val subfolders = mutableSetOf<String>()
        val directSongs = mutableListOf<Song>()

        for (song in songs) {
            val path = song.relativePath
            if (!path.startsWith(currentPath)) continue
            val remainder = path.removePrefix(currentPath)
            if (remainder.isEmpty()) {
                directSongs.add(song)
            } else {
                val segment = remainder.substringBefore('/')
                subfolders.add(currentPath + segment + "/")
            }
        }
        return subfolders.sorted() to directSongs.sortedBy { it.title }
    }

    fun getTopLevelFolders(songs: List<Song>): List<String> {
        return songs
            .map { it.relativePath }
            .filter { it.isNotBlank() }
            .map { path ->
                val idx = path.indexOf('/')
                if (idx >= 0) path.substring(0, idx + 1) else path
            }
            .distinct()
            .sorted()
    }

    private fun extractRelativePath(absolutePath: String): String {
        val markers = listOf("/sdcard/", "/storage/emulated/0/", "/storage/sdcard0/")
        for (marker in markers) {
            if (absolutePath.startsWith(marker)) {
                val after = absolutePath.removePrefix(marker)
                val lastSlash = after.lastIndexOf('/')
                return if (lastSlash >= 0) after.substring(0, lastSlash + 1) else ""
            }
        }
        return ""
    }
}
