package com.offline.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController

/**
 * Mirror of the ExoPlayer state. Updated only from Player callbacks,
 * ensuring UI cannot drift from actual playback.
 */
class PlayerState {
    var controller by mutableStateOf<MediaController?>(null)
    var current by mutableStateOf<MediaItem?>(null)
    var playing by mutableStateOf(false)
    var shuffle by mutableStateOf(false)
    var repeat by mutableIntStateOf(Player.REPEAT_MODE_OFF)
    var pos by mutableLongStateOf(0L)
    var dur by mutableLongStateOf(0L)
    var queue by mutableStateOf<List<MediaItem>>(emptyList())
    var index by mutableIntStateOf(0)

    fun sync(pl: Player, rebuildQueue: Boolean) {
        current = pl.currentMediaItem
        playing = pl.isPlaying
        shuffle = pl.shuffleModeEnabled
        repeat = pl.repeatMode
        index = pl.currentMediaItemIndex
        if (rebuildQueue) {
            queue = List(pl.mediaItemCount) { pl.getMediaItemAt(it) }
        }
    }

    fun toggle() {
        controller?.let {
            if (it.isPlaying) it.pause() else it.play()
        }
    }

    fun playSongs(list: List<Song>, start: Int, shuffleOn: Boolean? = null) {
        val c = controller ?: return
        if (list.isEmpty()) return
        shuffleOn?.let { c.shuffleModeEnabled = it }
        c.setMediaItems(list.map { it.toItem() }, start.coerceIn(0, list.lastIndex), 0L)
        c.prepare()
        c.play()
    }

    fun playNext(s: Song) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) {
            c.setMediaItem(s.toItem())
            c.prepare()
            c.play()
        } else {
            c.addMediaItem(c.currentMediaItemIndex + 1, s.toItem())
        }
    }

    fun addToQueue(s: Song) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) {
            c.setMediaItem(s.toItem())
            c.prepare()
        } else {
            c.addMediaItem(s.toItem())
        }
    }

    fun removeAt(i: Int) {
        controller?.let {
            if (i in 0 until it.mediaItemCount) it.removeMediaItem(i)
        }
    }

    fun move(from: Int, to: Int) {
        val c = controller ?: return
        if (from in 0 until c.mediaItemCount && to in 0 until c.mediaItemCount) {
            c.moveMediaItem(from, to)
        }
    }

    fun clearUpcoming() {
        val c = controller ?: return
        val cur = c.currentMediaItemIndex
        if (cur + 1 < c.mediaItemCount) c.removeMediaItems(cur + 1, c.mediaItemCount)
        if (cur > 0) c.removeMediaItems(0, cur)
    }

    fun clearQueue() {
        val c = controller ?: return
        c.clearMediaItems()
    }

    fun jumpTo(i: Int) {
        controller?.let {
            it.seekTo(i, 0L)
            it.play()
        }
    }
}

sealed class Detail {
    data class Album(val id: Long) : Detail()
    data class Artist(val name: String) : Detail()
    data class PL(val id: Long) : Detail()
    data class Folder(val path: String, val name: String) : Detail()
    object Favorites : Detail()
    object History : Detail()
    object Vault : Detail()
}

class AppState(
    val prefs: Prefs,
    val pls: PlaylistStore,
    val ps: PlayerState,
    val history: HistoryStore,
    val lyrics: LyricsStore,
    val eq: EqualizerManager,
    val vault: VaultStore,
    val meta: MetadataStore
) {
    /** All unhidden songs loaded from device */
    var songs by mutableStateOf<List<Song>>(emptyList()); private set
    /** All songs including hidden ones for Vault */
    var rawSongs by mutableStateOf<List<Song>>(emptyList()); private set
    var byId by mutableStateOf<Map<Long, Song>>(emptyMap()); private set

    /** Song waiting for "add to playlist" dialog */
    var pickFor by mutableStateOf<List<Song>?>(null)

    fun setSongsList(l: List<Song>) {
        rawSongs = l
        val unhidden = l.filter { !vault.isHidden(it.id) }
        songs = unhidden
        byId = l.associateBy { it.id }
    }

    fun refreshHiddenState() {
        songs = rawSongs.filter { !vault.isHidden(it.id) }
    }
}

fun saveSession(ps: PlayerState, prefs: Prefs) {
    val c = ps.controller ?: return
    if (c.mediaItemCount == 0) return
    val ids = buildString {
        for (i in 0 until c.mediaItemCount) {
            if (i > 0) append(',')
            append(c.getMediaItemAt(i).mediaId)
        }
    }
    prefs.saveSession(ids, c.currentMediaItemIndex, c.currentPosition)
}
