package com.offline.player

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    companion object {
        var activeSessionId: Int = 0
            private set
        var activePlayer: ExoPlayer? = null
            private set
        var isOnlineActive: Boolean = false
            private set

        fun getOrCreateSilenceWav(context: Context): Uri {
            val file = File(context.cacheDir, "silence_loop.wav")
            if (!file.exists() || file.length() < 44) {
                try {
                    val sampleRate = 44100
                    val numChannels = 1
                    val bitsPerSample = 16
                    val durationSec = 1
                    val dataSize = sampleRate * numChannels * (bitsPerSample / 8) * durationSec
                    val totalSize = 36 + dataSize
                    val byteRate = sampleRate * numChannels * (bitsPerSample / 8)
                    val blockAlign = numChannels * (bitsPerSample / 8)

                    file.outputStream().use { out ->
                        val bb = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
                        bb.put("RIFF".toByteArray(Charsets.US_ASCII))
                        bb.putInt(totalSize)
                        bb.put("WAVE".toByteArray(Charsets.US_ASCII))
                        bb.put("fmt ".toByteArray(Charsets.US_ASCII))
                        bb.putInt(16)
                        bb.putShort(1.toShort())
                        bb.putShort(numChannels.toShort())
                        bb.putInt(sampleRate)
                        bb.putInt(byteRate)
                        bb.putShort(blockAlign.toShort())
                        bb.putShort(bitsPerSample.toShort())
                        bb.put("data".toByteArray(Charsets.US_ASCII))
                        bb.putInt(dataSize)
                        out.write(bb.array())
                        val zeroes = ByteArray(4096)
                        var written = 0
                        while (written < dataSize) {
                            val toWrite = minOf(zeroes.size, dataSize - written)
                            out.write(zeroes, 0, toWrite)
                            written += toWrite
                        }
                    }
                } catch (e: Exception) {
                    // fallback
                }
            }
            return Uri.fromFile(file)
        }

        fun startOnlineStream(context: Context, title: String, artist: String, artworkUri: Uri?) {
            val player = activePlayer ?: return
            isOnlineActive = true
            val silenceUri = getOrCreateSilenceWav(context)
            val metadata = MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setArtworkUri(artworkUri)
                .setDisplayTitle(title)
                .setIsPlayable(true)
                .build()
            val item = MediaItem.Builder()
                .setMediaId("online_stream")
                .setUri(silenceUri)
                .setMediaMetadata(metadata)
                .build()
            player.repeatMode = Player.REPEAT_MODE_ALL
            player.volume = 0f
            player.setMediaItem(item)
            player.prepare()
            player.play()
            FrMusicWidget.updateWidget(context, title, artist, true)
        }

        fun stopOnlineStream() {
            if (!isOnlineActive) return
            isOnlineActive = false
            val player = activePlayer ?: return
            player.volume = 1f
            player.stop()
            player.clearMediaItems()
        }
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
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isOnlineActive) {
                    OnlineStreamManager.onRemotePlayPauseChanged(isPlaying)
                }
            }

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
        if (isOnlineActive && OnlineStreamManager.isPlaying) {
            // Keep playing online stream in background when app is swiped from recents!
            return
        }
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
