package com.oxoghost.hexaplayer.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.graphics.Color
import android.widget.RemoteViews
import com.oxoghost.hexaplayer.R

class HexaWidget5x1 : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult = goAsync()
        Thread {
            try {
                for (id in appWidgetIds) {
                    updateWidget(context, appWidgetManager, id)
                }
            } finally {
                pendingResult.finish()
            }
        }.start()
    }

    companion object {
        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, widgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_5x1)

            val title = WidgetController.getTitle(context).ifBlank { "Not playing" }
            val artist = WidgetController.getArtist(context)
            val songInfo = if (artist.isNotBlank()) "$title  —  $artist" else title
            views.setTextViewText(R.id.widget_song_info, songInfo)

            val albumId = WidgetController.getAlbumId(context)
            val art = WidgetController.loadAlbumArt(context, albumId)
            if (art != null) {
                views.setImageViewBitmap(R.id.widget_album_art, art)
            } else {
                views.setImageViewResource(R.id.widget_album_art, R.drawable.ic_music_note)
            }

            val playIcon = if (WidgetController.isPlaying(context)) R.drawable.ic_pause else R.drawable.ic_play_arrow
            views.setImageViewResource(R.id.widget_play_pause, playIcon)

            // Tint all control icons white
            val white = Color.WHITE
            views.setInt(R.id.widget_repeat, "setColorFilter", white)
            views.setInt(R.id.widget_previous, "setColorFilter", white)
            views.setInt(R.id.widget_play_pause, "setColorFilter", white)
            views.setInt(R.id.widget_next, "setColorFilter", white)
            views.setInt(R.id.widget_shuffle, "setColorFilter", white)

            views.setOnClickPendingIntent(R.id.widget_repeat,
                WidgetController.pendingIntent(context, WidgetController.ACTION_REPEAT, 20))
            views.setOnClickPendingIntent(R.id.widget_previous,
                WidgetController.pendingIntent(context, WidgetController.ACTION_PREVIOUS, 21))
            views.setOnClickPendingIntent(R.id.widget_play_pause,
                WidgetController.pendingIntent(context, WidgetController.ACTION_PLAY_PAUSE, 22))
            views.setOnClickPendingIntent(R.id.widget_next,
                WidgetController.pendingIntent(context, WidgetController.ACTION_NEXT, 23))
            views.setOnClickPendingIntent(R.id.widget_shuffle,
                WidgetController.pendingIntent(context, WidgetController.ACTION_SHUFFLE, 24))
            views.setOnClickPendingIntent(R.id.widget_root,
                WidgetController.openAppIntent(context))

            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }
}
