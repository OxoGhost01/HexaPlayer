package com.oxoghost.hexaplayer.ui.common

import android.content.ContentUris
import android.net.Uri
import com.oxoghost.hexaplayer.data.Album
import com.oxoghost.hexaplayer.data.Song
import java.util.Locale

fun albumArtUri(albumId: Long): Uri =
    ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)

/** Custom cover override, or the MediaStore album art. */
val Song.artModel: Any get() = customCoverUri?.let(Uri::parse) ?: albumArtUri(albumId)

/** Key for placeholder art and pixel cover caches: same album, same art. */
val Song.albumKey: String get() = customCoverUri ?: albumId.toString()

val Album.artModel: Any get() = albumArtUri(id)
val Album.albumKey: String get() = id.toString()
val Album.durationMs: Long get() = songs.sumOf { it.duration }

/** File extension in lower case (flac, opus, mp3), or "" when unknown. */
val Song.format: String get() = displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)

/** 4.1 MB (or 4.1M when [short]). */
fun formatSize(bytes: Long, short: Boolean = false): String {
    if (bytes <= 0) return ""
    val mb = bytes / 1_048_576.0
    return if (mb >= 1) "%.1f%s".format(mb, if (short) "M" else " MB")
    else "%.0f%s".format(bytes / 1024.0, if (short) "K" else " KB")
}
