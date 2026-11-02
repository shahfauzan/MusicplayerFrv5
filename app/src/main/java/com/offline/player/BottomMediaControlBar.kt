package com.offline.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * A persistent bottom media control bar component that interfaces directly with
 * ExoPlayer (via PlayerState & MediaController) to handle play, pause, and seek operations,
 * track transitions, and real-time playback progress scrubbing.
 */
@Composable
fun BottomMediaControlBar(
    app: AppState,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit
) {
    val ps = app.ps
    val u = LocalUi.current
    val item = ps.current ?: return
    val title = item.mediaMetadata.title?.toString()?.ifBlank { "FR Music Player" } ?: "FR Music Player"
    val artist = item.mediaMetadata.artist?.toString()?.ifBlank { "Unknown Artist" } ?: "Unknown Artist"
    val isOnline = PlaybackService.isOnlineActive

    val songId = item.mediaId.toLongOrNull()
    val isFav = songId?.let { app.prefs.favs.contains(it) } ?: false

    // ── Scrubbing / Seeking State ──
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubPosMs by remember { mutableLongStateOf(0L) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }

    // Live display position: uses scrubPosMs while user drags, else ExoPlayer ps.pos
    val displayPos = if (isScrubbing) scrubPosMs else ps.pos
    val duration = ps.dur.coerceAtLeast(0L)
    val progress = if (duration > 0L) {
        (displayPos.toFloat() / duration).coerceIn(0f, 1f)
    } else 0f

    // Subtle vinyl rotation effect when playing
    val rotationAnim = remember { Animatable(0f) }
    LaunchedEffect(ps.playing) {
        if (ps.playing) {
            rotationAnim.animateTo(
                targetValue = rotationAnim.value + 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(12000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                )
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .shadow(12.dp, RoundedCornerShape(20.dp), spotColor = u.accent.copy(alpha = 0.25f))
            .glass(20.dp)
            .border(1.dp, u.accent.copy(alpha = 0.22f), RoundedCornerShape(20.dp))
            .testTag("bottom_media_control_bar")
    ) {
        // ── Top Interactive Seek Bar ──
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
                .testTag("bottom_media_seek_bar")
                .pointerInput(duration) {
                    if (duration <= 0L) return@pointerInput
                    detectTapGestures { offset ->
                        val ratio = (offset.x / size.width).coerceIn(0f, 1f)
                        val targetMs = (ratio * duration).toLong()
                        ps.controller?.seekTo(targetMs)
                    }
                }
                .pointerInput(duration) {
                    if (duration <= 0L) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            isScrubbing = true
                            val ratio = (offset.x / size.width).coerceIn(0f, 1f)
                            scrubFraction = ratio
                            scrubPosMs = (ratio * duration).toLong()
                        },
                        onDragEnd = {
                            isScrubbing = false
                            ps.controller?.seekTo(scrubPosMs)
                        },
                        onDragCancel = {
                            isScrubbing = false
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            val ratio = (change.position.x / size.width).coerceIn(0f, 1f)
                            scrubFraction = ratio
                            scrubPosMs = (ratio * duration).toLong()
                        }
                    )
                },
            contentAlignment = Alignment.CenterStart
        ) {
            val trackWidth = maxWidth
            val activeFraction = if (isScrubbing) scrubFraction else progress

            // Background track
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(if (isScrubbing) 6.dp else 3.5.dp)
                    .clip(CircleShape)
                    .background(u.dim.copy(alpha = 0.22f))
            )

            // Active played progress track
            Box(
                Modifier
                    .width(trackWidth * activeFraction)
                    .height(if (isScrubbing) 6.dp else 3.5.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.horizontalGradient(
                            listOf(u.accent.copy(alpha = 0.85f), u.accent)
                        )
                    )
            )

            // Interactive scrubber thumb
            Box(
                Modifier
                    .offset {
                        IntOffset(
                            x = ((trackWidth * activeFraction).toPx() - 6.dp.toPx()).roundToInt(),
                            y = 0
                        )
                    }
                    .size(if (isScrubbing) 14.dp else 9.dp)
                    .clip(CircleShape)
                    .background(u.onAccent)
                    .border(2.dp, u.accent, CircleShape)
            )
        }

        // ── Main Content & Controls Row ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = "Open Now Playing",
                    onClick = onOpen
                )
                .padding(start = 10.dp, end = 6.dp, top = 2.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Artwork with Vinyl / Beat rotation badge
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Art(
                    model = item.mediaMetadata.artworkUri,
                    seed = title,
                    size = 46.dp,
                    radius = 12.dp
                )
                if (ps.playing && !isOnline) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(u.accent.copy(alpha = 0.8f))
                            .border(1.dp, Color.White, CircleShape)
                    )
                }
            }

            Spacer(Modifier.width(10.dp))

            // Track metadata & live seek timestamp
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = u.text,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isOnline) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(u.accent.copy(alpha = 0.2f))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                "ONLINE",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = u.accentText
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = artist,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 12.sp,
                        color = u.dim,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (duration > 0L) {
                        Text(
                            text = "${fmt(displayPos)} / ${fmt(duration)}",
                            fontSize = 11.sp,
                            fontWeight = if (isScrubbing) FontWeight.Bold else FontWeight.Normal,
                            color = if (isScrubbing) u.accentText else u.dim.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            Spacer(Modifier.width(4.dp))

            // ── Interactive Media Controls ──
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Favorite Button (for local tracks)
                if (songId != null && !isOnline) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { app.prefs.toggleFav(songId) }
                            )
                            .testTag("bottom_media_fav_button")
                            .semantics {
                                contentDescription = if (isFav) "Remove from favorites" else "Add to favorites"
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Ico(
                            path = if (isFav) P.Heart else P.HeartOutline,
                            tint = if (isFav) Color(0xFFFF4757) else u.dim,
                            sz = 20.dp
                        )
                    }
                }

                // Previous Track / Rewind 10s button
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                if (isOnline) {
                                    ps.controller?.let { c ->
                                        c.seekTo((c.currentPosition - 10000L).coerceAtLeast(0L))
                                    }
                                } else {
                                    ps.controller?.seekToPrevious()
                                }
                            }
                        )
                        .testTag("bottom_media_prev_button")
                        .semantics { contentDescription = "Previous song" },
                    contentAlignment = Alignment.Center
                ) {
                    Ico(P.Prev, tint = u.text, sz = 22.dp)
                }

                // Primary Play / Pause Button (Prominent pill/circle with ripple & >=48dp touch target)
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(u.accent)
                        .clickable(
                            onClickLabel = if (ps.playing) "Pause" else "Play",
                            onClick = { ps.toggle() }
                        )
                        .testTag("bottom_media_play_pause_button")
                        .semantics { contentDescription = if (ps.playing) "Pause" else "Play" },
                    contentAlignment = Alignment.Center
                ) {
                    Ico(
                        path = if (ps.playing) P.Pause else P.Play,
                        tint = u.onAccent,
                        sz = 26.dp
                    )
                }

                // Next Track / Fast-Forward 10s button
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                if (isOnline) {
                                    ps.controller?.let { c ->
                                        c.seekTo(c.currentPosition + 10000L)
                                    }
                                } else {
                                    ps.controller?.seekToNext()
                                }
                            }
                        )
                        .testTag("bottom_media_next_button")
                        .semantics { contentDescription = "Next song" },
                    contentAlignment = Alignment.Center
                ) {
                    Ico(P.Next, tint = u.text, sz = 22.dp)
                }
            }
        }
    }
}
