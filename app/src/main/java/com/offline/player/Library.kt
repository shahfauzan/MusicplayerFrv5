package com.offline.player

import android.content.ContentUris
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import org.json.JSONObject
import java.io.File

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val uri: Uri,
    val dateAdded: Long = 0L,
    val track: Int = 0,
    val year: Int = 0,
    val inDownloads: Boolean = false,
    val folderPath: String = "",
    val folderName: String = "Internal",
    val sizeBytes: Long = 0L
) {
    /** Pre-lowercased search haystack including folder for fast search. */
    val key: String = "$title $artist $album $folderName".lowercase()

    val art: Uri
        get() = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)

    fun toItem(): MediaItem = MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(art)
                .build()
        ).build()
}

data class FolderItem(val name: String, val path: String, val songs: List<Song>)

val sorts = listOf("Title", "Artist", "Newest", "Oldest", "Duration", "Album")

fun sortSongs(l: List<Song>, mode: Int): List<Song> = when (mode) {
    1 -> l.sortedWith(compareBy<Song>({ it.artist.lowercase() }, { it.title.lowercase() }))
    2 -> l.sortedWith(compareByDescending<Song> { it.dateAdded }.thenByDescending { it.id })
    3 -> l.sortedWith(compareBy<Song>({ it.dateAdded }, { it.id }))
    4 -> l.sortedByDescending { it.durationMs }
    5 -> l.sortedWith(compareBy<Song>({ it.album.lowercase() }, { it.track % 1000 }, { it.title.lowercase() }))
    else -> l.sortedBy { it.title.lowercase() }
}

/**
 * Stores custom song tag overrides (title, artist, album, year)
 * so user edits in Tag Editor take effect instantly and persist.
 */
class MetadataStore(ctx: Context) {
    private val p: SharedPreferences = ctx.applicationContext.getSharedPreferences("fr_meta", Context.MODE_PRIVATE)

    fun getOverride(id: Long): TagOverride? {
        val s = p.getString("tag_$id", null) ?: return null
        return try {
            val j = JSONObject(s)
            TagOverride(
                title = j.optString("title").takeIf { it.isNotBlank() },
                artist = j.optString("artist").takeIf { it.isNotBlank() },
                album = j.optString("album").takeIf { it.isNotBlank() },
                year = j.optInt("year", 0).takeIf { it > 0 }
            )
        } catch (e: Exception) { null }
    }

    fun saveOverride(id: Long, title: String, artist: String, album: String, year: Int) {
        val j = JSONObject()
        j.put("title", title.trim())
        j.put("artist", artist.trim())
        j.put("album", album.trim())
        j.put("year", year)
        p.edit().putString("tag_$id", j.toString()).apply()
    }
}

data class TagOverride(val title: String?, val artist: String?, val album: String?, val year: Int?)

/**
 * Reads MediaStore audio library in the background.
 * Excludes songs under 10 seconds (ringtones/notifications) and excluded folders.
 */
fun loadSongs(ctx: Context, metaStore: MetadataStore? = null, excludedFolders: Set<String> = emptySet()): List<Song> {
    val out = ArrayList<Song>()
    val m = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    val pathCol = if (Build.VERSION.SDK_INT >= 29) MediaStore.Audio.Media.RELATIVE_PATH else MediaStore.Audio.Media.DATA
    val proj = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.ALBUM_ID,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.DATE_ADDED,
        MediaStore.Audio.Media.TRACK,
        MediaStore.Audio.Media.YEAR,
        MediaStore.Audio.Media.SIZE,
        pathCol
    )

    try {
        ctx.contentResolver.query(
            m, proj, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        )?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val iT = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val iAr = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val iAl = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val iAid = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val iD = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val iDa = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val iTr = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val iY = c.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val iSize = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val iP = c.getColumnIndexOrThrow(pathCol)

            while (c.moveToNext()) {
                val dur = c.getLong(iD)
                if (dur < 10_000L) continue // skip notification sounds / ringtones under 10s

                val id = c.getLong(iId)
                val rawPath = c.getString(iP) ?: ""
                val folderPath = if (Build.VERSION.SDK_INT >= 29) {
                    rawPath.trimEnd('/')
                } else {
                    File(rawPath).parent ?: ""
                }
                val folderName = if (folderPath.isNotBlank()) {
                    File(folderPath).name.ifBlank { "Music" }
                } else "Music"

                if (folderName in excludedFolders || folderPath in excludedFolders) continue

                val ov = metaStore?.getOverride(id)
                val title = ov?.title ?: c.getString(iT)?.takeIf { it.isNotBlank() } ?: "Unknown"
                val artist = ov?.artist ?: c.getString(iAr)?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "Unknown artist"
                val album = ov?.album ?: c.getString(iAl)?.takeIf { it != "<unknown>" } ?: ""
                val year = ov?.year ?: c.getInt(iY)

                val inDownloads = if (Build.VERSION.SDK_INT >= 29) {
                    rawPath.startsWith("Download", ignoreCase = true)
                } else {
                    rawPath.contains("/Download/", ignoreCase = true)
                }

                out.add(
                    Song(
                        id = id,
                        title = title,
                        artist = artist,
                        album = album,
                        albumId = c.getLong(iAid),
                        durationMs = dur,
                        uri = ContentUris.withAppendedId(m, id),
                        dateAdded = c.getLong(iDa),
                        track = c.getInt(iTr),
                        year = year,
                        inDownloads = inDownloads,
                        folderPath = folderPath,
                        folderName = folderName,
                        sizeBytes = c.getLong(iSize)
                    )
                )
            }
        }
    } catch (e: SecurityException) {
        // Permission revoked
    } catch (e: Exception) {
        // Catch any MediaStore driver anomalies
    }
    return out
}

fun groupSongsByFolder(songs: List<Song>): List<FolderItem> {
    return songs.groupBy { it.folderPath.ifBlank { it.folderName } }
        .map { (path, list) ->
            val name = list.firstOrNull()?.folderName ?: "Music"
            FolderItem(name, path, list)
        }.sortedBy { it.name.lowercase() }
}
