package com.oxoghost.hexaplayer.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.oxoghost.hexaplayer.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Seed color of a cover: Palette's vibrant swatch, then the dominant one. The widget uses it too. */
fun seedOf(bitmap: Bitmap): Color? {
    val palette = Palette.from(bitmap).generate()
    return (palette.vibrantSwatch ?: palette.dominantSwatch)?.rgb?.let(::Color)
}

/** Seed color of a song's cover, null when it has no art. */
@Composable
fun rememberAlbumSeed(song: Song?): Color? {
    val context = LocalContext.current
    val key = song?.albumKey
    val seed by produceState(AlbumSeeds.cached(key), key) { value = song?.let { AlbumSeeds.load(context, it) } }
    return seed
}

object AlbumSeeds {
    private val cache = LruCache<String, Color>(64)

    // A cover can be missing for a moment (a fresh download before MediaStore has its art),
    // so a miss is only trusted for a while instead of for the whole session.
    private val misses = HashMap<String, Long>()
    private const val RETRY_MS = 30_000L

    fun cached(key: String?): Color? = key?.let { cache.get(it) }

    suspend fun load(context: Context, song: Song): Color? {
        val key = song.albumKey
        cache.get(key)?.let { return it }
        misses[key]?.let { if (SystemClock.elapsedRealtime() - it < RETRY_MS) return null }
        // A picked cover can lose its permission; the album art is what the widget reads.
        val color = seedFrom(context, song.artModel)
            ?: song.customCoverUri?.let { seedFrom(context, albumArtUri(song.albumId)) }
        if (color != null) {
            cache.put(key, color)
            misses.remove(key)
        } else {
            misses[key] = SystemClock.elapsedRealtime()
        }
        return color
    }

    private suspend fun seedFrom(context: Context, model: Any): Color? {
        val request = ImageRequest.Builder(context).data(model).size(128).allowHardware(false).build()
        val result = context.imageLoader.execute(request) as? SuccessResult ?: return null
        return withContext(Dispatchers.Default) { seedOf(result.image.toBitmap()) }
    }
}
