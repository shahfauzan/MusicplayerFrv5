package com.offline.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.util.LruCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val Accents = listOf(
    "Ocean" to Color(0xFF5DADE2),
    "Sunset" to Color(0xFFFFA45B),
    "Forest" to Color(0xFF58D68D),
    "Berry" to Color(0xFFE58BFF),
    "Indigo" to Color(0xFF8C9EFF),
    "Violet" to Color(0xFF9B3DF0),
    "Ruby" to Color(0xFFFF5252),
    "Amber" to Color(0xFFFFD54F)
)

data class ThemeProfile(
    val id: Int,
    val name: String,
    val subtitle: String,
    val previewColors: List<Color>
)

val ThemeProfiles = listOf(
    ThemeProfile(0, "Violet Glass", "Luxury violet glassmorphism", listOf(Color(0xFF7B38B3), Color(0xFF3C2E92), Color(0xFF15143F))),
    ThemeProfile(1, "AMOLED Stealth", "Pure black energy saver", listOf(Color(0xFF000000), Color(0xFF0D0D12), Color(0xFF161622))),
    ThemeProfile(2, "Crystal Light", "Clean crisp daytime frost", listOf(Color(0xFFF7F5FC), Color(0xFFEDE7F6), Color(0xFFDCD2F2))),
    ThemeProfile(3, "Cyber Neon", "Electric dark cyan & navy", listOf(Color(0xFF0B2545), Color(0xFF133C55), Color(0xFF08182B))),
    ThemeProfile(4, "Sunset Amber", "Warm espresso & copper glow", listOf(Color(0xFF421C0E), Color(0xFF2C1309), Color(0xFF170803)))
)

@Immutable
class UiColors(
    val top: Color,
    val mid: Color,
    val bottom: Color,
    val accent: Color,
    val accentText: Color,
    val onAccent: Color,
    val text: Color,
    val dim: Color,
    val glassHi: Color,
    val glassLo: Color,
    val border: Color,
    val light: Boolean
)

fun uiColors(mode: Int, accent: Color, top: Color? = null, bottom: Color? = null, mid: Color? = null): UiColors {
    val light = mode == 2
    val t = top ?: when (mode) {
        1 -> Color(0xFF000000)
        2 -> Color(0xFFF7F5FC)
        3 -> Color(0xFF0B2545)
        4 -> Color(0xFF421C0E)
        else -> Color(0xFF7B38B3)
    }
    val m = mid ?: when (mode) {
        1 -> Color(0xFF040406)
        2 -> Color(0xFFECE6F8)
        3 -> Color(0xFF0E1E38)
        4 -> Color(0xFF2C1309)
        else -> Color(0xFF3C2E92)
    }
    val b = bottom ?: when (mode) {
        1 -> Color(0xFF06060A)
        2 -> Color(0xFFDDD4F2)
        3 -> Color(0xFF060D1A)
        4 -> Color(0xFF170803)
        else -> Color(0xFF15143F)
    }

    return UiColors(
        top = t,
        mid = m,
        bottom = b,
        accent = accent,
        accentText = if (light) lerp(accent, Color.Black, 0.45f) else lerp(accent, Color.White, 0.30f),
        onAccent = if (accent.luminance() > 0.55f) Color(0xFF101014) else Color.White,
        text = if (light) Color(0xFF181428) else Color.White,
        dim = if (light) Color(0xFF635C7A) else Color(0xFFCBC6E6),
        glassHi = if (light) Color.Black.copy(0.06f) else Color.White.copy(0.15f),
        glassLo = if (light) Color.Black.copy(0.02f) else Color.White.copy(0.05f),
        border = if (light) Color.Black.copy(0.10f) else Color.White.copy(0.18f),
        light = light
    )
}

fun uiBrush(u: UiColors): Brush = Brush.verticalGradient(listOf(u.top, u.mid, u.bottom))

val LocalUi = staticCompositionLocalOf { uiColors(0, Accents[5].second) }
val LocalArtShape = compositionLocalOf { 0 }
val LocalMotion = compositionLocalOf { 0 }

