package com.offline.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class OnlineLink(
    val platform: String,
    val embed: String,
    val external: String,
    val rawUrl: String
)

data class OnlineMeta(
    val title: String,
    val author: String?,
    val thumb: String?
)

private val ytHosts = setOf(
    "youtube.com",
    "www.youtube.com",
    "m.youtube.com",
    "music.youtube.com",
    "youtu.be"
)

fun parseOnlineLink(raw: String): OnlineLink? {
    val s = raw.trim()
    if (s.isEmpty()) return null
    val fullUrl = if (s.startsWith("http://") || s.startsWith("https://")) s else "https://$s"

    // Parse using standard Java URI so it works seamlessly in both Android runtime and JVM unit tests
    val javaUri = try { java.net.URI(fullUrl) } catch (e: Exception) { null }
    val host = javaUri?.host?.lowercase() ?: try {
        Uri.parse(fullUrl).host?.lowercase()
    } catch (e: Exception) { null } ?: return null

    val path = javaUri?.path ?: ""
    val query = javaUri?.query ?: ""
    val segs = path.split('/').filter { it.isNotEmpty() }

    fun getQueryParam(name: String): String? {
        val pattern = Regex("(?:[?&])$name=([^&]+)")
        return pattern.find("?$query")?.groupValues?.getOrNull(1)
    }

    if (host in ytHosts) {
        val id = when {
            host == "youtu.be" -> segs.firstOrNull()
            segs.firstOrNull() in setOf("shorts", "embed", "live") -> segs.getOrNull(1)
            else -> getQueryParam("v")
        }
        val pl = getQueryParam("list")
        return when {
            id != null && Regex("[A-Za-z0-9_-]{11}").matches(id) -> {
                val embedUrl = "https://www.youtube.com/embed/$id?autoplay=1&playsinline=1&enablejsapi=1&rel=0" +
                        (pl?.let { "&list=$it" } ?: "")
                OnlineLink("YouTube", embedUrl, "https://www.youtube.com/watch?v=$id", s)
            }
            pl != null && segs.firstOrNull() == "playlist" -> {
                OnlineLink(
                    "YouTube",
                    "https://www.youtube.com/embed/videoseries?list=$pl&autoplay=1&playsinline=1&enablejsapi=1",
                    "https://www.youtube.com/playlist?list=$pl",
                    s
                )
            }
            else -> null
        }
    }

    if (host == "open.spotify.com") {
        val p = segs.filter { !it.startsWith("intl-") }
        val type = p.getOrNull(0)
        val id = p.getOrNull(1)
        if (type in setOf("track", "album", "playlist", "episode", "show", "artist") && id != null && Regex("[A-Za-z0-9]{22}").matches(id)) {
            val embedUrl = "https://open.spotify.com/embed/$type/$id?utm_source=generator&theme=0"
            return OnlineLink("Spotify", embedUrl, "https://open.spotify.com/$type/$id", s)
        }
    }
    return null
}

suspend fun fetchOnlineMeta(l: OnlineLink): OnlineMeta? = withContext(Dispatchers.IO) {
    try {
        val api = if (l.platform == "YouTube") {
            "https://www.youtube.com/oembed?format=json&url="
        } else {
            "https://open.spotify.com/oembed?url="
        }
        val c = URL(api + URLEncoder.encode(l.external, "UTF-8")).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:109.0) Gecko/109.0 Firefox/119.0")
        if (c.responseCode != 200) return@withContext null
        val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
        OnlineMeta(
            title = j.optString("title").ifBlank { "${l.platform} Stream" },
            author = j.optString("author_name").takeIf { it.isNotBlank() } ?: l.platform,
            thumb = j.optString("thumbnail_url").takeIf { it.isNotBlank() }
        )
    } catch (e: Exception) {
        null
    }
}

/**
 * Singleton managing background audio streaming for YouTube & Spotify.
 * Synchronizes with PlaybackService for lock screen and notification controls,
 * and maintains continuous background audio until another song is played.
 */
