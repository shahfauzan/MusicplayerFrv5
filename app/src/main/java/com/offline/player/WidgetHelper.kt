package com.offline.player

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.util.Size
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object WidgetHelper {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var cachedArtUri: Uri? = null
    private var cachedBitmap: Bitmap? = null

    fun updateAll(
        context: Context,
        title: String,
        artist: String,
        artworkUri: Uri?,
        isPlaying: Boolean,
        posMs: Long,
        durMs: Long
    ) {
        scope.launch {
            val artBitmap = loadOrGenerateThumbnail(context, artworkUri, title)
            withContext(Dispatchers.Main) {
                updateCardWidget(context, title, artist, artBitmap, isPlaying, posMs, durMs)
                updatePillWidget(context, title, artist, artBitmap, isPlaying, posMs, durMs)
            }
        }
    }

    private fun loadOrGenerateThumbnail(context: Context, uri: Uri?, title: String): Bitmap {
        if (uri != null && uri == cachedArtUri && cachedBitmap != null) {
            return cachedBitmap!!
        }

        if (uri != null) {
            try {
                val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    context.contentResolver.loadThumbnail(uri, Size(140, 140), null)
                } else {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }
                }
                if (bmp != null) {
                    val rounded = getRoundedCornerBitmap(bmp, 28f)
                    cachedArtUri = uri
                    cachedBitmap = rounded
                    return rounded
                }
            } catch (_: Exception) {
                // fall through to generator
            }
        }

        // Generate luxury silver/graphite placeholder
        val sz = 140
        val bmp = Bitmap.createBitmap(sz, sz, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Gradient background
        paint.shader = LinearGradient(
            0f, 0f, sz.toFloat(), sz.toFloat(),
            intArrayOf(0xFF2A2B32.toInt(), 0xFF141519.toInt()),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(RectF(0f, 0f, sz.toFloat(), sz.toFloat()), 28f, 28f, paint)

        // Subtle concentric vinyl ring
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.color = 0x33FFFFFF
        paint.strokeWidth = 2f
        canvas.drawCircle(sz / 2f, sz / 2f, sz * 0.32f, paint)

        // Monogram initial
        paint.style = Paint.Style.FILL
        paint.color = 0xFFE0E2EC.toInt()
        paint.textSize = 48f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER
        val initial = title.firstOrNull()?.uppercaseChar()?.toString() ?: "♪"
        val yPos = (canvas.height / 2f) - ((paint.descent() + paint.ascent()) / 2f)
        canvas.drawText(initial, sz / 2f, yPos, paint)

        cachedArtUri = uri
        cachedBitmap = bmp
        return bmp
    }

    private fun getRoundedCornerBitmap(bitmap: Bitmap, cornerRadius: Float): Bitmap {
        val size = minOf(bitmap.width, bitmap.height)
        val x = (bitmap.width - size) / 2
        val y = (bitmap.height - size) / 2
        val square = Bitmap.createBitmap(bitmap, x, y, size, size)

        val output = Bitmap.createBitmap(140, 140, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = RectF(0f, 0f, 140f, 140f)

        paint.color = Color.BLACK
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(square, null, rect, paint)
        return output
    }

    fun updateCardWidget(
        ctx: Context,
        title: String,
        artist: String,
        artBitmap: Bitmap?,
        isPlaying: Boolean,
        posMs: Long,
        durMs: Long
    ) {
        val appWidgetManager = AppWidgetManager.getInstance(ctx)
        val comp = ComponentName(ctx, FrMusicWidget::class.java)
        val ids = appWidgetManager.getAppWidgetIds(comp)
        if (ids.isEmpty()) return

        val timeStr = "${fmt(posMs)} / ${fmt(durMs)}"
        val progressVal = if (durMs > 0) ((posMs * 1000) / durMs).toInt().coerceIn(0, 1000) else 0

        for (id in ids) {
            val views = RemoteViews(ctx.packageName, R.layout.widget_music)
            views.setTextViewText(R.id.widget_title, title)
            views.setTextViewText(R.id.widget_artist, artist)
            views.setTextViewText(R.id.widget_time_seconds, timeStr)
            views.setProgressBar(R.id.widget_progress, 1000, progressVal, false)

            if (artBitmap != null) {
                views.setImageViewBitmap(R.id.widget_art, artBitmap)
            } else {
                views.setImageViewResource(R.id.widget_art, R.mipmap.ic_launcher)
            }

            views.setImageViewResource(
                R.id.widget_play,
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            )

            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val playIntent = Intent(ctx, FrMusicWidget::class.java).apply { action = FrMusicWidget.ACTION_PLAY_PAUSE }
            views.setOnClickPendingIntent(R.id.widget_play, PendingIntent.getBroadcast(ctx, 201, playIntent, flags))

            val nextIntent = Intent(ctx, FrMusicWidget::class.java).apply { action = FrMusicWidget.ACTION_NEXT }
            views.setOnClickPendingIntent(R.id.widget_next, PendingIntent.getBroadcast(ctx, 202, nextIntent, flags))

            val prevIntent = Intent(ctx, FrMusicWidget::class.java).apply { action = FrMusicWidget.ACTION_PREV }
            views.setOnClickPendingIntent(R.id.widget_prev, PendingIntent.getBroadcast(ctx, 203, prevIntent, flags))

            val openIntent = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            views.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(ctx, 200, openIntent, flags))

            appWidgetManager.updateAppWidget(id, views)
        }
    }

    fun updatePillWidget(
        ctx: Context,
        title: String,
        artist: String,
        artBitmap: Bitmap?,
        isPlaying: Boolean,
        posMs: Long,
        durMs: Long
    ) {
        val appWidgetManager = AppWidgetManager.getInstance(ctx)
        val comp = ComponentName(ctx, FrPillWidget::class.java)
        val ids = appWidgetManager.getAppWidgetIds(comp)
        if (ids.isEmpty()) return

        val timeStr = "${fmt(posMs)} / ${fmt(durMs)}"
        val progressVal = if (durMs > 0) ((posMs * 1000) / durMs).toInt().coerceIn(0, 1000) else 0

        for (id in ids) {
            val views = RemoteViews(ctx.packageName, R.layout.widget_pill)
            views.setTextViewText(R.id.widget_pill_title, title)
            views.setTextViewText(R.id.widget_pill_seconds, timeStr)
            views.setProgressBar(R.id.widget_pill_progress, 1000, progressVal, false)

            if (artBitmap != null) {
                views.setImageViewBitmap(R.id.widget_pill_art, artBitmap)
            } else {
                views.setImageViewResource(R.id.widget_pill_art, R.mipmap.ic_launcher)
            }

            views.setImageViewResource(
                R.id.widget_pill_play,
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            )

            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val playIntent = Intent(ctx, FrPillWidget::class.java).apply { action = FrPillWidget.ACTION_PLAY_PAUSE }
            views.setOnClickPendingIntent(R.id.widget_pill_play, PendingIntent.getBroadcast(ctx, 301, playIntent, flags))

            val nextIntent = Intent(ctx, FrPillWidget::class.java).apply { action = FrPillWidget.ACTION_NEXT }
            views.setOnClickPendingIntent(R.id.widget_pill_next, PendingIntent.getBroadcast(ctx, 302, nextIntent, flags))

            val prevIntent = Intent(ctx, FrPillWidget::class.java).apply { action = FrPillWidget.ACTION_PREV }
            views.setOnClickPendingIntent(R.id.widget_pill_prev, PendingIntent.getBroadcast(ctx, 303, prevIntent, flags))

            val openIntent = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            views.setOnClickPendingIntent(R.id.widget_pill_root, PendingIntent.getActivity(ctx, 300, openIntent, flags))

            appWidgetManager.updateAppWidget(id, views)
        }
    }
}