@Composable
fun animColor(c: Color, motion: Int): Color =
    animateColorAsState(c, tween(if (motion == 2) 0 else 500), label = "color").value

@Composable
fun Modifier.glass(r: Dp = 20.dp): Modifier {
    val u = LocalUi.current
    val s = RoundedCornerShape(r)
    return clip(s).background(Brush.verticalGradient(listOf(u.glassHi, u.glassLo))).border(1.dp, u.border, s)
}

/**
 * Premium theme-aware clickable modifier.
 * Replaces default ugly black pressed overlays with smooth accent-colored ripple.
 */
@Composable
fun Modifier.themeClickable(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    onClick: () -> Unit
): Modifier {
    val u = LocalUi.current
    val rippleColor = remember(u.accent, u.light) {
        if (u.light) u.accent.copy(alpha = 0.22f) else u.accent.copy(alpha = 0.30f)
    }
    return this.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = ripple(bounded = true, color = rippleColor),
        enabled = enabled,
        onClickLabel = onClickLabel,
        role = role,
        onClick = onClick
    )
}

fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

object P {
    const val Play = "M8 5v14l11-7z"
    const val Pause = "M6 19h4V5H6v14zm8-14v14h4V5h-4z"
    const val Next = "M6 18l8.5-6L6 6v12zM16 6v12h2V6h-2z"
    const val Prev = "M6 6h2v12H6zm3.5 6l8.5 6V6z"
    const val Shuffle = "M10.59 9.17L5.41 4 4 5.41l5.17 5.17 1.42-1.41zM14.5 4l2.04 2.04L4 18.59 5.41 20 17.96 7.46 20 9.5V4h-5.5zm.33 9.41l-1.41 1.41 3.13 3.13L14.5 20H20v-5.5l-2.04 2.04-3.13-3.13z"
    const val Repeat = "M7 7h10v3l4-4-4-4v3H5v6h2V7zm10 10H7v-3l-4 4 4 4v-3h12v-6h-2v4z"
    const val RepeatOne = "M7 7h10v3l4-4-4-4v3H5v6h2V7zm10 10H7v-3l-4 4 4 4v-3h12v-6h-2v4zm-4-2V9h-1l-2 1v1h1.5v4H13z"
    const val Heart = "M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"
    const val HeartOutline = "M16.5 3c-1.74 0-3.41.81-4.5 2.09C10.91 3.81 9.24 3 7.5 3 4.42 3 2 5.42 2 8.5c0 3.78 3.4 6.86 8.55 11.54L12 21.35l1.45-1.32C18.6 15.36 22 12.28 22 8.5 22 5.42 19.58 3 16.5 3zm-4.4 15.55l-.1.1-.1-.1C7.14 14.24 4 11.39 4 8.5 4 6.5 5.5 5 7.5 5c1.54 0 3.04.99 3.57 2.36h1.87C13.46 5.99 14.96 5 16.5 5c2 0 3.5 1.5 3.5 3.5 0 2.89-3.14 5.74-7.9 10.05z"
    const val Search = "M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z"
    const val More = "M12 8c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm0 2c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm0 6c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z"
    const val Queue = "M15 6H3v2h12V6zm0 4H3v2h12v-2zM3 16h8v-2H3v2zM17 6v8.18c-.31-.11-.65-.18-1-.18-1.66 0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3V8h3V6h-5z"
    const val Down = "M7.41 8.59L12 13.17l4.59-4.58L18 10l-6 6-6-6 1.41-1.41z"
    const val Back = "M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z"
    const val Close = "M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"
    const val Note = "M12 3v10.55c-.59-.34-1.27-.55-2-.55-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4V7h4V3h-6z"
    const val PlaylistAdd = "M14 10H2v2h12v-2zm0-4H2v2h12V6zm4 8v-4h-2v4h-4v2h4v4h2v-4h4v-2h-4zM2 16h8v-2H2v2z"
    const val List = "M3 13h2v-2H3v2zm0 4h2v-2H3v2zm0-8h2V7H3v2zm4 4h14v-2H7v2zm0 4h14v-2H7v2zM7 7v2h14V7H7z"
    const val Home = "M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z"
    const val ChevronLeft = "M15.41 7.41L14 6l-6 6 6 6 1.41-1.41L10.83 12z"
    const val Gear = "M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58c.18-.14.23-.41.12-.61l-1.92-3.32c-.12-.22-.37-.29-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54c-.04-.24-.24-.41-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58c-.18.14-.23.41-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z"
    const val Equalizer = "M10 20h4V4h-4v16zm-6 0h4v-8H4v8zM16 9v11h4V9h-4z"
    const val Lyrics = "M12 3v10.55c-.59-.34-1.27-.55-2-.55-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4V7h4V3h-6zm-2 16c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2z"
    const val Folder = "M10 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z"
    const val Vault = "M18 8h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zm-6 9c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2zm3.1-9H8.9V6c0-1.71 1.39-3.1 3.1-3.1 1.71 0 3.1 1.39 3.1 3.1v2z"
    const val Edit = "M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34c-.39-.39-1.02-.39-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z"
    const val History = "M13 3c-4.97 0-9 4.03-9 9H1l3.89 3.89.07.14L9 12H6c0-3.87 3.13-7 7-7s7 3.13 7 7-3.13 7-7 7c-1.93 0-3.68-.79-4.94-2.06l-1.42 1.42C8.27 19.99 10.51 21 13 21c4.97 0 9-4.03 9-9s-4.03-9-9-9zm-1 5v5l4.28 2.54.72-1.21-3.5-2.08V8H12z"
    const val Visualizer = "M4 18h2V6H4v12zm4 0h2V2H8v16zm4 0h2v-8h-2v8zm4 0h2V4h-2v14zm4 0h2v-6h-2v6z"
}

