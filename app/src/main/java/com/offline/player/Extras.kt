package com.offline.player

import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

@Composable
fun EqualizerSheet(app: AppState, onDismiss: () -> Unit) {
    val eq = app.eq
    val u = LocalUi.current
    val bandNames = listOf("60 Hz", "230 Hz", "910 Hz", "3.6 kHz", "14 kHz")

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(if (u.light) Color(0xFFF7F5FC) else Color(0xFF161522))
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Audio Equalizer", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = u.text)
                        Text(
                            if (eq.isSupported) "Hardware audio effects active" else "Audio effects not supported on this device",
                            fontSize = 12.sp,
                            color = if (eq.isSupported) u.dim else Color(0xFFFF6B6B)
                        )
                    }
                    Switch(
                        checked = eq.enabled,
                        onCheckedChange = { eq.setEnabledState(it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = u.accent, checkedThumbColor = u.onAccent)
                    )
                }

                // Presets Dropdown
                var presetMenu by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(
                        onClick = { presetMenu = true },
                        enabled = eq.enabled,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Preset: ${eq.presets.getOrElse(eq.presetIndex) { "Custom" }}", color = if (eq.enabled) u.text else u.dim)
                    }
                    DropdownMenu(presetMenu, { presetMenu = false }) {
                        eq.presets.forEachIndexed { i, name ->
                            DropdownMenuItem(text = { Text(name) }, onClick = {
                                presetMenu = false
                                eq.setPreset(i)
                            })
                        }
                    }
                }

                // 5 Frequency sliders
                Text("Frequency Bands", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = u.text)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    val levels = eq.bandLevels
                    for (i in 0 until 5) {
                        val level = levels.getOrElse(i) { 0 }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${if (level > 0) "+$level" else "$level"} dB", fontSize = 10.sp, color = u.dim)
                            Slider(
                                value = level.toFloat(),
                                onValueChange = { eq.setBand(i, it.toInt()) },
                                valueRange = -10f..10f,
                                steps = 19,
                                enabled = eq.enabled,
                                modifier = Modifier
                                    .height(130.dp)
                                    .width(44.dp),
                                colors = SliderDefaults.colors(thumbColor = u.accent, activeTrackColor = u.accent)
                            )
                            Text(bandNames.getOrElse(i) { "" }, fontSize = 10.sp, color = u.text, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                // Bass Boost & Virtualizer
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Bass Boost: ${eq.bassStrength / 10}%", fontSize = 13.sp, color = u.text, fontWeight = FontWeight.Medium)
                        Slider(
                            value = eq.bassStrength.toFloat(),
                            onValueChange = { eq.setBass(it.toInt()) },
                            valueRange = 0f..1000f,
                            enabled = eq.enabled,
                            colors = SliderDefaults.colors(thumbColor = u.accent, activeTrackColor = u.accent)
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Virtualizer: ${eq.virtualizerStrength / 10}%", fontSize = 13.sp, color = u.text, fontWeight = FontWeight.Medium)
                        Slider(
                            value = eq.virtualizerStrength.toFloat(),
                            onValueChange = { eq.setVirtualizer(it.toInt()) },
                            valueRange = 0f..1000f,
                            enabled = eq.enabled,
                            colors = SliderDefaults.colors(thumbColor = u.accent, activeTrackColor = u.accent)
                        )
                    }
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = u.accent, contentColor = u.onAccent)
                ) {
                    Text("Done", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun LyricsSheet(app: AppState, onDismiss: () -> Unit) {
    val u = LocalUi.current
    val item = app.ps.current
    val songId = item?.mediaId?.toLongOrNull() ?: 0L
    val title = item?.mediaMetadata?.title?.toString() ?: "Song"
    val ctx = LocalContext.current

    var editing by remember { mutableStateOf(false) }
    var lyricText by remember(songId) { mutableStateOf(app.lyrics.getLyrics(songId) ?: "") }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f))
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier
                    .fillMaxWidth(0.92f)
                    .fillMaxHeight(0.82f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(if (u.light) Color.White else Color(0xFF1A192A))
                    .padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Lyrics", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = u.text)
                        Text(title, fontSize = 13.sp, color = u.dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Chip(if (editing) "Cancel" else "Edit") { editing = !editing }
                }

                Spacer(Modifier.height(12.dp))

                Box(Modifier.weight(1f)) {
                    if (editing) {
                        OutlinedTextField(
                            value = lyricText,
                            onValueChange = { lyricText = it },
                            modifier = Modifier.fillMaxSize(),
                            placeholder = { Text("Paste or type song lyrics here...", color = u.dim) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = u.text, unfocusedTextColor = u.text,
                                cursorColor = u.accent, focusedBorderColor = u.accent
                            )
                        )
                    } else {
                        if (lyricText.isBlank()) {
                            Column(
                                Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text("No lyrics found for this track.", color = u.dim, fontSize = 15.sp)
                                Spacer(Modifier.height(8.dp))
                                Button(
                                    onClick = { editing = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = u.accent, contentColor = u.onAccent)
                                ) {
                                    Text("Add Lyrics")
                                }
                            }
                        } else {
                            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                                Text(
                                    lyricText,
                                    color = u.text,
                                    fontSize = 17.sp,
                                    lineHeight = 28.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Close", color = u.text)
                    }
                    if (editing) {
                        Button(
                            onClick = {
                                app.lyrics.saveLyrics(songId, lyricText)
                                editing = false
                                Toast.makeText(ctx, "Lyrics saved", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = u.accent, contentColor = u.onAccent)
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TagEditorDialog(app: AppState, song: Song, onDismiss: () -> Unit) {
    val u = LocalUi.current
    val ctx = LocalContext.current
    var title by remember { mutableStateOf(song.title) }
    var artist by remember { mutableStateOf(song.artist) }
    var album by remember { mutableStateOf(song.album) }
    var year by remember { mutableStateOf(if (song.year > 0) song.year.toString() else "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = if (u.light) Color.White else Color(0xFF1E1E2E),
        title = { Text("Edit Song Information", color = u.text, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = u.text, unfocusedTextColor = u.text)
                )
                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text("Artist") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = u.text, unfocusedTextColor = u.text)
                )
                OutlinedTextField(
                    value = album,
                    onValueChange = { album = it },
                    label = { Text("Album") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = u.text, unfocusedTextColor = u.text)
                )
                OutlinedTextField(
                    value = year,
                    onValueChange = { year = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("Year") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = u.text, unfocusedTextColor = u.text)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val y = year.toIntOrNull() ?: 0
                    app.meta.saveOverride(song.id, title, artist, album, y)
                    val updated = app.rawSongs.map {
                        if (it.id == song.id) it.copy(title = title.ifBlank { it.title }, artist = artist.ifBlank { it.artist }, album = album, year = y)
                        else it
                    }
                    app.setSongsList(updated)
                    Toast.makeText(ctx, "Song details updated", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            ) {
                Text("Save Changes", color = u.accentText, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = u.dim) }
        }
    )
}

@Composable
fun VaultPinDialog(
    vault: VaultStore,
    onSuccess: () -> Unit,
    onDismiss: () -> Unit
) {
    val u = LocalUi.current
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = if (u.light) Color.White else Color(0xFF1E1E2E),
        title = { Text("Private Vault PIN", color = u.text, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Enter your 4-digit PIN to access private tracks (Default: 1234)", fontSize = 12.sp, color = u.dim)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = {
                        if (it.length <= 4) { pin = it; error = false }
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    isError = error,
                    supportingText = { if (error) Text("Incorrect PIN. Try default 1234.", color = Color.Red) },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = u.text, unfocusedTextColor = u.text)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (pin == vault.getPin()) {
                        onSuccess()
                    } else {
                        error = true
                    }
                }
            ) {
                Text("Unlock", color = u.accentText, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = u.dim) }
        }
    )
}

@Composable
fun HistoryStatsSheet(app: AppState, onBack: () -> Unit) {
    val u = LocalUi.current
    val history = app.history
    val totalMins = history.totalSeconds / 60
    val totalHrs = totalMins / 60
    var confirmClear by remember { mutableStateOf(false) }

    val recentSongs = remember(history.history, app.byId) {
        history.history.mapNotNull { item -> app.byId[item.songId]?.let { it to item.timestamp } }
    }

    val topArtists = remember(history.plays, app.byId) {
        history.plays.entries
            .mapNotNull { (id, count) -> app.byId[id]?.let { it.artist to count } }
            .groupBy({ it.first }, { it.second })
            .mapValues { it.value.sum() }
            .entries.sortedByDescending { it.value }.take(5)
    }

    var historyTab by remember { mutableIntStateOf(0) }
    val mostPlayedSongs = remember(history.plays, app.byId) {
        history.plays.entries.filter { it.value > 0 }
            .sortedByDescending { it.value }
            .mapNotNull { (id, count) -> app.byId[id]?.let { it to count } }
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBtn(P.Back, "Back", onBack)
            Text("Listening History & Stats", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = u.text, modifier = Modifier.weight(1f))
            if (recentSongs.isNotEmpty() || mostPlayedSongs.isNotEmpty()) {
                Chip("Clear") { confirmClear = true }
            }
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Listening Overview Stats
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(
                        Modifier
                            .weight(1f)
                            .glass(18.dp)
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(if (totalHrs > 0) "${totalHrs}h ${totalMins % 60}m" else "${totalMins}m", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = u.accentText)
                        Text("Listening Time", fontSize = 12.sp, color = u.dim)
                    }
                    Column(
                        Modifier
                            .weight(1f)
                            .glass(18.dp)
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("${history.plays.values.sum()}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = u.accentText)
                        Text("Tracks Played", fontSize = 12.sp, color = u.dim)
                    }
                }
            }

            // Top Artists
            if (topArtists.isNotEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().glass(18.dp).padding(16.dp)) {
                        Text("Top Artists", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = u.text)
                        Spacer(Modifier.height(8.dp))
                        topArtists.forEachIndexed { i, (artist, count) ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("${i + 1}. $artist", color = u.text, fontSize = 14.sp)
                                Text("$count plays", color = u.dim, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Tabs for Recent vs Most Played
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Recently Played (${recentSongs.size})", historyTab == 0) { historyTab = 0 }
                    Chip("Most Played (${mostPlayedSongs.size})", historyTab == 1) { historyTab = 1 }
                }
            }

            if (historyTab == 0) {
                if (recentSongs.isEmpty()) {
                    item {
                        Text("No playback history recorded yet.", color = u.dim, fontSize = 14.sp, modifier = Modifier.padding(vertical = 20.dp))
                    }
                } else {
                    items(recentSongs) { (song, _) ->
                        MusicRow(app, song, app.ps.current?.mediaId == song.id.toString(), {
                            app.ps.playSongs(listOf(song), 0)
                        })
                    }
                }
            } else {
                if (mostPlayedSongs.isEmpty()) {
                    item {
                        Text("No most-played tracks yet. Listen for 30s+ to record plays.", color = u.dim, fontSize = 14.sp, modifier = Modifier.padding(vertical = 20.dp))
                    }
                } else {
                    items(mostPlayedSongs) { (song, count) ->
                        MusicRow(app, song, app.ps.current?.mediaId == song.id.toString(), {
                            app.ps.playSongs(mostPlayedSongs.map { it.first }, mostPlayedSongs.indexOfFirst { it.first.id == song.id })
                        }, extras = listOf("$count total plays" to {}))
                    }
                }
            }
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            "Clear Listening History?",
            "This will reset your play history and statistics stored on this device.",
            "Clear History",
            { confirmClear = false; history.clearHistory() },
            { confirmClear = false }
        )
    }
}

const val GLOBE = "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM4,12c0,-0.61 0.08,-1.21 0.21,-1.78L8.99,15v1c0,1.1 0.9,2 2,2v1.93C7.06,19.43 4,16.07 4,12zM18.89,17.4C18.63,16.58 17.88,16 17,16h-1v-3c0,-0.55 -0.45,-1 -1,-1H9v-2h2c0.55,0 1,-0.45 1,-1V7h2c1.1,0 2,-0.9 2,-2v-0.41C17.92,5.77 20,8.65 20,12c0,2.08 -0.81,3.98 -2.11,5.4z"

private class OnlineLink(val platform: String, val embed: String, val external: String)
private class OnlineMeta(val title: String, val author: String?, val thumb: String?)

private val ytHosts = setOf("youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com", "youtu.be")

private fun parseOnlineLink(raw: String): OnlineLink? {
    val s = raw.trim()
    if (s.isEmpty()) return null
    val uri = try { Uri.parse(if (s.startsWith("http")) s else "https://$s") } catch (e: Exception) { return null }
    val host = uri.host?.lowercase() ?: return null
    val segs = uri.pathSegments
    if (host in ytHosts) {
        val id = when {
            host == "youtu.be" -> segs.firstOrNull()
            segs.firstOrNull() in setOf("shorts", "embed", "live") -> segs.getOrNull(1)
            else -> uri.getQueryParameter("v")
        }
        val pl = uri.getQueryParameter("list")
        return when {
            id != null && Regex("[A-Za-z0-9_-]{11}").matches(id) ->
                OnlineLink("YouTube", "https://www.youtube.com/embed/$id?playsinline=1&rel=0" + (pl?.let { "&list=$it" } ?: ""), "https://www.youtube.com/watch?v=$id")
            pl != null && segs.firstOrNull() == "playlist" ->
                OnlineLink("YouTube", "https://www.youtube.com/embed/videoseries?list=$pl&playsinline=1", "https://www.youtube.com/playlist?list=$pl")
            else -> null
        }
    }
    if (host == "open.spotify.com") {
        val p = segs.filter { !it.startsWith("intl-") }
        val type = p.getOrNull(0)
        val id = p.getOrNull(1)
        if (type in setOf("track", "album", "playlist", "episode", "show", "artist") && id != null && Regex("[A-Za-z0-9]{22}").matches(id)) {
            return OnlineLink("Spotify", "https://open.spotify.com/embed/$type/$id", "https://open.spotify.com/$type/$id")
        }
    }
    return null
}

private suspend fun fetchOnlineMeta(l: OnlineLink): OnlineMeta? = withContext(Dispatchers.IO) {
    try {
        val api = if (l.platform == "YouTube") "https://www.youtube.com/oembed?format=json&url=" else "https://open.spotify.com/oembed?url="
        val c = URL(api + URLEncoder.encode(l.external, "UTF-8")).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        if (c.responseCode != 200) return@withContext null
        val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
        OnlineMeta(j.optString("title"), j.optString("author_name").takeIf { it.isNotBlank() }, j.optString("thumbnail_url").takeIf { it.isNotBlank() })
    } catch (e: Exception) { null }
}

@Composable
private fun OnlineEmbedView(l: OnlineLink) {
    val allowed = listOf("youtube.com", "spotify.com", "google.com")
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val h = request.url.host ?: return true
                        return allowed.none { h.endsWith(it) }
                    }
                }
            }
        },
        update = {
            if (it.tag != l.embed) {
                it.tag = l.embed
                if (l.platform == "YouTube") it.loadUrl(l.embed, mapOf("Referer" to "https://com.offline.player")) else it.loadUrl(l.embed)
            }
        },
        onRelease = { it.destroy() },
        modifier = Modifier.fillMaxWidth().height(if (l.platform == "Spotify") 232.dp else 210.dp).clip(RoundedCornerShape(16.dp))
    )
}

