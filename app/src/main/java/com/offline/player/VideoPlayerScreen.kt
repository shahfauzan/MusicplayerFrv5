package com.offline.player

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

@Composable
fun VideoPlayerScreen(
    app: AppState,
    video: VideoItem,
    onClose: () -> Unit,
    onEqualizer: () -> Unit
) {
    val ctx = LocalContext.current
    val ps = app.ps
    val u = LocalUi.current
    val controller = ps.controller

    var controlsVisible by remember { mutableStateOf(true) }
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var isLandscape by remember { mutableStateOf(false) }

    // Auto-hide controls after 4 seconds of inactivity
    LaunchedEffect(controlsVisible, ps.playing) {
        if (controlsVisible && ps.playing) {
            delay(4000)
            controlsVisible = false
        }
    }

    // When back is pressed: return to previous screen while keeping audio playing in background!
    BackHandler {
        onClose()
    }

    val activity = ctx as? Activity
    DisposableEffect(isLandscape) {
        activity?.requestedOrientation = if (isLandscape) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    val duration = ps.dur.coerceAtLeast(0L)
    val position = ps.pos.coerceAtLeast(0L)
    val progress = if (duration > 0L) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { controlsVisible = !controlsVisible },
                    onDoubleTap = { offset ->
                        val half = size.width / 2f
                        if (offset.x < half) {
                            controller?.seekTo((ps.pos - 10000L).coerceAtLeast(0L))
                        } else {
                            controller?.seekTo((ps.pos + 10000L).coerceAtMost(ps.dur))
                        }
                    }
                )
            }
    ) {
        // ── Native Media3 Video Surface ──
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    useController = false
                    this.player = controller
                    this.resizeMode = resizeMode
                    setBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            update = { playerView ->
                playerView.player = controller
                playerView.resizeMode = resizeMode
            },
            modifier = Modifier.fillMaxSize()
        )

        // ── Bespoke Luxury White & Gray Controls Overlay ──
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.75f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.85f)
                            )
                        )
                    )
            ) {
                // ── Top Header Bar ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Minimize / Back button
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f))
                            .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                            .clickable(onClick = onClose),
                        contentAlignment = Alignment.Center
                    ) {
                        Ico(P.ChevronLeft, Color.White, 22.dp)
                    }

                    Spacer(Modifier.width(12.dp))

                    Column(Modifier.weight(1f)) {
                        Text(
                            text = video.title,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "${video.folderName} · ${video.resolutionLabel} · ${video.formattedSize}",
                                color = Color(0xFFA0A2AE),
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                            // Background audio badge
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF38EF7D).copy(alpha = 0.2f))
                                    .border(1.dp, Color(0xFF38EF7D).copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    "BG AUDIO ON",
                                    color = Color(0xFF38EF7D),
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    // Aspect ratio mode toggle
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.15f))
                            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                            .clickable {
                                resizeMode = when (resizeMode) {
                                    AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val modeLabel = when (resizeMode) {
                            AspectRatioFrameLayout.RESIZE_MODE_FIT -> "FIT"
                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "ZOOM"
                            else -> "FILL"
                        }
                        Text(modeLabel, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(Modifier.width(8.dp))

                    // Orientation toggle
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f))
                            .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                            .clickable { isLandscape = !isLandscape },
                        contentAlignment = Alignment.Center
                    ) {
                        Ico(P.Expand, Color.White, 20.dp)
                    }
                }

                // ── Center Controls ──
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(36.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Rewind 10s
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.12f))
                            .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape)
                            .clickable { controller?.seekTo((ps.pos - 10000L).coerceAtLeast(0L)) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("-10s", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    // Main Play/Pause Button
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE0E2EC))
                            .shadow(16.dp, CircleShape)
                            .clickable { ps.toggle() },
                        contentAlignment = Alignment.Center
                    ) {
                        Ico(
                            path = if (ps.playing) P.Pause else P.Play,
                            tint = Color(0xFF141518),
                            sz = 34.dp
                        )
                    }

                    // Forward 10s
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.12f))
                            .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape)
                            .clickable { controller?.seekTo((ps.pos + 10000L).coerceAtMost(ps.dur)) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("+10s", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // ── Bottom Scrub Bar & Navigation Row ──
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    // Interactive Seek Bar
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .pointerInput(duration) {
                                if (duration <= 0L) return@pointerInput
                                detectTapGestures { offset ->
                                    val ratio = (offset.x / size.width).coerceIn(0f, 1f)
                                    controller?.seekTo((ratio * duration).toLong())
                                }
                            },
                        contentAlignment = Alignment.CenterStart
                    ) {
                        val trackW = maxWidth
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.25f))
                        )
                        Box(
                            Modifier
                                .width(trackW * progress)
                                .height(5.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE0E2EC))
                        )
                    }

                    // Timeline seconds & Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${fmt(position)} / ${fmt(duration)}",
                            color = Color(0xFFE0E2EC),
                            fontSize = 12.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontWeight = FontWeight.Medium
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Audio Equalizer shortcut
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.15f))
                                    .clickable(onClick = onEqualizer)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("EQ", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            // Prev video
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .clickable { controller?.seekToPrevious() },
                                contentAlignment = Alignment.Center
                            ) {
                                Ico(P.Prev, Color.White, 22.dp)
                            }

                            // Next video
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .clickable { controller?.seekToNext() },
                                contentAlignment = Alignment.Center
                            ) {
                                Ico(P.Next, Color.White, 22.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}