@Composable
fun Ico(path: String, tint: Color = LocalUi.current.text, sz: Dp = 24.dp, modifier: Modifier = Modifier) {
    val pth = remember(path) { runCatching { PathParser().parsePathString(path).toPath() }.getOrElse { Path() } }
    Canvas(modifier.size(sz)) {
        val k = this.size.width / 24f
        withTransform({ scale(k, k, Offset.Zero) }) { drawPath(pth, tint) }
    }
}

@Composable
fun IconBtn(
    path: String,
    desc: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = LocalUi.current.text,
    sz: Dp = 24.dp
) {
    Box(
        modifier
            .size(48.dp)
            .clip(CircleShape)
            .semantics { contentDescription = desc }
            .themeClickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Ico(path, tint, sz)
    }
}

@Composable
fun Chip(
    t: String,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    val u = LocalUi.current
    val s = RoundedCornerShape(50)
    Box(
        Modifier
            .heightIn(min = 40.dp)
            .clip(s)
            .then(
                if (selected) Modifier.background(u.accent)
                else Modifier.background(Brush.verticalGradient(listOf(u.glassHi, u.glassLo))).border(1.dp, u.border, s)
            )
            .semantics { this.selected = selected }
            .themeClickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            t,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = if (selected) u.onAccent else u.text
        )
    }
}

@Composable
fun artShape(r: Dp): Shape = when (LocalArtShape.current) {
    1 -> RoundedCornerShape(4.dp)
    2 -> CircleShape
    else -> RoundedCornerShape(r)
}

