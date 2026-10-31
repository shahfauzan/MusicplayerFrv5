package com.offline.player

import android.content.Context
import android.media.audiofx.Visualizer
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Modes for Beat-synchronized Screen Shake & Bass Pulse:
 * 0: Off - No motion or pulse
 * 1: Subtle - Gentle album art bass bounce (no screen shake)
 * 2: Dynamic - Recommended! Screen micro-shake & punchy bass drop
 * 3: Party / Wild - High energy screen shake & intense bass surge
 */
data class BeatShakeModeInfo(
    val id: Int,
    val name: String,
    val emoji: String,
    val desc: String
)

val BeatShakeModes = listOf(
    BeatShakeModeInfo(0, "Off", "🛑", "No screen shake or bass pulse"),
    BeatShakeModeInfo(1, "Subtle", "🍃", "Gentle cover art bass pulse only"),
    BeatShakeModeInfo(2, "Dynamic", "⚡", "Punchy bass drop & screen micro-shake"),
    BeatShakeModeInfo(3, "Party Mode", "🔥", "Maximum beat shake & neon surge")
)

/**
 * State holding instantaneous transform values for screen shake and art pulse
 */
@Stable
data class BeatShakeState(
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val rotation: Float = 0f,
    val scale: Float = 1f,
    val glowAlpha: Float = 0f,
    val energy: Float = 0f
)

/**
 * Audio Beat Sync hook:
 * Analyzes audio energy from active playback session via Visualizer (with organic rhythmic
 * fallback when hardware visualizer is not accessible) and translates into animated
 * 2D translation offset, rotation, scale, and bass glow.
 */
