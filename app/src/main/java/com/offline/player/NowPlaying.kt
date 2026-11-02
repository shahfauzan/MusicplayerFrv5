package com.offline.player

import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import kotlin.math.PI
import kotlin.math.sin

fun waveHeights(seed: Long, n: Int): FloatArray {
    var s = seed xor 0x2545F4914F6CDD1DL
    val out = FloatArray(n)
    for (i in 0 until n) {
        s = s * 6364136223846793005L + 1442695040888963407L
        val r = ((s ushr 40) and 0xFFFF).toInt() / 65535f
        val env = 0.35f + 0.65f * sin(PI * (i + 0.5) / n).toFloat()
        out[i] = ((0.18f + 0.82f * r) * env).coerceIn(0.08f, 1f)
    }
    return out
}

@Composable
fun WaveSeek(
    seed: Long, frac: Float, enabled: Boolean, played: Color, idle: Color, state: String,
    onSeek: (Float) -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier
) {
    val bars = remember(seed) { waveHeights(seed, 64) }
    val seek by rememberUpdatedState(onSeek)
    val done by rememberUpdatedState(onDone)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .semantics {
                contentDescription = "Seek bar"
                stateDescription = state
                progressBarRangeInfo = ProgressBarRangeInfo(frac, 0f..1f)
                if (enabled) setProgress { v -> seek(v.coerceIn(0f, 1f)); done(); true }
            }
            .pointerInput(enabled) {
                if (enabled) detectTapGestures { o -> seek((o.x / size.width).coerceIn(0f, 1f)); done() }
            }
            .pointerInput(enabled) {
                if (enabled) detectHorizontalDragGestures(
                    onDragStart = { o -> seek((o.x / size.width).coerceIn(0f, 1f)) },
                    onDragEnd = { done() },
                    onDragCancel = { done() }
                ) { change, _ -> change.consume(); seek((change.position.x / size.width).coerceIn(0f, 1f)) }
            }
    ) {
        val n = bars.size
        val bw = (this.size.width / n) * 0.55f
        for (i in 0 until n) {
            val x = this.size.width * (i + 0.5f) / n
            val h = this.size.height * bars[i]
            drawRoundRect(
                color = if ((i + 0.5f) / n <= frac) played else idle,
                topLeft = Offset(x - bw / 2, (this.size.height - h) / 2),
                size = Size(bw, h),
                cornerRadius = CornerRadius(bw / 2)
            )
        }
    }
}