@Composable
fun Art(
    model: Any?,
    seed: String,
    size: Dp,
    radius: Dp = 12.dp,
    plain: Boolean = false,
    modifier: Modifier = Modifier,
    shape: Shape? = null
) {
    val ctx = LocalContext.current
    val sh = shape ?: if (plain) RoundedCornerShape(0.dp) else RoundedCornerShape(radius)
    val hue = seed.hashCode().mod(360).toFloat()
    val req = remember(model) { ImageRequest.Builder(ctx).data(model).crossfade(true).build() }
    Box(
        modifier
            .size(size)
            .clip(sh)
            .background(
                Brush.linearGradient(
                    listOf(
                        Color.hsv(hue, 0.45f, 0.42f),
                        Color.hsv((hue + 40f) % 360f, 0.5f, 0.22f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Ico(P.Note, Color.White.copy(0.55f), size * 0.45f)
        AsyncImage(req, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

@Composable
fun ArtFill(model: Any?, seed: String, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val hue = seed.hashCode().mod(360).toFloat()
    val req = remember(model) { ImageRequest.Builder(ctx).data(model).crossfade(true).build() }
    Box(
        modifier.background(
            Brush.linearGradient(
                listOf(
                    Color.hsv(hue, 0.45f, 0.42f),
                    Color.hsv((hue + 40f) % 360f, 0.5f, 0.22f)
                )
            )
        ),
        contentAlignment = Alignment.Center
    ) {
        Ico(P.Note, Color.White.copy(0.5f), 40.dp)
        AsyncImage(req, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

@Composable
fun GlassCircleBtn(
    path: String,
    desc: String,
    onClick: () -> Unit,
    tint: Color = LocalUi.current.text
) {
    val u = LocalUi.current
    Box(
        Modifier
            .size(48.dp)
            .semantics { contentDescription = desc }
            .clip(CircleShape)
            .themeClickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(u.glassHi, u.glassLo)))
                .border(1.dp, u.border, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Ico(path, tint, 20.dp)
        }
    }
}

class ArtPalette(val accent: Color, val top: Color, val mid: Color, val bottom: Color)

private val palCache = LruCache<String, ArtPalette>(48)

suspend fun artPalette(ctx: Context, model: Any?): ArtPalette? {
    if (model == null) return null
    val key = model.toString()
    palCache.get(key)?.let { return it }
    val res = try {
        ctx.imageLoader.execute(
            ImageRequest.Builder(ctx)
                .data(model)
                .size(48)
                .allowHardware(false)
                .build()
        )
    } catch (e: Exception) { return null }
    val bmp = ((res as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap ?: return null
    val p = withContext(Dispatchers.Default) { computePalette(bmp) } ?: return null
    palCache.put(key, p)
    return p
}

private fun computePalette(b: Bitmap): ArtPalette? {
    val w = b.width
    val h = b.height
    if (w == 0 || h == 0) return null
    val px = IntArray(w * h)
    b.getPixels(px, 0, w, 0, 0, w, h)
    val hsv = FloatArray(3)
    val wSum = FloatArray(12)
    val hSum = FloatArray(12)
    val sSum = FloatArray(12)
    val vSum = FloatArray(12)
    for (c in px) {
        if (android.graphics.Color.alpha(c) < 128) continue
        android.graphics.Color.colorToHSV(c, hsv)
        if (hsv[1] < 0.18f || hsv[2] < 0.15f) continue
        val bin = (hsv[0] / 30f).toInt().coerceIn(0, 11)
        val wt = hsv[1] * hsv[2]
        wSum[bin] += wt
        hSum[bin] += hsv[0] * wt
        sSum[bin] += hsv[1] * wt
        vSum[bin] += hsv[2] * wt
    }
    var best = -1
    for (i in 0 until 12) {
        if (wSum[i] > 0f && (best < 0 || wSum[i] > wSum[best])) best = i
    }
    if (best < 0) return null
    val hue = hSum[best] / wSum[best]
    val sat = (sSum[best] / wSum[best]).coerceIn(0.45f, 0.90f)
    val v = (vSum[best] / wSum[best]).coerceIn(0.70f, 0.95f)
    return ArtPalette(
        accent = Color.hsv(hue, sat, v),
        top = Color.hsv(hue, sat * 0.85f, 0.55f),
        mid = Color.hsv(hue, sat * 0.80f, 0.32f),
        bottom = Color.hsv(hue, sat * 0.70f, 0.14f)
    )
}
