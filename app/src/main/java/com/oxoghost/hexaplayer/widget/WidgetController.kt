package com.oxoghost.hexaplayer.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ContentUris
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.oxoghost.hexaplayer.service.MusicService

object WidgetController {

    const val ACTION_PLAY_PAUSE = "com.oxoghost.hexaplayer.widget.PLAY_PAUSE"
    const val ACTION_NEXT       = "com.oxoghost.hexaplayer.widget.NEXT"
    const val ACTION_PREVIOUS   = "com.oxoghost.hexaplayer.widget.PREVIOUS"
    const val ACTION_REPEAT     = "com.oxoghost.hexaplayer.widget.REPEAT"
    const val ACTION_SHUFFLE    = "com.oxoghost.hexaplayer.widget.SHUFFLE"

    private const val PREFS        = "widget_prefs"
    private const val KEY_TITLE    = "widget_title"
    private const val KEY_ARTIST   = "widget_artist"
    private const val KEY_ALBUM_ID = "widget_album_id"
    private const val KEY_PLAYING  = "widget_is_playing"

    fun saveState(context: Context, title: String, artist: String, albumId: Long, isPlaying: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_TITLE, title)
            .putString(KEY_ARTIST, artist)
            .putLong(KEY_ALBUM_ID, albumId)
            .putBoolean(KEY_PLAYING, isPlaying)
            .apply()
    }

    fun getTitle(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TITLE, "") ?: ""

    fun getArtist(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ARTIST, "") ?: ""

    fun getAlbumId(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_ALBUM_ID, -1L)

    fun isPlaying(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_PLAYING, false)

    fun loadAlbumArt(context: Context, albumId: Long): Bitmap? {
        if (albumId <= 0L) return null
        return try {
            val uri = ContentUris.withAppendedId(
                Uri.parse("content://media/external/audio/albumart"), albumId
            )
            val raw = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                ?: return null
            val size = minOf(raw.width, raw.height)
            val x = (raw.width - size) / 2
            val y = (raw.height - size) / 2
            Bitmap.createBitmap(raw, x, y, size, size)
        } catch (_: Exception) { null }
    }

    fun pendingIntent(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, MusicService::class.java).apply { this.action = action }
        return PendingIntent.getService(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun openAppIntent(context: Context): PendingIntent {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent()
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun updateAllWidgets(context: Context) {
        triggerUpdate(context, HexaWidget4x1::class.java)
        triggerUpdate(context, HexaWidget5x1::class.java)
    }

    private fun triggerUpdate(context: Context, cls: Class<*>) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, cls))
        if (ids.isNotEmpty()) {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                component = ComponentName(context, cls)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(intent)
        }
    }
}
