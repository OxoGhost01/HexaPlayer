package com.oxoghost.hexaplayer.service

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import com.oxoghost.hexaplayer.repository.CoverRepository
import com.oxoghost.hexaplayer.repository.PlaylistRepository
import java.io.FileNotFoundException

/**
 * Read-only covers for Android Auto (see Artwork): `/song/{id}`, `/album/{id}`, `/playlist/{id}`.
 * Not exported; MusicService grants Auto read access when it connects.
 */
class ArtworkProvider : ContentProvider() {

    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw SecurityException("Covers are read only")
        val ctx = context ?: throw FileNotFoundException(uri.toString())
        val id = uri.pathSegments.getOrNull(1) ?: throw FileNotFoundException(uri.toString())
        val sources = when (uri.pathSegments.firstOrNull()) {
            "song" -> id.toLongOrNull()?.let { songId ->
                listOfNotNull(
                    CoverRepository(ctx).getOverride(songId)?.let(Uri::parse),
                    albumIdOf(ctx, songId)?.let(::albumArt),
                )
            }
            "album" -> id.toLongOrNull()?.let { listOf(albumArt(it)) }
            "playlist" -> PlaylistRepository(ctx).getAll().firstOrNull { it.id == id }?.let { playlist ->
                listOfNotNull(
                    playlist.coverUri?.let(Uri::parse),
                    playlist.songIds.firstOrNull()?.let { albumIdOf(ctx, it) }?.let(::albumArt),
                )
            }
            else -> null
        }.orEmpty()
        // A picked cover can lose its permission; the album art is the fallback.
        for (source in sources) {
            runCatching { ctx.contentResolver.openFileDescriptor(source, "r") }.getOrNull()?.let { return it }
        }
        throw FileNotFoundException(uri.toString())
    }

    override fun getType(uri: Uri) = "image/*"

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0

    private fun albumArt(albumId: Long): Uri =
        ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)

    private fun albumIdOf(context: Context, songId: Long): Long? =
        context.contentResolver.query(
            ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, songId),
            arrayOf(MediaStore.Audio.Media.ALBUM_ID), null, null, null,
        )?.use { if (it.moveToFirst()) it.getLong(0) else null }
}
