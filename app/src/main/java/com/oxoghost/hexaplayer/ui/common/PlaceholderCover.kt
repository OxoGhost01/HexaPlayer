package com.oxoghost.hexaplayer.ui.common

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import com.oxoghost.hexaplayer.ui.tokens.CoverArtTokens
import com.oxoghost.hexaplayer.ui.tokens.CoverShape
import com.oxoghost.hexaplayer.ui.tokens.DesignTokens
import kotlin.math.abs

/** Generated art for songs without a cover (tokens.json > cover_placeholder). */
object Placeholders {
    /** Same album, same art: seed = abs(albumKey.hashCode()) % 8. Null = unknown file. */
    fun art(albumKey: String?): CoverArtTokens {
        val arts = DesignTokens.Cover.arts
        return if (albumKey == null) DesignTokens.Cover.unknownFile
        else arts[abs(albumKey.hashCode()) % arts.size]
    }

    /** Rasterized at res x res by sampling cell centers (terminal pixel covers). */
    fun pixels(art: CoverArtTokens, res: Int): ImageBitmap {
        val out = IntArray(res * res)
        for (y in 0 until res) for (x in 0 until res) {
            val u = (x + 0.5f) / res
            val v = (y + 0.5f) / res
            var c = art.background
            for (s in art.shapes) if (s.contains(u, v)) c = s.color
            out[y * res + x] = c.toArgb()
        }
        return Bitmap.createBitmap(out, res, res, Bitmap.Config.ARGB_8888).asImageBitmap()
    }

    private fun CoverShape.contains(u: Float, v: Float) = when (this) {
        is CoverShape.Circle -> (u - cx) * (u - cx) + (v - cy) * (v - cy) <= r * r
        is CoverShape.Rect -> u >= x && u < x + w && v >= y && v < y + h
    }
}

fun DrawScope.drawCoverArt(art: CoverArtTokens) {
    val w = size.width
    val h = size.height
    drawRect(art.background)
    for (s in art.shapes) when (s) {
        is CoverShape.Circle -> drawCircle(s.color, s.r * w, Offset(s.cx * w, s.cy * h))
        is CoverShape.Rect -> drawRect(s.color, Offset(s.x * w, s.y * h), Size(s.w * w, s.h * h))
    }
}

@Composable
fun PlaceholderCover(albumKey: String?, modifier: Modifier = Modifier) {
    val art = Placeholders.art(albumKey)
    Canvas(modifier) { drawCoverArt(art) }
}
