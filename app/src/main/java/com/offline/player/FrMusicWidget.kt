package com.offline.player

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent

class FrMusicWidget : AppWidgetProvider() {

    companion object {
        const val ACTION_PLAY_PAUSE = "com.offline.player.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.offline.player.ACTION_NEXT"
        const val ACTION_PREV = "com.offline.player.ACTION_PREV"

        fun updateWidget(
            ctx: Context,
            title: String,
            artist: String,
            isPlaying: Boolean,
            posMs: Long = 0L,
            durMs: Long = 0L
        ) {
            val player = PlaybackService.activePlayer
            val artUri = player?.currentMediaItem?.mediaMetadata?.artworkUri
            val curPos = if (posMs > 0L) posMs else (player?.currentPosition ?: 0L)
            val curDur = if (durMs > 0L) durMs else (player?.duration?.coerceAtLeast(0L) ?: 0L)

            WidgetHelper.updateAll(
                context = ctx,
                title = title,
                artist = artist,
                artworkUri = artUri,
                isPlaying = isPlaying,
                posMs = curPos,
                durMs = curDur
            )
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val player = PlaybackService.activePlayer
        val title = player?.currentMediaItem?.mediaMetadata?.title?.toString() ?: "FR Music Player"
        val artist = player?.currentMediaItem?.mediaMetadata?.artist?.toString() ?: "Tap to play"
        val isPlaying = player?.isPlaying ?: false
        val artUri = player?.currentMediaItem?.mediaMetadata?.artworkUri
        val pos = player?.currentPosition ?: 0L
        val dur = player?.duration?.coerceAtLeast(0L) ?: 0L

        WidgetHelper.updateAll(context, title, artist, artUri, isPlaying, pos, dur)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val player = PlaybackService.activePlayer
        when (intent.action) {
            ACTION_PLAY_PAUSE -> {
                if (PlaybackService.isOnlineActive) {
                    OnlineStreamManager.toggle()
                } else {
                    player?.let {
                        if (it.isPlaying) it.pause() else it.play()
                    }
                }
            }
            ACTION_NEXT -> {
                if (PlaybackService.isOnlineActive) {
                    player?.let { c -> c.seekTo(c.currentPosition + 10000L) }
                } else {
                    player?.seekToNext()
                }
            }
            ACTION_PREV -> {
                if (PlaybackService.isOnlineActive) {
                    player?.let { c -> c.seekTo((c.currentPosition - 10000L).coerceAtLeast(0L)) }
                } else {
                    player?.seekToPrevious()
                }
            }
        }
    }
}
