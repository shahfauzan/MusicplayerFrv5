package com.offline.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.File

class CropState(val z: Float, val fx: Float, val fy: Float)

object AvatarStore {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("fr_avatar", Context.MODE_PRIVATE)
    private fun src(ctx: Context) = File(ctx.filesDir, "avatar_src.jpg")

    fun hasSource(ctx: Context) = src(ctx).exists()
    fun loadSource(ctx: Context): Bitmap? = if (src(ctx).exists()) BitmapFactory.decodeFile(src(ctx).path) else null
    fun loadCrop(ctx: Context): CropState? {
        val s = sp(ctx)
        return if (s.contains("z")) CropState(s.getFloat("z", 1f), s.getFloat("fx", 0f), s.getFloat("fy", 0f)) else null
    }
    fun clear(ctx: Context) {
        src(ctx).delete()
        sp(ctx).edit().clear().apply()
    }

    fun save(ctx: Context, dest: File, source: Bitmap, out: Bitmap, st: CropState): Boolean = try {
        val tmp = File(ctx.filesDir, "avatar.tmp")
        tmp.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        val tmpSrc = File(ctx.filesDir, "avatar_src.tmp")
        tmpSrc.outputStream().use { source.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        if (!tmp.renameTo(dest)) { dest.delete(); tmp.renameTo(dest) }
        if (!tmpSrc.renameTo(src(ctx))) { src(ctx).delete(); tmpSrc.renameTo(src(ctx)) }
        sp(ctx).edit().putFloat("z", st.z).putFloat("fx", st.fx).putFloat("fy", st.fy).apply()
        dest.exists()
    } catch (e: Exception) { false }
}

fun decodeSampled(ctx: Context, uri: Uri, maxSide: Int = 2048): Bitmap? = try {
    val cr = ctx.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) null else {
        var s = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (s * 2) >= maxSide) s *= 2
        val bmp = cr.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s })
        }
        val orient = cr.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
        val deg = when (orient) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (bmp == null) null
        else if (deg == 0f) bmp
        else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(deg) }, true)
    }
} catch (e: Throwable) { null }

@Composable
fun AvatarEditor(
    bmp: Bitmap,
    init: CropState?,
    onCancel: () -> Unit,
    onSave: (Bitmap, CropState) -> Unit
) {
    val u = LocalUi.current
    val density = LocalDensity.current
    var z by remember { mutableFloatStateOf(init?.z ?: 1f) }

    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF0C0B12))
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            val boxDp = minOf(maxWidth - 48.dp, maxHeight - 260.dp, 360.dp).coerceAtLeast(180.dp)
            val boxPx = with(density) { boxDp.toPx() }
            val base = maxOf(boxPx / bmp.width, boxPx / bmp.height)
            val wPx = bmp.width * base
            val hPx = bmp.height * base

            var ox by remember(boxPx) { mutableFloatStateOf((init?.fx ?: 0f) * boxPx) }
            var oy by remember(boxPx) { mutableFloatStateOf((init?.fy ?: 0f) * boxPx) }

            fun fit() {
                val mx = maxOf(0f, (wPx * z - boxPx) / 2f)
                val my = maxOf(0f, (hPx * z - boxPx) / 2f)
                ox = ox.coerceIn(-mx, mx)
                oy = oy.coerceIn(-my, my)
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Crop Profile Photo", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(Modifier.height(4.dp))
                    Text("Drag to reposition, pinch or slider to zoom", fontSize = 12.sp, color = Color.White.copy(0.7f))
                }

                Box(
                    Modifier
                        .size(boxDp)
                        .clipToBounds()
                        .background(Color.Black)
                        .pointerInput(boxPx) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                z = (z * zoom).coerceIn(1f, 5f)
                                ox += pan.x
                                oy += pan.y
                                fit()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bmp.asImageBitmap(),
                        contentDescription = "Profile photo preview",
                        modifier = Modifier
                            .requiredSize(with(density) { wPx.toDp() }, with(density) { hPx.toDp() })
                            .graphicsLayer {
                                scaleX = z
                                scaleY = z
                                translationX = ox
                                translationY = oy
                            },
                        contentScale = ContentScale.FillBounds
                    )

                    Canvas(Modifier.fillMaxSize()) {
                        val r = size.minDimension / 2f
                        val mask = Path().apply {
                            addRect(Rect(Offset.Zero, size))
                            addOval(Rect(center - Offset(r, r), Size(r * 2, r * 2)))
                            fillType = PathFillType.EvenOdd
                        }
                        drawPath(mask, Color.Black.copy(alpha = 0.65f))
                        drawCircle(u.accent, r - 1.dp.toPx(), center, style = Stroke(2.5.dp.toPx()))
                    }
                }

                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("1x", fontSize = 12.sp, color = Color.White.copy(0.7f))
                        Slider(
                            value = z,
                            onValueChange = { z = it; fit() },
                            valueRange = 1f..5f,
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                            colors = SliderDefaults.colors(thumbColor = u.accent, activeTrackColor = u.accent)
                        )
                        Text("5x", fontSize = 12.sp, color = Color.White.copy(0.7f))
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onCancel,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Cancel", color = Color.White)
                        }

                        OutlinedButton(
                            onClick = { z = 1f; ox = 0f; oy = 0f },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Reset", color = Color.White)
                        }

                        Spacer(Modifier.weight(1f))

                        Button(
                            onClick = {
                                val out = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
                                val f = 512f / boxPx
                                val m = Matrix().apply {
                                    postTranslate(-bmp.width / 2f, -bmp.height / 2f)
                                    postScale(base * z * f, base * z * f)
                                    postTranslate(256f + ox * f, 256f + oy * f)
                                }
                                android.graphics.Canvas(out).drawBitmap(bmp, m, Paint(Paint.FILTER_BITMAP_FLAG))
                                onSave(out, CropState(z, ox / boxPx, oy / boxPx))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = u.accent, contentColor = u.onAccent),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Save", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