@Composable
fun RotatingHaloArt(
    model: Any?,
    seed: String,
    artSize: androidx.compose.ui.unit.Dp,
    playing: Boolean,
    motion: Int,
    scale: Float,
    beatGlow: Float = 0f
) {
    val u = LocalUi.current
    var angle by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playing, motion) {
        if (playing && motion == 0) {
            var last = withFrameNanos { it }
            while (true) {
                val t = withFrameNanos { it }
                angle = (angle + (t - last) / 1_000_000_000f * 14f) % 360f
                last = t
            }
        }
    }
    val ring = u.accent
    Box(
        Modifier
            .size(artSize + 44.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = artSize.toPx() / 2f + 9.dp.toPx()
            val extraRadius = r * (1.32f + beatGlow * 0.35f)
            val glowAlpha = (0.38f + beatGlow * 0.45f).coerceIn(0f, 0.95f)
            drawCircle(
                androidx.compose.ui.graphics.Brush.radialGradient(
                    listOf(ring.copy(alpha = glowAlpha), Color.Transparent),
                    center = center,
                    radius = extraRadius
                ),
                radius = extraRadius,
                center = center
            )
            drawCircle(
                ring.copy(alpha = (0.30f + beatGlow * 0.4f).coerceIn(0f, 0.95f)),
                radius = r + (beatGlow * 5.dp.toPx()),
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = (6.dp.toPx() + beatGlow * 4.dp.toPx()))
            )
            drawCircle(
                ring.copy(alpha = 0.95f),
                radius = r,
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.6.dp.toPx())
            )
        }
        Box(Modifier.graphicsLayer { rotationZ = angle }) {
            Art(model, seed, artSize, 28.dp, shape = CircleShape)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NowPlaying(
    app: AppState,
    sleepLabel: String,
    onSleep: () -> Unit,
    onQueue: () -> Unit,
    onEqualizer: () -> Unit,
    onLyrics: () -> Unit,
    onTagEdit: (Song) -> Unit,
    onClose: () -> Unit
) {
    val ps = app.ps
    val u = LocalUi.current
    val m = LocalMotion.current
    val ctx = LocalContext.current
    val item = ps.current ?: return
    val songId = item.mediaId.toLongOrNull()
    val fav = songId != null && songId in app.prefs.favs
    val fadeMs = when (m) { 0 -> 400; 1 -> 150; else -> 0 }
    val song = songId?.let { app.byId[it] }

    var showMenu by remember { mutableStateOf(false) }
    var showBeatSyncSheet by remember { mutableStateOf(false) }

    val beatShake = rememberBeatShake(
        playing = ps.playing,
        mode = app.prefs.beatShakeMode,
        hapticsEnabled = app.prefs.beatShakeHaptics
    )

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        val artSize = minOf(maxWidth - 64.dp, maxHeight * 0.35f, 340.dp).coerceAtLeast(150.dp)
        val scale by animateFloatAsState(if (ps.playing || m != 0) 1f else 0.94f, tween(400), label = "artScale")
        var drag by remember { mutableStateOf<Float?>(null) }

        Column(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxHeight()
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    translationX = beatShake.offsetX
                    translationY = beatShake.offsetY
                    rotationZ = beatShake.rotation
                }
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header bar
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                GlassCircleBtn(P.Down, "Close Now Playing", onClose)
                Text("Now Playing", fontSize = 16.sp, color = u.text, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassCircleBtn(
                        if (fav) P.Heart else P.HeartOutline,
                        if (fav) "Remove from favorites" else "Add to favorites",
                        { songId?.let { app.prefs.toggleFav(it) } },
                        tint = if (fav) u.accent else u.text
                    )
                    Box {
                        GlassCircleBtn(P.More, "Song options", { showMenu = true })
                        DropdownMenu(showMenu, { showMenu = false }) {
                            DropdownMenuItem(text = { Text("Equalizer") }, onClick = { showMenu = false; onEqualizer() })
                            DropdownMenuItem(text = { Text("⚡ Beat Shake Settings") }, onClick = { showMenu = false; showBeatSyncSheet = true })
                            DropdownMenuItem(text = { Text("Lyrics") }, onClick = { showMenu = false; onLyrics() })
                            if (song != null) {
                                DropdownMenuItem(text = { Text("Edit info (Tags)") }, onClick = { showMenu = false; onTagEdit(song) })
                                DropdownMenuItem(text = { Text(if (app.vault.isHidden(song.id)) "Unhide from Vault" else "Hide in Private Vault") }, onClick = {
                                    showMenu = false
                                    app.vault.toggleHide(song.id)
                                    app.refreshHiddenState()
                                    Toast.makeText(ctx, if (app.vault.isHidden(song.id)) "Moved to Private Vault" else "Restored to Library", Toast.LENGTH_SHORT).show()
                                })
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Cover Art with dynamic bass thump scale and glow
            val effectiveScale = scale * beatShake.scale
            Crossfade(item, animationSpec = tween(fadeMs), label = "npArt") { cur ->
                if (LocalArtShape.current == 2) {
                    RotatingHaloArt(
                        cur.mediaMetadata.artworkUri,
                        cur.mediaMetadata.title?.toString() ?: "",
                        artSize,
                        ps.playing,
                        m,
                        effectiveScale,
                        beatShake.glowAlpha
                    )
                } else {
                    val shape = artShape(24.dp)
                    Box(
                        Modifier
                            .graphicsLayer { scaleX = effectiveScale; scaleY = effectiveScale }
                            .shadow(24.dp + (14.dp * beatShake.glowAlpha), shape, ambientColor = u.accent, spotColor = u.accent)
                    ) {
                        Art(cur.mediaMetadata.artworkUri, cur.mediaMetadata.title?.toString() ?: "", artSize, 24.dp, shape = shape)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Audio Visualizer if enabled
            if (app.prefs.visualizerOn) {
                LiveVisualizer(
                    playing = ps.playing,
                    accent = u.accent,
                    height = 36.dp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(Modifier.height(10.dp))
            }

            // Title & Artist with Marquee
            Crossfade(item, animationSpec = tween(fadeMs), label = "npText") { cur ->
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        cur.mediaMetadata.title?.toString() ?: "Unknown Title",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        color = u.text,
                        overflow = if (m == 0) TextOverflow.Clip else TextOverflow.Ellipsis,
                        modifier = if (m == 0) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        listOfNotNull(
                            cur.mediaMetadata.artist?.toString()?.takeIf { it.isNotBlank() },
                            cur.mediaMetadata.albumTitle?.toString()?.takeIf { it.isNotBlank() }
                        ).joinToString(" · "),
                        fontSize = 14.sp,
                        color = u.dim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Seek Bar
            val frac = drag ?: if (ps.dur > 0) (ps.pos.toFloat() / ps.dur).coerceIn(0f, 1f) else 0f
            val shownPos = if (drag != null) (frac * ps.dur).toLong() else ps.pos
            val finish = {
                drag?.let { ps.controller?.seekTo((it * ps.dur).toLong()) }
                drag = null
            }

            if (app.prefs.seekStyle == 0) {
                WaveSeek(
                    songId ?: 0L, frac, ps.dur > 0, u.accent, u.dim.copy(alpha = 0.35f),
                    "${fmt(shownPos)} of ${fmt(ps.dur)}",
                    { drag = it }, { finish() }
                )
            } else {
                Slider(
                    value = frac,
                    onValueChange = { drag = it },
                    onValueChangeFinished = { finish() },
                    enabled = ps.dur > 0,
                    modifier = Modifier.semantics {
                        contentDescription = "Seek bar"
                        stateDescription = "${fmt(shownPos)} of ${fmt(ps.dur)}"
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = u.accent,
                        activeTrackColor = u.accent,
                        inactiveTrackColor = u.dim.copy(alpha = 0.3f)
                    )
                )
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(fmt(shownPos), fontSize = 12.sp, color = u.dim)
                Text(fmt(ps.dur), fontSize = 12.sp, color = u.dim)
            }

            Spacer(Modifier.height(14.dp))

            // Main playback controls
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconBtn(
                    P.Shuffle,
                    if (ps.shuffle) "Shuffle enabled" else "Shuffle disabled",
                    { ps.controller?.let { it.shuffleModeEnabled = !ps.shuffle } },
                    tint = if (ps.shuffle) u.accentText else u.dim
                )

                IconBtn(P.Prev, "Previous song", { ps.controller?.seekToPrevious() }, sz = 32.dp)

                Box(
                    Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(u.accent)
                        .semantics { contentDescription = if (ps.playing) "Pause" else "Play" }
                        .themeClickable(role = Role.Button) { ps.toggle() },
                    contentAlignment = Alignment.Center
                ) {
                    Ico(if (ps.playing) P.Pause else P.Play, u.onAccent, 34.dp)
                }

                IconBtn(P.Next, "Next song", { ps.controller?.seekToNext() }, sz = 32.dp)

                val repeatDesc = when (ps.repeat) {
                    Player.REPEAT_MODE_ALL -> "Repeat all"
                    Player.REPEAT_MODE_ONE -> "Repeat one"
                    else -> "Repeat off"
                }
                IconBtn(
                    if (ps.repeat == Player.REPEAT_MODE_ONE) P.RepeatOne else P.Repeat,
                    repeatDesc,
                    {
                        ps.controller?.let {
                            it.repeatMode = when (ps.repeat) {
                                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                else -> Player.REPEAT_MODE_OFF
                            }
                        }
                    },
                    tint = if (ps.repeat != Player.REPEAT_MODE_OFF) u.accentText else u.dim
                )
            }

            Spacer(Modifier.height(14.dp))

            // Action Quick Chips (Queue, Equalizer, Beat Shake, Lyrics, Sleep, Speed)
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Chip("Queue (${ps.queue.size})", onClick = onQueue)
                Chip("Equalizer", onClick = onEqualizer)
                val shakeChipLabel = when (app.prefs.beatShakeMode) {
                    0 -> "Shake: Off"
                    1 -> "🍃 Shake: Subtle"
                    2 -> "⚡ Shake: Dynamic"
                    else -> "🔥 Shake: Party"
                }
                Chip(shakeChipLabel, onClick = { showBeatSyncSheet = true })
                Chip("Lyrics", onClick = onLyrics)
                Chip(sleepLabel, onClick = onSleep)
                Chip("${speeds[app.prefs.speed]}x") {
                    app.prefs.setSpeed((app.prefs.speed + 1) % speeds.size)
                }
            }

            Spacer(Modifier.height(18.dp))
        }

        if (showBeatSyncSheet) {
            BeatSyncSheet(app = app, onDismiss = { showBeatSyncSheet = false })
        }
    }
}

@Composable
fun MiniPlayer(app: AppState, onOpen: () -> Unit) {
    BottomMediaControlBar(app = app, onOpen = onOpen)
}

@Composable
fun QueueScreen(app: AppState, onBack: () -> Unit) {
    val ps = app.ps
    val u = LocalUi.current
    var confirmClear by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBtn(P.Back, "Back to player", onBack)
            Column(Modifier.weight(1f)) {
                Text("Playback Queue", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = u.text)
                Text("${ps.queue.size} songs scheduled", fontSize = 12.sp, color = u.dim)
            }
            if (ps.queue.size > 1) {
                Chip("Clear") { confirmClear = true }
            }
            Spacer(Modifier.width(8.dp))
        }

        if (ps.queue.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Queue is empty. Select songs or use 'Play next'.", color = u.dim, fontSize = 14.sp)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(ps.queue) { i, mi ->
                    val isCur = i == ps.index
                    val title = mi.mediaMetadata.title?.toString() ?: ""
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 2.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .then(if (isCur) Modifier.background(u.accent.copy(alpha = 0.18f)) else Modifier)
                            .themeClickable(onClickLabel = "Play $title") { ps.jumpTo(i) }
                            .padding(start = 8.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Art(mi.mediaMetadata.artworkUri, title, 44.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isCur) u.accentText else u.text
                            )
                            Text(mi.mediaMetadata.artist?.toString() ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = u.dim)
                        }
                        var open by remember { mutableStateOf(false) }
                        Box {
                            IconBtn(P.More, "Options for $title", { open = true })
                            DropdownMenu(open, { open = false }) {
                                if (i != ps.index && i != ps.index + 1) {
                                    DropdownMenuItem(text = { Text("Play next") }, onClick = {
                                        open = false
                                        val target = if (i > ps.index) ps.index + 1 else ps.index
                                        ps.move(i, target)
                                    })
                                }
                                if (i > 0) DropdownMenuItem(text = { Text("Move up") }, onClick = { open = false; ps.move(i, i - 1) })
                                if (i < ps.queue.lastIndex) DropdownMenuItem(text = { Text("Move down") }, onClick = { open = false; ps.move(i, i + 1) })
                                DropdownMenuItem(text = { Text("Remove from queue") }, onClick = { open = false; ps.removeAt(i) })
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            "Clear upcoming queue?",
            "All previous and upcoming tracks will be cleared. The currently playing song will keep playing.",
            "Clear Queue",
            { confirmClear = false; ps.clearUpcoming() },
            { confirmClear = false }
        )
    }
}
