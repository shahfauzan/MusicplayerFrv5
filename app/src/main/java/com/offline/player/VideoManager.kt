package com.offline.player

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import java.io.File

data class VideoItem(
    val id: Long,
    val title: String,
    val path: String,
    val uri: Uri,
    val duration: Long,
    val size: Long,
    val width: Int,
    val height: Int,
    val folderName: String,
    val folderPath: String,
    val dateAdded: Long
) {
    val resolutionLabel: String
        get() {
            if (width <= 0 || height <= 0) return ""
            val h = minOf(width, height)
            val w = maxOf(width, height)
            return when {
                w >= 3800 || h >= 2100 -> "4K"
                w >= 2500 || h >= 1400 -> "2K"
                w >= 1900 || h >= 1000 -> "1080p"
                w >= 1200 || h >= 700 -> "720p"
                w >= 800 || h >= 460 -> "480p"
                else -> "${h}p"
            }
        }

    val formattedSize: String
        get() {
            if (size <= 0) return ""
            val mb = size / (1024.0 * 1024.0)
            return if (mb >= 1000.0) {
                "%.1f GB".format(mb / 1024.0)
            } else {
                "%.1f MB".format(mb)
            }
        }

    fun toMediaItem(): MediaItem {
        return MediaItem.Builder()
            .setMediaId("video_$id")
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(folderName)
                    .setArtworkUri(uri)
                    .setDisplayTitle(title)
                    .setIsPlayable(true)
                    .build()
            )
            .build()
    }
}

data class VideoFolder(
    val name: String,
    val path: String,
    val count: Int,
    val thumbUri: Uri,
    val videos: List<VideoItem>
)

object VideoManager {
    fun loadVideos(context: Context): List<VideoItem> {
        val list = mutableListOf<VideoItem>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.DATE_ADDED
        )

        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        try {
            context.contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val wCol = cursor.getColumnIndex(MediaStore.Video.Media.WIDTH)
                val hCol = cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT)
                val dateCol = cursor.getColumnIndex(MediaStore.Video.Media.DATE_ADDED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val rawTitle = cursor.getString(titleCol) ?: "Video $id"
                    val path = cursor.getString(dataCol) ?: ""
                    val duration = cursor.getLong(durCol)
                    val size = cursor.getLong(sizeCol)
                    val width = if (wCol != -1) cursor.getInt(wCol) else 0
                    val height = if (hCol != -1) cursor.getInt(hCol) else 0
                    val dateAdded = if (dateCol != -1) cursor.getLong(dateCol) else 0L

                    val contentUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)

                    val file = File(path)
                    val folderFile = file.parentFile
                    val folderName = folderFile?.name ?: "Videos"
                    val folderPath = folderFile?.absolutePath ?: ""
                    val cleanTitle = if (rawTitle.isBlank()) file.nameWithoutExtension else rawTitle

                    list.add(
                        VideoItem(
                            id = id,
                            title = cleanTitle,
                            path = path,
                            uri = contentUri,
                            duration = duration,
                            size = size,
                            width = width,
                            height = height,
                            folderName = folderName,
                            folderPath = folderPath,
                            dateAdded = dateAdded
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // handle permission or query failure safely
        }
        return list
    }

    fun groupByFolder(videos: List<VideoItem>): List<VideoFolder> {
        return videos.groupBy { it.folderPath }.map { (path, vids) ->
            val folderName = vids.firstOrNull()?.folderName ?: "Folder"
            val thumb = vids.first().uri
            VideoFolder(
                name = folderName,
                path = path,
                count = vids.size,
                thumbUri = thumb,
                videos = vids
            )
        }.sortedBy { it.name.lowercase() }
    }
}
