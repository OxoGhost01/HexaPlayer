package com.oxoghost.hexaplayer.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Terminal cover: the art averaged down to [res] x [res] cells and drawn without filtering.
 * With [pixelated] off it shows the real cover with square corners.
 */
@Composable
fun PixelCover(
    model: Any?,
    albumKey: String?,
    res: Int,
    modifier: Modifier = Modifier,
    pixelated: Boolean = true,
    contentDescription: String? = null,
) {
    if (!pixelated) {
        CoverImage(model, albumKey, modifier, RectangleShape, contentDescription)
        return
    }
    val context = LocalContext.current
    val bitmap by produceState(PixelCovers.cached(albumKey, res), model, albumKey, res) {
        value = PixelCovers.load(context, model, albumKey, res)
    }
    Image(
        bitmap = bitmap,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.FillBounds,
        filterQuality = FilterQuality.None,
    )
}

object PixelCovers {
    private val cache = LruCache<String, ImageBitmap>(256)

    private fun key(albumKey: String?, res: Int) = "${albumKey ?: "?"}@$res"

    /** Cached pixels, or the placeholder while the real cover loads. */
    fun cached(albumKey: String?, res: Int): ImageBitmap =
        cache.get(key(albumKey, res)) ?: placeholder(albumKey, res)

    suspend fun load(context: Context, model: Any?, albumKey: String?, res: Int): ImageBitmap {
        val k = key(albumKey, res)
        cache.get(k)?.let { return it }
        val loaded = model?.let { decode(context, it, res) }
        if (loaded != null) cache.put(k, loaded)
        return loaded ?: placeholder(albumKey, res)
    }

    private fun placeholder(albumKey: String?, res: Int): ImageBitmap {
        val k = "placeholder:" + key(albumKey, res)
        return cache.get(k) ?: Placeholders.pixels(Placeholders.art(albumKey), res).also { cache.put(k, it) }
    }

    private suspend fun decode(context: Context, model: Any, res: Int): ImageBitmap? {
        val request = ImageRequest.Builder(context)
            .data(model)
            .size(res * 8)
            .allowHardware(false)
            .build()
        val result = context.imageLoader.execute(request) as? SuccessResult ?: return null
        return withContext(Dispatchers.Default) { average(result.image.toBitmap(), res) }
    }

    /** Center-crops to a square and averages every source pixel into its cell. */
    internal fun average(src: Bitmap, res: Int): ImageBitmap {
        val side = minOf(src.width, src.height)
        val left = (src.width - side) / 2
        val top = (src.height - side) / 2
        val pixels = IntArray(side * side)
        src.getPixels(pixels, 0, side, left, top, side, side)
        val r = LongArray(res * res)
        val g = LongArray(res * res)
        val b = LongArray(res * res)
        val n = IntArray(res * res)
        for (y in 0 until side) {
            val cy = y * res / side
            for (x in 0 until side) {
                val cell = cy * res + x * res / side
                val p = pixels[y * side + x]
                r[cell] += (p shr 16) and 0xFF
                g[cell] += (p shr 8) and 0xFF
                b[cell] += p and 0xFF
                n[cell]++
            }
        }
        val out = IntArray(res * res) { i ->
            val c = n[i].coerceAtLeast(1)
            (0xFF shl 24) or ((r[i] / c).toInt() shl 16) or ((g[i] / c).toInt() shl 8) or (b[i] / c).toInt()
        }
        return Bitmap.createBitmap(out, res, res, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
}
