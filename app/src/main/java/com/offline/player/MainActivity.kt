package com.offline.player

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { PlayerApp() }
    }
}

@Composable
fun PlayerApp() {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    val pls = remember { PlaylistStore(prefs.raw) }
    val history = remember { HistoryStore(ctx) }
    val lyrics = remember { LyricsStore(ctx) }
    val eq = remember { EqualizerManager(ctx) }
    val vault = remember { VaultStore(ctx) }
    val meta = remember { MetadataStore(ctx) }

    val app = remember { AppState(prefs, pls, PlayerState(), history, lyrics, eq, vault, meta) }
    val ps = app.ps
    val scope = rememberCoroutineScope()

    var granted by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(true) }
    var tab by rememberSaveable { mutableStateOf(0) } // 0 Home, 1 My Music, 2 Settings
    var homeChip by rememberSaveable { mutableStateOf(0) }
    var musicChip by rememberSaveable { mutableStateOf(0) }
    var searchOn by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }

    var showNow by rememberSaveable { mutableStateOf(false) }
    var showQueue by rememberSaveable { mutableStateOf(false) }
    var showEqualizer by rememberSaveable { mutableStateOf(false) }
    var showLyrics by rememberSaveable { mutableStateOf(false) }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var editingSong by remember { mutableStateOf<Song?>(null) }
    var detail by remember { mutableStateOf<Detail?>(null) }

    var showSleep by remember { mutableStateOf(false) }
    var sleepEnd by remember { mutableLongStateOf(0L) }
    var sleepAfterCurrentSong by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var restored by remember { mutableStateOf(false) }

    // ── Permissions & Library Loading ──
    val perms = if (Build.VERSION.SDK_INT >= 33) {
        arrayOf(
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.RECORD_AUDIO
        )
    } else {
        arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO
        )
    }

    fun hasAudioPerm() = ContextCompat.checkSelfPermission(
        ctx,
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    ) == PackageManager.PERMISSION_GRANTED

    fun reload() {
        scope.launch {
            val list = withContext(Dispatchers.IO) { loadSongs(ctx, app.meta, app.prefs.excludedFolders) }
            app.setSongsList(list)
            loaded = true
        }
    }

    val openAppSettings = {
        ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null)))
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = hasAudioPerm()
        if (granted) reload()
    }

    LaunchedEffect(Unit) {
        if (hasAudioPerm()) {
            granted = true
            reload()
        } else {
            launcher.launch(perms)
        }
    }

    // ── Lifecycle: session persistence & visibility ──
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val ob = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> {
                    visible = true
                    if (!granted && hasAudioPerm()) {
                        granted = true
                        reload()
                    }
                }
                Lifecycle.Event.ON_STOP -> {
                    visible = false
                    saveSession(ps, prefs)
                }
                else -> {}
            }
        }
        owner.lifecycle.addObserver(ob)
        onDispose { owner.lifecycle.removeObserver(ob) }
    }

    // ── Connect to PlaybackService ──
    DisposableEffect(Unit) {
        val future = MediaController.Builder(ctx, SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))).buildAsync()
        future.addListener({
            val c = try { future.get() } catch (e: Exception) { return@addListener }
            ps.controller = c
            c.addListener(object : Player.Listener {
                override fun onEvents(pl: Player, e: Player.Events) {
                    ps.sync(pl, e.contains(Player.EVENT_TIMELINE_CHANGED))
                    if (e.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) && sleepAfterCurrentSong) {
                        sleepAfterCurrentSong = false
                        pl.pause()
                    }
                    if (e.containsAny(Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_IS_PLAYING_CHANGED) && !pl.isPlaying) {
                        saveSession(ps, prefs)
                    } else if (e.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                        saveSession(ps, prefs)
                    }
                }
            })
            ps.sync(c, true)
            app.eq.bind(PlaybackService.activeSessionId)
        }, ContextCompat.getMainExecutor(ctx))
        onDispose {
            MediaController.releaseFuture(future)
            ps.controller = null
        }
    }

    // Bind Equalizer when playback session ID changes
    LaunchedEffect(ps.playing) {
        if (PlaybackService.activeSessionId > 0) {
            app.eq.bind(PlaybackService.activeSessionId)
        }
    }

    // ── Session restore ──
    LaunchedEffect(ps.controller, app.songs) {
        val c = ps.controller ?: return@LaunchedEffect
        if (restored || !loaded) return@LaunchedEffect
        restored = true
        if (c.mediaItemCount > 0) return@LaunchedEffect
        val ids = prefs.sessionIds()
        val items = ids.mapNotNull { app.byId[it] }
        if (items.isEmpty()) return@LaunchedEffect
        val curId = ids.getOrNull(prefs.sessionIdx())
        val start = items.indexOfFirst { it.id == curId }
        c.setMediaItems(items.map { it.toItem() }, start.coerceAtLeast(0), if (start >= 0) prefs.sessionPos() else 0L)
        c.prepare()
    }

    // ── Position polling ──
    LaunchedEffect(ps.controller, visible) {
        while (visible) {
            ps.controller?.let {
                ps.pos = it.currentPosition
                ps.dur = it.duration.coerceAtLeast(0)
            }
            delay(if (ps.playing) 350 else 1000)
        }
    }

    // ── Listening stats recording (counts play after 30s or half the song) ──
    LaunchedEffect(ps.controller) {
        var countedId = ""
        while (true) {
            val c = ps.controller
            if (c != null && c.isPlaying) {
                val id = c.currentMediaItem?.mediaId
                val d = c.duration
                val need = if (d > 0) minOf(30_000L, d / 2) else 30_000L
                if (id != null) {
                    if (c.currentPosition < 2_000L && countedId == id) countedId = ""
                    if (countedId != id && c.currentPosition >= need) {
                        id.toLongOrNull()?.let { app.history.recordPlay(it, need / 1000) }
                        countedId = id
                    }
                }
            }
            delay(3_000)
        }
    }

    // ── Speed & Sleep timer ──
    LaunchedEffect(ps.controller, prefs.speed) {
        ps.controller?.setPlaybackSpeed(speeds[prefs.speed])
    }

    LaunchedEffect(sleepEnd) {
        while (sleepEnd > System.currentTimeMillis()) {
            now = System.currentTimeMillis()
            delay(1000)
        }
        if (sleepEnd > 0) {
            ps.controller?.pause()
            sleepEnd = 0
        }
    }

    // ── Search & sorting (background, debounced) ──
    val sortMode = prefs.sort
    val sorted by produceState(emptyList<Song>(), app.songs, sortMode) {
        value = withContext(Dispatchers.Default) { sortSongs(app.songs, sortMode) }
    }
    val results by produceState(emptyList<Song>(), sorted, query) {
        if (query.isNotBlank()) delay(120)
        val q = query.trim().lowercase()
        value = withContext(Dispatchers.Default) {
            if (q.isEmpty()) sorted else sorted.filter { it.key.contains(q) }
        }
    }

    // ── Dynamic Artwork Palette & 5 Themes ──
    val item = ps.current
    var pal by remember { mutableStateOf<ArtPalette?>(null) }
    LaunchedEffect(item?.mediaId, prefs.dynamic) {
        pal = if (prefs.dynamic && item != null) artPalette(ctx, item.mediaMetadata.artworkUri) else null
    }

    val motion = prefs.motion
    val baseAccent = Accents[prefs.accent].second
    val accent = animColor(pal?.accent ?: baseAccent, motion)
    val base = uiColors(prefs.mode, accent)
    val tint = pal != null && prefs.mode == 0
    val top = animColor(if (tint) lerp(base.top, pal!!.top, 0.45f) else base.top, motion)
    val mid = animColor(if (tint) lerp(base.mid, pal!!.mid, 0.45f) else base.mid, motion)
    val bottom = animColor(if (tint) lerp(base.bottom, pal!!.bottom, 0.45f) else base.bottom, motion)
    val ui = uiColors(prefs.mode, accent, top, bottom, mid)

    val npBase = uiColors(if (prefs.mode == 1) 1 else 0, accent)
    val npTop = animColor(pal?.top ?: npBase.top, motion)
    val npMid = animColor(pal?.mid ?: npBase.mid, motion)
    val npBottom = animColor(pal?.bottom ?: npBase.bottom, motion)
    val npUi = uiColors(if (prefs.mode == 1) 1 else 0, accent, npTop, npBottom, npMid)

    val view = LocalView.current
    SideEffect {
        val w = (view.context as? Activity)?.window ?: return@SideEffect
        val light = ui.light && !showNow
        WindowCompat.getInsetsController(w, view).apply {
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
        }
    }

    val scheme = if (ui.light) {
        lightColorScheme(primary = ui.accent, onPrimary = ui.onAccent, surface = Color.White, onSurface = ui.text)
    } else {
        darkColorScheme(primary = ui.accent, onPrimary = ui.onAccent, surface = Color(0xFF161522), onSurface = ui.text)
    }

    val sleepLabel = when {
        sleepAfterCurrentSong -> "Stop after song"
        sleepEnd > now -> "Sleep " + fmt(sleepEnd - now)
        else -> "Sleep timer"
    }

    // ── Back Handlers ──
    BackHandler(showEqualizer) { showEqualizer = false }
    BackHandler(showLyrics) { showLyrics = false }
    BackHandler(showHistory) { showHistory = false }
    BackHandler(showQueue) { showQueue = false }
    BackHandler(showNow) { showNow = false }
    BackHandler(detail != null) { detail = null }
    BackHandler(tab != 0) { tab = 0 }

    val enter = when (motion) {
        0 -> slideInVertically(tween(350)) { it / 3 } + fadeIn(tween(250))
        1 -> fadeIn(tween(120))
        else -> EnterTransition.None
    }
    val exit = when (motion) {
        0 -> slideOutVertically(tween(300)) { it / 3 } + fadeOut(tween(200))
        1 -> fadeOut(tween(120))
        else -> ExitTransition.None
    }

    CompositionLocalProvider(
        LocalUi provides ui,
        LocalArtShape provides prefs.artShape,
        LocalMotion provides motion,
        LocalContentColor provides ui.text
    ) {
        MaterialTheme(colorScheme = scheme) {
            Box(Modifier.fillMaxSize().background(uiBrush(ui))) {
                if (prefs.wallpaperRev > 0) {
                    AsyncImage(prefs.wallpaperFile.takeIf { prefs.wallpaperRev > 0 }, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    Box(Modifier.fillMaxSize().background(ui.top.copy(alpha = if (ui.light) 0.55f else 0.72f)))
                }

                Column(Modifier.fillMaxSize().statusBarsPadding()) {
                    Box(Modifier.weight(1f)) {
                        val d = detail
                        if (d != null) {
                            DetailScreen(app, d) { detail = null }
                        } else when (tab) {
                            0 -> HomeScreen(
                                app = app,
                                chip = homeChip,
                                onChip = { homeChip = it },
                                loaded = loaded,
                                granted = granted,
                                onRequest = { launcher.launch(perms) },
                                onSettings = openAppSettings,
                                onOpen = { detail = it },
                                onSearch = { tab = 1; searchOn = true; musicChip = 0 },
                                onLiked = { tab = 1; musicChip = 6 },
                                onHistory = { showHistory = true },
                                onEqualizer = { showEqualizer = true },
                                onProfile = { tab = 2 }
                            )
                            1 -> MyMusicScreen(
                                app = app,
                                results = results,
                                query = query,
                                onQuery = { query = it },
                                chip = musicChip,
                                onChip = { musicChip = it },
                                searchOn = searchOn,
                                onSearchToggle = { searchOn = !searchOn; if (!searchOn) query = "" },
                                loaded = loaded,
                                granted = granted,
                                onRequest = { launcher.launch(perms) },
                                onSettings = openAppSettings,
                                onBack = { tab = 0 },
                                onLiked = { musicChip = if (musicChip == 6) 0 else 6 },
                                onOpen = { detail = it }
                            )
                            3 -> OnlineScreen(app)
                            else -> SettingsScreen(
                                app = app,
                                onRescan = { if (granted) reload() },
                                onHistory = { showHistory = true },
                                onEqualizer = { showEqualizer = true }
                            )
                        }
                    }

                    MiniPlayer(app) { showNow = true }
                    Box(Modifier.navigationBarsPadding()) {
                        FloatNav(tab) { tab = it; detail = null }
                    }
                }

                // Full Now Playing overlay
                AnimatedVisibility(showNow && item != null, enter = enter, exit = exit) {
                    CompositionLocalProvider(LocalUi provides npUi, LocalContentColor provides npUi.text) {
                        Box(Modifier.fillMaxSize().background(uiBrush(npUi))) {
                            if (prefs.blur) item?.let {
                                AsyncImage(it.mediaMetadata.artworkUri, null, Modifier.fillMaxSize().blur(48.dp), alpha = 0.28f, contentScale = ContentScale.Crop)
                            }
                            NowPlaying(
                                app = app,
                                sleepLabel = sleepLabel,
                                onSleep = { showSleep = true },
                                onQueue = { showQueue = true },
                                onEqualizer = { showEqualizer = true },
                                onLyrics = { showLyrics = true },
                                onTagEdit = { editingSong = it },
                                onClose = { showNow = false }
                            )
                        }
                    }
                }

                // Queue overlay
                AnimatedVisibility(showQueue, enter = enter, exit = exit) {
                    CompositionLocalProvider(LocalUi provides npUi, LocalContentColor provides npUi.text) {
                        Box(Modifier.fillMaxSize().background(uiBrush(npUi))) {
                            QueueScreen(app) { showQueue = false }
                        }
                    }
                }

                // Listening Stats & History screen
                AnimatedVisibility(showHistory, enter = enter, exit = exit) {
                    CompositionLocalProvider(LocalUi provides ui, LocalContentColor provides ui.text) {
                        Box(Modifier.fillMaxSize().background(uiBrush(ui))) {
                            HistoryStatsSheet(app) { showHistory = false }
                        }
                    }
                }

                // Equalizer Bottom Sheet
                if (showEqualizer) {
                    EqualizerSheet(app) { showEqualizer = false }
                }

                // Lyrics Sheet
                if (showLyrics) {
                    LyricsSheet(app) { showLyrics = false }
                }

                // Tag Editor Dialog
                editingSong?.let { song ->
                    TagEditorDialog(app, song) { editingSong = null }
                }

                // Add to Playlist Dialog
                AddToPlaylistDialog(app)

                // Sleep Timer Dialog
                if (showSleep) {
                    AlertDialog(
                        onDismissRequest = { showSleep = false },
                        confirmButton = {},
                        containerColor = if (ui.light) Color.White else Color(0xFF1E1E2A),
                        title = { Text("Sleep Timer", color = ui.text, fontWeight = FontWeight.Bold) },
                        text = {
                            Column {
                                TextButton(
                                    onClick = {
                                        sleepAfterCurrentSong = true
                                        sleepEnd = 0
                                        showSleep = false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Stop after current song", color = ui.accentText, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                }
                                listOf(15, 30, 45, 60, 90).forEach { m ->
                                    TextButton(
                                        onClick = {
                                            sleepAfterCurrentSong = false
                                            sleepEnd = System.currentTimeMillis() + m * 60000L
                                            now = System.currentTimeMillis()
                                            showSleep = false
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("$m minutes", color = ui.text, fontSize = 15.sp)
                                    }
                                }
                                TextButton(
                                    onClick = {
                                        sleepEnd = 0
                                        sleepAfterCurrentSong = false
                                        showSleep = false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Turn off sleep timer", color = ui.accentText, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun FloatNav(tab: Int, onTab: (Int) -> Unit) {
    val u = LocalUi.current
    val items = listOf(
        Triple("Home", P.Home, 0),
        Triple("My Music", P.Note, 1),
        Triple("Online", GLOBE, 3),
        Triple("Settings", P.Gear, 2)
    )

    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
        Row(
            Modifier.glass(34.dp).padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items.forEach { (label, icon, i) ->
                val sel = tab == i
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .then(if (sel) Modifier.background(u.accent) else Modifier)
                        .semantics { contentDescription = label }
                        .selectable(selected = sel, role = Role.Tab, onClick = { onTab(i) }),
                    contentAlignment = Alignment.Center
                ) {
                    Ico(icon, if (sel) u.onAccent else u.dim, 24.dp)
                }
            }
        }
    }
}
