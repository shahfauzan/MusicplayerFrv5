package com.offline.player

import android.media.audiofx.Visualizer
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

@Composable
fun LiveVisualizer(
    playing: Boolean,
    accent: Color,
    height: Dp = 48.dp,
    barCount: Int = 36,
    modifier: Modifier = Modifier
) {
    val sessionId = PlaybackService.activeSessionId
    val rawBytes = remember { mutableStateOf(ByteArray(barCount)) }

    DisposableEffect(sessionId, playing) {
        var visualizer: Visualizer? = null
        if (playing && sessionId > 0) {
            try {
                val range = Visualizer.getCaptureSizeRange()
                val cap = if (range != null && range.isNotEmpty()) range[0].coerceAtLeast(64) else 64
                visualizer = Visualizer(sessionId).apply {
                    captureSize = cap
                    setDataCaptureListener(
                        object : Visualizer.OnDataCaptureListener {
                            override fun onWaveFormDataCapture(v: Visualizer, data: ByteArray, samplingRate: Int) {
                                val b = ByteArray(barCount)
                                val step = (data.size / barCount).coerceAtLeast(1)
                                for (i in 0 until barCount) {
                                    val idx = (i * step).coerceIn(0, data.lastIndex)
                                    b[i] = data[idx]
                                }
                                rawBytes.value = b
                            }
                            override fun onFftDataCapture(v: Visualizer, fft: ByteArray, rate: Int) {}
                        },
                        Visualizer.getMaxCaptureRate() / 2, true, false
                    )
                    enabled = true
                }
            } catch (e: Exception) {
                visualizer = null
            }
        }
        onDispose {
            try {
                visualizer?.enabled = false
                visualizer?.release()
            } catch (e: Exception) {}
        }
    }

    val phase = remember { Animatable(0f) }
    LaunchedEffect(playing) {
        if (playing) {
            phase.animateTo(
                targetValue = (2 * PI).toFloat(),
                animationSpec = infiniteRepeatable(
                    animation = tween(2200, easing = LinearEasing)
                )
            )
        } else {
            phase.snapTo(0f)
        }
    }

    val currentBytes = rawBytes.value
    Canvas(modifier.fillMaxWidth().height(height)) {
        val w = size.width
        val h = size.height
        val bw = (w / barCount) * 0.65f
        val gap = w / barCount
        val p = phase.value

        for (i in 0 until barCount) {
            val cx = i * gap + (gap - bw) / 2f
            val baseMag = if (playing) {
                if (currentBytes.isNotEmpty() && currentBytes[0].toInt() != 0) {
                    val raw = abs(currentBytes[i.coerceIn(0, currentBytes.lastIndex)].toInt() - 128)
                    (raw / 128f).coerceIn(0.12f, 1f)
                } else {
                    val wave1 = sin(i.toFloat() * 0.35f + p)
                    val wave2 = sin(i.toFloat() * 0.7f - p * 0.5f)
                    val combined = (abs(wave1 * 0.6f + wave2 * 0.4f)).toFloat()
                    (0.15f + 0.85f * combined).coerceIn(0.12f, 1f)
                }
            } else {
                0.08f
            }

            val barH = (h * baseMag).coerceAtLeast(3.dp.toPx())
            val topY = (h - barH) / 2f

            drawRoundRect(
                brush = Brush.verticalGradient(
                    listOf(
                        accent,
                        accent.copy(alpha = 0.45f)
                    )
                ),
                topLeft = Offset(cx, topY),
                size = Size(bw, barH),
                cornerRadius = CornerRadius(bw / 2f)
            )
        }
    }
}
