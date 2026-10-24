package com.oxoghost.hexaplayer.widget

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.net.Uri
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState

/** Playback state shared between the music service and the home screen widgets. */
object WidgetController {

    const val ACTION_PLAY_PAUSE = "com.oxoghost.hexaplayer.widget.PLAY_PAUSE"
    const val ACTION_NEXT       = "com.oxoghost.hexaplayer.widget.NEXT"
    const val ACTION_PREVIOUS   = "com.oxoghost.hexaplayer.widget.PREVIOUS"
    const val ACTION_RESUME     = "com.oxoghost.hexaplayer.widget.RESUME"

    private const val PREFS = "widget_prefs"

    fun saveState(context: Context, title: String, artist: String, albumId: Long, isPlaying: Boolean, positionMs: Long, durationMs: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("widget_title", title)
            .putString("widget_artist", artist)
            .putLong("widget_album_id", albumId)
            .putBoolean("widget_is_playing", isPlaying)
            .putLong("widget_position", positionMs)
            .putLong("widget_duration", durationMs)
            .apply()
    }

    /** Nothing loaded in the player: the widgets show their idle state with Resume. */
    fun saveIdle(context: Context) = saveState(context, "", "", -1L, false, 0L, 0L)

    fun read(context: Context): WidgetData {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return WidgetData(
            title = p.getString("widget_title", "").orEmpty(),
            artist = p.getString("widget_artist", "").orEmpty(),
            albumId = p.getLong("widget_album_id", -1L),
            isPlaying = p.getBoolean("widget_is_playing", false),
            positionMs = p.getLong("widget_position", 0L),
            durationMs = p.getLong("widget_duration", 0L),
        )
    }

    /** Square-cropped album art, [sizePx] wide. */
    fun loadAlbumArt(context: Context, albumId: Long, sizePx: Int = 300): Bitmap? {
        if (albumId <= 0L) return null
        return try {
            val uri = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= sizePx) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val raw = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
            val size = minOf(raw.width, raw.height)
            val square = Bitmap.createBitmap(raw, (raw.width - size) / 2, (raw.height - size) / 2, size, size)
            Bitmap.createScaledBitmap(square, sizePx, sizePx, true)
        } catch (_: Exception) {
            null
        }
    }

    /** The terminal pixel cover: averaged down to [cells] x [cells], then scaled up without smoothing. */
    fun pixelate(bitmap: Bitmap, cells: Int, sizePx: Int): Bitmap {
        val small = Bitmap.createScaledBitmap(bitmap, cells, cells, true)
        return Bitmap.createScaledBitmap(small, sizePx, sizePx, false)
    }

    /** Rounded corners drawn into the bitmap, since widgets can't clip images before Android 12. */
    fun rounded(bitmap: Bitmap, radiusPx: Float): Bitmap {
        val out = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawRoundRect(RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat()), radiusPx, radiusPx, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return out
    }

    suspend fun updateAllWidgets(context: Context) {
        val now = System.currentTimeMillis()
        listOf(WideWidget(), SlimWidget()).forEach { widget ->
            GlanceAppWidgetManager(context).getGlanceIds(widget.javaClass).forEach { id ->
                updateAppWidgetState(context, id) { it[VersionKey] = now }
                widget.update(context, id)
            }
        }
    }
}

data class WidgetData(
    val title: String,
    val artist: String,
    val albumId: Long,
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long,
) {
    val idle: Boolean get() = title.isBlank()
    val fraction: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}
