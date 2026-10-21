package com.oxoghost.hexaplayer.ui.common

import android.content.Context
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

/** Seed color of a song's cover (Palette vibrant, then dominant), null when it has no art. */
@Composable
fun rememberAlbumSeed(song: Song?): Color? {
    val context = LocalContext.current
    val key = song?.albumKey
    val seed by produceState(AlbumSeeds.cached(key), key) { value = song?.let { AlbumSeeds.load(context, it) } }
    return seed
}

object AlbumSeeds {
    private val cache = LruCache<String, Color>(64)
    private val misses = HashSet<String>()

    fun cached(key: String?): Color? = key?.let { cache.get(it) }

    suspend fun load(context: Context, song: Song): Color? {
        val key = song.albumKey
        cache.get(key)?.let { return it }
        if (key in misses) return null
        val request = ImageRequest.Builder(context).data(song.artModel).size(96).allowHardware(false).build()
        val result = context.imageLoader.execute(request) as? SuccessResult
        val color = result?.let {
            withContext(Dispatchers.Default) {
                val palette = Palette.from(it.image.toBitmap()).generate()
                (palette.vibrantSwatch ?: palette.dominantSwatch)?.rgb?.let(::Color)
            }
        }
        if (color != null) cache.put(key, color) else misses += key
        return color
    }
}
