package com.offline.player

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    companion object {
        var activeSessionId: Int = 0
            private set
        var activePlayer: ExoPlayer? = null
            private set
    }

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(), true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        activePlayer = player
        activeSessionId = player.audioSessionId

        player.addListener(object : Player.Listener {
            override fun onEvents(pl: Player, events: Player.Events) {
                activeSessionId = player.audioSessionId
                // Notify home-screen widget on track transitions or playback changes
                if (events.containsAny(
                        Player.EVENT_MEDIA_ITEM_TRANSITION,
                        Player.EVENT_IS_PLAYING_CHANGED,
                        Player.EVENT_PLAYBACK_STATE_CHANGED
                    )
                ) {
                    FrMusicWidget.updateWidget(
                        this@PlaybackService,
                        title = player.currentMediaItem?.mediaMetadata?.title?.toString() ?: "FR Music Player",
                        artist = player.currentMediaItem?.mediaMetadata?.artist?.toString() ?: "No song playing",
                        isPlaying = player.isPlaying
                    )
                }
            }
        })

        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        session = MediaSession.Builder(this, player).setSessionActivity(open).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        activePlayer = null
        activeSessionId = 0
        super.onDestroy()
    }
}
