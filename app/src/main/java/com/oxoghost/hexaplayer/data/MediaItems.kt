package com.oxoghost.hexaplayer.data

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.oxoghost.hexaplayer.BuildConfig

/**
 * Cover links served by ArtworkProvider. Android Auto can't open MediaStore album art or the
 * app's own files, so everything the player and the car show points here instead.
 */
object Artwork {
    const val AUTHORITY = "${BuildConfig.APPLICATION_ID}.art"
    val root: Uri = Uri.parse("content://$AUTHORITY")

    /** The song's custom cover, or its album art. */
    fun song(songId: Long): Uri = root.buildUpon().appendPath("song").appendPath(songId.toString()).build()
    fun album(albumId: Long): Uri = root.buildUpon().appendPath("album").appendPath(albumId.toString()).build()
    fun playlist(playlistId: String): Uri = root.buildUpon().appendPath("playlist").appendPath(playlistId).build()
}

/**
 * The one way a library song becomes a player item, so the app, Android Auto, the widget and the
 * play history all find the same fields (the widget and history read the albumId extra).
 */
fun Song.toMediaItem(mediaId: String): MediaItem = MediaItem.Builder()
    .setUri(uri)
    .setMediaId(mediaId)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setArtworkUri(Artwork.song(id))
            .setDurationMs(duration.takeIf { it > 0 })
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setExtras(Bundle().apply { putLong("albumId", albumId) })
            .build()
    )
    .build()