@Composable
fun rememberBeatShake(
    playing: Boolean,
    mode: Int, // 0..3
    hapticsEnabled: Boolean = false
): BeatShakeState {
    if (mode == 0 || !playing) {
        return BeatShakeState()
    }

    val density = LocalDensity.current
    val view = LocalView.current
    val ctx = LocalContext.current
    val sessionId = PlaybackService.activeSessionId

    // Raw energy input: 0.0f .. 1.0f
    var rawEnergy by remember { mutableFloatStateOf(0f) }
    var isKickDrop by remember { mutableStateOf(false) }

    // Hardware Audio Visualizer listener for real bass energy
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
                            private var lastEnergy = 0f
                            override fun onWaveFormDataCapture(v: Visualizer, data: ByteArray, rate: Int) {
                                if (data.isEmpty()) return
                                var sumSq = 0.0
                                for (b in data) {
                                    val d = abs(b.toInt() - 128)
                                    sumSq += (d * d)
                                }
                                val rms = (sqrt(sumSq / data.size) / 128.0).toFloat().coerceIn(0f, 1f)
                                val smoothed = lastEnergy * 0.4f + rms * 0.6f
                                lastEnergy = smoothed
                                rawEnergy = (smoothed * 1.6f).coerceIn(0f, 1f)
                                isKickDrop = (smoothed > 0.42f && smoothed > lastEnergy)
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

    // Dynamic animation phase for rhythm and directional shake
    val beatPhase = remember { Animatable(0f) }
    val animatedEnergy = remember { Animatable(0f) }

    LaunchedEffect(playing) {
        if (playing) {
            beatPhase.animateTo(
                targetValue = (2000 * PI).toFloat(),
                animationSpec = infiniteRepeatable(
                    animation = tween(2_000_000, easing = LinearEasing)
                )
            )
        } else {
            beatPhase.snapTo(0f)
        }
    }

    // Smooth physics spring for energy response
    LaunchedEffect(rawEnergy, playing) {
        if (playing) {
            // If raw energy is low or visualizer unavailable, generate synthetic 126 BPM rhythm
            val effectiveEnergy = if (rawEnergy > 0.05f) {
                rawEnergy
            } else {
                val t = beatPhase.value
                val b1 = abs(sin(t * 4.2f)) // 126 BPM beat pulse
                val b2 = abs(cos(t * 2.1f))
                (b1 * 0.7f + b2 * 0.3f).coerceIn(0f, 1f)
            }
            animatedEnergy.animateTo(
                targetValue = effectiveEnergy,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        } else {
            animatedEnergy.snapTo(0f)
        }
    }

    // Haptics trigger on strong bass hits
    LaunchedEffect(isKickDrop, hapticsEnabled) {
        if (hapticsEnabled && isKickDrop && playing) {
            try {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            } catch (e: Exception) {}
        }
    }

    val energy = animatedEnergy.value
    val phase = beatPhase.value

    return remember(energy, phase, mode, density) {
        when (mode) {
            1 -> {
                // Subtle: Artwork gentle pulse only (up to +3.5%), no screen translation
                BeatShakeState(
                    offsetX = 0f,
                    offsetY = 0f,
                    rotation = 0f,
                    scale = 1f + (energy * 0.035f),
                    glowAlpha = energy * 0.35f,
                    energy = energy
                )
            }
            2 -> {
                // Dynamic: Punchy screen micro-shake (±3 to 5dp) + art bounce (up to +6.5%)
                val maxOffsetPx = with(density) { 5.dp.toPx() }
                val sx = sin(phase * 9.3f) * maxOffsetPx * energy
                val sy = cos(phase * 7.1f) * (maxOffsetPx * 0.75f) * energy
                val rot = sin(phase * 4.5f) * (0.45f * energy)
                BeatShakeState(
                    offsetX = sx,
                    offsetY = sy,
                    rotation = rot,
                    scale = 1f + (energy * 0.065f),
                    glowAlpha = energy * 0.65f,
                    energy = energy
                )
            }
            3 -> {
                // Wild / Party: Intense beat shake (±8 to 11dp) + fast rotational jitter
                val maxOffsetPx = with(density) { 9.dp.toPx() }
                val sx = sin(phase * 13.7f) * maxOffsetPx * energy
                val sy = cos(phase * 11.2f) * maxOffsetPx * energy
                val rot = sin(phase * 8.0f) * (0.95f * energy)
                BeatShakeState(
                    offsetX = sx,
                    offsetY = sy,
                    rotation = rot,
                    scale = 1f + (energy * 0.10f),
                    glowAlpha = (energy * 0.95f).coerceIn(0f, 1f),
                    energy = energy
                )
            }
            else -> BeatShakeState()
        }
    }
}

/**
 * Interactive Beat Sync Dialog / Sheet for quick mode adjustment and haptic toggle
 */
@Composable
fun BeatSyncSheet(
    app: AppState,
    onDismiss: () -> Unit
) {
    val u = LocalUi.current
    val prefs = app.prefs

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = if (u.light) Color.White else Color(0xFF1E1E2A),
        titleContentColor = u.text,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⚡ Beat Sync & Screen Shake", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = u.text)
            }
        },
        text = {
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Syncs screen micro-motion and album cover bounce with the music's bass drops and beat peaks.",
                    fontSize = 13.sp,
                    color = u.dim
                )

                // Mode selection cards
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BeatShakeModes.forEach { mode ->
                        val sel = prefs.beatShakeMode == mode.id
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .then(
                                    if (sel) Modifier.background(u.accent.copy(alpha = 0.22f)).border(1.5.dp, u.accent, RoundedCornerShape(14.dp))
                                    else Modifier.background(u.glassLo)
                                )
                                .themeClickable { prefs.setBeatShake(mode.id) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(mode.emoji, fontSize = 22.sp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(mode.name, fontWeight = FontWeight.Bold, color = u.text)
                                Text(mode.desc, fontSize = 11.sp, color = u.dim)
                            }
                            RadioButton(
                                selected = sel,
                                onClick = { prefs.setBeatShake(mode.id) },
                                colors = RadioButtonDefaults.colors(selectedColor = u.accent)
                            )
                        }
                    }
                }

                HorizontalDivider(color = u.dim.copy(alpha = 0.2f), thickness = 1.dp)

                // Haptic feedback toggle
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Beat Haptics", fontWeight = FontWeight.SemiBold, color = u.text, fontSize = 14.sp)
                        Text("Subtle tactile vibration on heavy kicks and drops.", fontSize = 11.sp, color = u.dim)
                    }
                    Switch(
                        checked = prefs.beatShakeHaptics,
                        onCheckedChange = { prefs.setBeatHaptics(it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = u.accent, checkedThumbColor = u.onAccent)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = u.accentText, fontWeight = FontWeight.Bold)
            }
        }
    )
}
