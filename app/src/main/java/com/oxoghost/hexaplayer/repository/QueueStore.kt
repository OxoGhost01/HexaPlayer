package com.oxoghost.hexaplayer.repository

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import org.json.JSONArray
import org.json.JSONObject

/** One queue entry, with what is needed to rebuild it without scanning the library. */
data class SavedItem(
    val mediaId: String,
    val uri: Uri,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
) {
    /** MediaStore id of the song, from its content uri; -1 for files outside the library. */
    val songId: Long get() = runCatching { ContentUris.parseId(uri) }.getOrDefault(-1L)

    fun toMediaItem(): MediaItem = MediaItem.Builder()
        .setUri(uri)
        .setMediaId(mediaId)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId))
                .setExtras(Bundle().apply { putLong("albumId", albumId) })
                .build()
        )
        .build()

    companion object {
        fun of(item: MediaItem): SavedItem? {
            val uri = item.localConfiguration?.uri ?: return null
            val m = item.mediaMetadata
            return SavedItem(
                mediaId = item.mediaId,
                uri = uri,
                title = m.title?.toString().orEmpty(),
                artist = m.artist?.toString().orEmpty(),
                album = m.albumTitle?.toString().orEmpty(),
                albumId = m.extras?.getLong("albumId") ?: 0L,
            )
        }
    }
}

data class SavedQueue(
    val items: List<SavedItem>,
    val index: Int,
    val positionMs: Long,
    val shuffle: Boolean,
    val repeat: Int,
    val source: String?,
    val savedAt: Long,
) {
    val current: SavedItem? get() = items.getOrNull(index)
}

/**
 * The remembered queue (ARCHITECTURE.md > Remember queue). Written by the service on every
 * change, read by the app at start to restore it paused.
 */
class QueueStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("hexa_queue", Context.MODE_PRIVATE)

    /** Label of the current queue ("album · Showbiz"); set by the app when it starts a queue. */
    var source: String?
        get() = prefs.getString("source", null)
        set(v) {
            prefs.edit().putString("source", v).apply()
        }

    /** [shuffle] is the service's own shuffle flag (the player's is always off). */
    fun save(player: Player, shuffle: Boolean) {
        val items = (0 until player.mediaItemCount).mapNotNull { SavedItem.of(player.getMediaItemAt(it)) }
        if (items.isEmpty() || items.any { it.mediaId.startsWith(EXTERNAL_PREFIX) }) return
        val arr = JSONArray()
        items.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.mediaId).put("uri", it.uri.toString()).put("title", it.title)
                    .put("artist", it.artist).put("album", it.album).put("albumId", it.albumId)
            )
        }
        prefs.edit()
            .putString("items", arr.toString())
            .putInt("index", player.currentMediaItemIndex.coerceIn(0, items.lastIndex))
            .putLong("position", player.currentPosition.coerceAtLeast(0L))
            .putBoolean("shuffle", shuffle)
            .putInt("repeat", player.repeatMode)
            .putLong("savedAt", System.currentTimeMillis())
            .apply()
    }

    fun load(): SavedQueue? = runCatching {
        val arr = JSONArray(prefs.getString("items", null) ?: return null)
        val items = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            SavedItem(o.getString("id"), Uri.parse(o.getString("uri")), o.optString("title"), o.optString("artist"),
                o.optString("album"), o.optLong("albumId"))
        }
        if (items.isEmpty()) return null
        SavedQueue(
            items = items,
            index = prefs.getInt("index", 0).coerceIn(0, items.lastIndex),
            positionMs = prefs.getLong("position", 0L),
            shuffle = prefs.getBoolean("shuffle", false),
            repeat = prefs.getInt("repeat", Player.REPEAT_MODE_OFF),
            source = source,
            savedAt = prefs.getLong("savedAt", 0L),
        )
    }.getOrNull()

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        /** Media ids of files played through Open with: never remembered, never counted as plays. */
        const val EXTERNAL_PREFIX = "ext:"
    }
}
