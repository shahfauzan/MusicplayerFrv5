package com.offline.player

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class FrMusicWidget : AppWidgetProvider() {

    companion object {
        const val ACTION_PLAY_PAUSE = "com.offline.player.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.offline.player.ACTION_NEXT"
        const val ACTION_PREV = "com.offline.player.ACTION_PREV"

        fun updateWidget(ctx: Context, title: String, artist: String, isPlaying: Boolean) {
            val appWidgetManager = AppWidgetManager.getInstance(ctx)
            val thisWidget = ComponentName(ctx, FrMusicWidget::class.java)
            val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            for (widgetId in allWidgetIds) {
                val views = RemoteViews(ctx.packageName, R.layout.widget_music)
                views.setTextViewText(R.id.widget_title, title)
                views.setTextViewText(R.id.widget_artist, artist)
                views.setImageViewResource(
                    R.id.widget_play,
                    if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
                )
                setupIntents(ctx, views)
                appWidgetManager.updateAppWidget(widgetId, views)
            }
        }

        private fun setupIntents(ctx: Context, views: RemoteViews) {
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

            val playIntent = Intent(ctx, FrMusicWidget::class.java).apply { action = ACTION_PLAY_PAUSE }
            views.setOnClickPendingIntent(R.id.widget_play, PendingIntent.getBroadcast(ctx, 101, playIntent, flags))

            val nextIntent = Intent(ctx, FrMusicWidget::class.java).apply { action = ACTION_NEXT }
            views.setOnClickPendingIntent(R.id.widget_next, PendingIntent.getBroadcast(ctx, 102, nextIntent, flags))

            val prevIntent = Intent(ctx, FrMusicWidget::class.java).apply { action = ACTION_PREV }
            views.setOnClickPendingIntent(R.id.widget_prev, PendingIntent.getBroadcast(ctx, 103, prevIntent, flags))

            val openIntent = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            views.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(ctx, 100, openIntent, flags))
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val player = PlaybackService.activePlayer
        val title = player?.currentMediaItem?.mediaMetadata?.title?.toString() ?: "FR Music Player"
        val artist = player?.currentMediaItem?.mediaMetadata?.artist?.toString() ?: "Tap to play"
        val isPlaying = player?.isPlaying ?: false

        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_music)
            views.setTextViewText(R.id.widget_title, title)
            views.setTextViewText(R.id.widget_artist, artist)
            views.setImageViewResource(
                R.id.widget_play,
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            )
            setupIntents(context, views)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val player = PlaybackService.activePlayer
        when (intent.action) {
            ACTION_PLAY_PAUSE -> {
                player?.let {
                    if (it.isPlaying) it.pause() else it.play()
                }
            }
            ACTION_NEXT -> {
                player?.seekToNext()
            }
            ACTION_PREV -> {
                player?.seekToPrevious()
            }
        }
    }
}