object OnlineStreamManager {
    var urlText by mutableStateOf("")
    var currentLink by mutableStateOf<OnlineLink?>(null)
    var currentMeta by mutableStateOf<OnlineMeta?>(null)
    var isPlaying by mutableStateOf(false)
    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)

    var activeWebView: WebView? = null
    private var wakeLock: PowerManager.WakeLock? = null

    fun playStream(context: Context, rawUrl: String, scope: CoroutineScope) {
        val link = parseOnlineLink(rawUrl)
        if (link == null) {
            errorMessage = if (rawUrl.isBlank()) "Please paste a YouTube or Spotify link first." else "That isn’t a supported YouTube or Spotify link."
            return
        }
        errorMessage = null
        urlText = rawUrl
        currentLink = link
        isLoading = true

        // Acquire WakeLock so device CPU stays awake when user locks screen
        try {
            if (wakeLock == null) {
                val pm = context.applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
                wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FRPlayer:OnlineStreamWakeLock")
            }
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(10 * 60 * 60 * 1000L) // 10 hours max
            }
        } catch (e: Exception) {}

        // Immediate start with basic metadata
        val initialTitle = if (link.platform == "YouTube") "YouTube Audio Stream" else "Spotify Audio Stream"
        PlaybackService.startOnlineStream(context, initialTitle, link.platform, null)
        isPlaying = true

        // Fetch detailed track metadata asynchronously
        scope.launch {
            val meta = fetchOnlineMeta(link)
            currentMeta = meta
            isLoading = false
            if (meta != null && isPlaying) {
                val artUri = meta.thumb?.let { Uri.parse(it) }
                PlaybackService.startOnlineStream(context, meta.title, meta.author ?: link.platform, artUri)
            }
        }

        // Load into persistent WebView
        activeWebView?.let { wv ->
            if (wv.tag != link.embed) {
                wv.tag = link.embed
                if (link.platform == "YouTube") {
                    wv.loadUrl(link.embed, mapOf("Referer" to "https://com.offline.player"))
                } else {
                    wv.loadUrl(link.embed)
                }
            }
        }
    }

    fun onRemotePlayPauseChanged(playing: Boolean) {
        isPlaying = playing
        executeWebViewPlayPause(playing)
    }

    fun toggle() {
        val next = !isPlaying
        isPlaying = next
        executeWebViewPlayPause(next)
        val player = PlaybackService.activePlayer ?: return
        if (next) player.play() else player.pause()
    }

    fun pause() {
        if (!isPlaying) return
        isPlaying = false
        executeWebViewPlayPause(false)
        PlaybackService.activePlayer?.pause()
    }

    fun resume() {
        if (isPlaying) return
        isPlaying = true
        executeWebViewPlayPause(true)
        PlaybackService.activePlayer?.play()
    }

    fun stop() {
        isPlaying = false
        isLoading = false
        activeWebView?.loadUrl("about:blank")
        activeWebView?.tag = null
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {}
        PlaybackService.stopOnlineStream()
    }

    private fun executeWebViewPlayPause(play: Boolean) {
        val wv = activeWebView ?: return
        val js = """
            (function() {
                var v = document.querySelector('video') || document.querySelector('audio');
                if (v) {
                    if (${play}) { v.play(); } else { v.pause(); }
                    return;
                }
                var btn = document.querySelector('button[data-testid="play-pause-button"]');
                if (btn) {
                    btn.click();
                    return;
                }
                var f = document.querySelector('iframe');
                if (f && f.contentWindow) {
                    f.contentWindow.postMessage(JSON.stringify({
                        event: 'command',
                        func: '${if (play) "playVideo" else "pauseVideo"}',
                        args: ''
                    }), '*');
                }
            })();
        """.trimIndent()
        wv.post {
            try {
                wv.evaluateJavascript(js, null)
            } catch (e: Exception) {}
        }
    }
}

@Composable
fun PersistentOnlineEmbedView(
    link: OnlineLink?,
    modifier: Modifier = Modifier
) {
    val allowed = listOf("youtube.com", "spotify.com", "google.com", "googlevideo.com", "scdn.co")
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                }
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val h = request.url.host ?: return true
                        return allowed.none { h.endsWith(it) }
                    }
                }
                OnlineStreamManager.activeWebView = this
                link?.let { l ->
                    tag = l.embed
                    if (l.platform == "YouTube") {
                        loadUrl(l.embed, mapOf("Referer" to "https://com.offline.player"))
                    } else {
                        loadUrl(l.embed)
                    }
                }
            }
        },
        update = { wv ->
            OnlineStreamManager.activeWebView = wv
            if (link != null && wv.tag != link.embed) {
                wv.tag = link.embed
                if (link.platform == "YouTube") {
                    wv.loadUrl(link.embed, mapOf("Referer" to "https://com.offline.player"))
                } else {
                    wv.loadUrl(link.embed)
                }
            }
        },
        modifier = modifier
    )
}