@Composable
fun OnlineScreen(app: AppState) {
    val u = LocalUi.current
    val ctx = LocalContext.current
    val clip = LocalClipboardManager.current
    var text by rememberSaveable { mutableStateOf("") }
    var link by remember { mutableStateOf<OnlineLink?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var meta by remember { mutableStateOf<OnlineMeta?>(null) }
    var metaState by remember { mutableIntStateOf(0) }

    LaunchedEffect(link) {
        val l = link ?: return@LaunchedEffect
        metaState = 1
        meta = fetchOnlineMeta(l)
        metaState = if (meta != null) 2 else 3
    }

    fun load() {
        val l = parseOnlineLink(text)
        if (l == null) {
            link = null
            error = if (text.isBlank()) "Paste a YouTube or Spotify link first." else "That isn’t a supported YouTube or Spotify link."
            return
        }
        error = null
        meta = null
        link = l
        app.ps.controller?.pause()
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Text("Online Streaming", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = u.text)
        Text("Play YouTube and Spotify links legally with official web player embeds.", fontSize = 13.sp, color = u.dim)

        OutlinedTextField(
            value = text,
            onValueChange = { text = it; error = null },
            singleLine = true,
            isError = error != null,
            placeholder = { Text("Paste YouTube or Spotify URL", color = u.dim) },
            supportingText = { error?.let { Text(it, color = Color.Red) } },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = u.text, unfocusedTextColor = u.text, focusedBorderColor = u.accent,
                unfocusedBorderColor = u.border, cursorColor = u.accent
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Paste from Clipboard") {
                clip.getText()?.text?.let { text = it; error = null }
            }
            Chip("Play Stream", true) { load() }
        }

        link?.let { l ->
            Column(Modifier.fillMaxWidth().glass(24.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Detected: ${l.platform}", color = u.accentText, fontWeight = FontWeight.SemiBold)
                when (metaState) {
                    1 -> Text("Loading track details…", color = u.dim, fontSize = 13.sp)
                    2 -> meta?.let { m ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (m.thumb != null) {
                                AsyncImage(m.thumb, null, Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                                Spacer(Modifier.width(12.dp))
                            }
                            Column {
                                Text(m.title, color = u.text, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                m.author?.let { Text(it, color = u.dim, fontSize = 12.sp) }
                            }
                        }
                    }
                    3 -> Text("Details unavailable (link may be private or restricted).", color = u.dim, fontSize = 13.sp)
                }

                OnlineEmbedView(l)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Open in ${l.platform}") {
                        try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(l.external)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) {}
                    }
                }
            }
        }

        Column(Modifier.fillMaxWidth().glass(16.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Policy & Storage Notice", fontWeight = FontWeight.Bold, color = u.text, fontSize = 13.sp)
            Text(
                "Direct stream ripping/downloading from YouTube and Spotify violates Google Play Developer Policies and platform terms of service. FR Music Player provides full streaming playback above, and plays all your local audio files (MP3, M4A, FLAC, WAV, OGG) offline.",
                fontSize = 12.sp, color = u.dim
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}