@Composable
fun OnlineScreen(app: AppState) {
    val u = LocalUi.current
    val ctx = LocalContext.current
    val clip = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val mgr = OnlineStreamManager

    // Restore last saved URL if text is empty
    LaunchedEffect(Unit) {
        if (mgr.urlText.isBlank() && app.prefs.lastOnlineUrl.isNotBlank()) {
            mgr.urlText = app.prefs.lastOnlineUrl
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.height(12.dp))

        // Title and live background badge
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Online Streaming", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = u.text)
                Text(
                    "Stream YouTube & Spotify songs with background playback & lock screen controls.",
                    fontSize = 13.sp,
                    color = u.dim
                )
            }
        }

        // URL Input
        OutlinedTextField(
            value = mgr.urlText,
            onValueChange = {
                mgr.urlText = it
                mgr.errorMessage = null
                app.prefs.setLastOnlineUrl(it)
            },
            singleLine = true,
            isError = mgr.errorMessage != null,
            placeholder = { Text("Paste YouTube or Spotify URL", color = u.dim) },
            supportingText = { mgr.errorMessage?.let { Text(it, color = Color(0xFFFF5252)) } },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = u.text,
                unfocusedTextColor = u.text,
                focusedBorderColor = u.accent,
                unfocusedBorderColor = u.border,
                cursorColor = u.accent
            ),
            modifier = Modifier.fillMaxWidth()
        )

        // Action Buttons Row
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Paste Link") {
                clip.getText()?.text?.let {
                    mgr.urlText = it
                    mgr.errorMessage = null
                    app.prefs.setLastOnlineUrl(it)
                }
            }
            Chip("Play Stream", true) {
                mgr.playStream(ctx, mgr.urlText, scope)
            }
            if (mgr.urlText.isNotBlank() || mgr.currentLink != null) {
                Chip("Clear") {
                    mgr.urlText = ""
                    mgr.errorMessage = null
                    app.prefs.setLastOnlineUrl("")
                    mgr.stop()
                }
            }
        }

        // Active Online Playing Card
        mgr.currentLink?.let { l ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .glass(24.dp)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Status Header
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (mgr.isPlaying) Color(0xFF4CAF50) else Color(0xFFFFB300))
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (mgr.isPlaying) "Playing in Background" else "Playback Paused",
                            color = if (mgr.isPlaying) Color(0xFF4CAF50) else Color(0xFFFFB300),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Box(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(u.accent.copy(alpha = 0.18f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            l.platform,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = u.accentText
                        )
                    }
                }

                // Track Details
                if (mgr.isLoading) {
                    LinearProgressIndicator(
                        Modifier.fillMaxWidth().height(3.dp),
                        color = u.accent,
                        trackColor = u.accent.copy(alpha = 0.2f)
                    )
                }

                mgr.currentMeta?.let { m ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (m.thumb != null) {
                            AsyncImage(
                                model = m.thumb,
                                contentDescription = m.title,
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.width(12.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                m.title,
                                color = u.text,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            m.author?.let {
                                Text(it, color = u.dim, fontSize = 12.sp)
                            }
                        }
                    }
                }

                // Persistent Web Player View (Stays alive across tab switches!)
                PersistentOnlineEmbedView(
                    link = l,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (l.platform == "Spotify") 232.dp else 210.dp)
                        .clip(RoundedCornerShape(16.dp))
                )

                // Playback & External Controls
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Chip(if (mgr.isPlaying) "Pause Audio" else "Resume Audio", true) {
                        mgr.toggle()
                    }
                    Chip("Stop Stream") {
                        mgr.stop()
                    }
                    Chip("Open in ${l.platform}") {
                        try {
                            ctx.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(l.external))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        } catch (e: Exception) {}
                    }
                }
            }
        }

        // Persistent Background & Lock Screen Information Card
        Column(
            Modifier
                .fillMaxWidth()
                .glass(18.dp)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🎵 Background & Lock Screen Enabled", fontWeight = FontWeight.Bold, color = u.text, fontSize = 13.sp)
            }
            Text(
                "• When an online song is playing, it will continue playing seamlessly in the background.\n" +
                "• Switching to the My Music or Home tab will NOT stop your song.\n" +
                "• Locking your phone or switching apps keeps audio running.\n" +
                "• Lock screen and notification center display the song title, artist, artwork, and Play/Pause controls.\n" +
                "• The online song will play until you select or play another song.",
                fontSize = 12.sp,
                color = u.dim,
                lineHeight = 18.sp
            )
        }

        Spacer(Modifier.height(16.dp))
    }
}
