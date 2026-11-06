package com.offline.player

import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import kotlin.math.roundToInt

@Composable
fun EqualizerSheet(app: AppState, onDismiss: () -> Unit) {
    val eq = app.eq
    val u = LocalUi.current
    val bandNames = listOf("60 Hz", "230 Hz", "910 Hz", "3.6 kHz", "14 kHz")
    val bandDesc = listOf("Bass", "Low Mid", "Mid", "High Mid", "Treble")

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .fillMaxHeight(0.88f)
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(if (u.light) Color(0xFFFAFAFC) else Color(0xFF141518))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {}
                    .padding(top = 10.dp, bottom = 16.dp, start = 18.dp, end = 18.dp)
            ) {
                // Top drag pill
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 38.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(u.dim.copy(alpha = 0.35f))
                )

                Spacer(Modifier.height(10.dp))

                // Header with Title, hardware indicator and Close Button
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Audio Equalizer",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = u.text
                            )
                            if (eq.enabled) {
                                Spacer(Modifier.width(8.dp))
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(u.accent.copy(alpha = 0.18f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        "ON",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = u.accentText
                                    )
                                }
                            }
                        }
                        Text(
                            if (eq.isSupported) "Hardware DSP Audio Effects Engine" else "Hardware effects not supported on device",
                            fontSize = 11.sp,
                            color = if (eq.isSupported) u.dim else Color(0xFFFF6B6B)
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = eq.enabled,
                            onCheckedChange = { eq.setEnabledState(it) },
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = u.accent,
                                checkedThumbColor = u.onAccent
                            )
                        )
                        Spacer(Modifier.width(8.dp))
                        IconBtn(path = P.Close, desc = "Close", onClick = onDismiss, sz = 20.dp)
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Scrollable Body - weight(1f) ensures it fits viewport and scrolls smoothly on any screen height!
                Column(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Presets Horizontal Row
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Sound Presets",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = u.text
                            )
                            Text(
                                eq.presets.getOrElse(eq.presetIndex) { "Custom" },
                                fontSize = 12.sp,
                                color = u.accentText,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = 2.dp)
                        ) {
                            itemsIndexed(eq.presets) { index, name ->
                                val isSelected = eq.presetIndex == index
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(if (isSelected) u.accent else u.glassHi)
                                        .border(
                                            1.dp,
                                            if (isSelected) u.accent else u.border,
                                            RoundedCornerShape(20.dp)
                                        )
                                        .clickable(enabled = eq.enabled) {
                                            eq.setPreset(index)
                                        }
                                        .padding(horizontal = 14.dp, vertical = 7.dp)
                                ) {
                                    Text(
                                        name,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) u.onAccent else if (eq.enabled) u.text else u.dim
                                    )
                                }
                            }
                        }
                    }

                    // Visual EQ Response Curve Canvas
                    val levels = eq.bandLevels
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(u.glassLo)
                            .border(1.dp, u.border, RoundedCornerShape(16.dp))
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Canvas(Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height
                            val midY = h / 2f

                            // Center 0dB reference line
                            drawLine(
                                color = Color.Gray.copy(alpha = 0.25f),
                                start = Offset(0f, midY),
                                end = Offset(w, midY),
                                strokeWidth = 1.dp.toPx()
                            )

                            if (levels.isNotEmpty()) {
                                val stepX = w / (levels.size - 1).coerceAtLeast(1)
                                val points = levels.mapIndexed { idx, lvl ->
                                    val normY = midY - (lvl / 10f) * (midY * 0.80f)
                                    Offset(idx * stepX, normY)
                                }

                                val path = Path().apply {
                                    moveTo(points[0].x, points[0].y)
                                    for (i in 0 until points.size - 1) {
                                        val p0 = points[i]
                                        val p1 = points[i + 1]
                                        val controlX = (p0.x + p1.x) / 2f
                                        cubicTo(controlX, p0.y, controlX, p1.y, p1.x, p1.y)
                                    }
                                }

                                // Fill under curve
                                val fillPath = Path().apply {
                                    addPath(path)
                                    lineTo(w, h)
                                    lineTo(0f, h)
                                    close()
                                }
                                drawPath(
                                    path = fillPath,
                                    brush = Brush.verticalGradient(
                                        listOf(
                                            u.accent.copy(alpha = if (eq.enabled) 0.35f else 0.1f),
                                            Color.Transparent
                                        )
                                    )
                                )

                                // Stroke curve line
                                drawPath(
                                    path = path,
                                    color = if (eq.enabled) u.accent else u.dim,
                                    style = Stroke(width = 2.5.dp.toPx())
                                )

                                // Draw control node dots
                                points.forEach { pt ->
                                    drawCircle(
                                        color = if (eq.enabled) u.accent else u.dim,
                                        radius = 4.dp.toPx(),
                                        center = pt
                                    )
                                    drawCircle(
                                        color = Color.White,
                                        radius = 2.dp.toPx(),
                                        center = pt
                                    )
                                }
                            }
                        }
                    }

                    // 5-Band Vertical Equalizer Section
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(u.glassHi)
                            .border(1.dp, u.border, RoundedCornerShape(20.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Equalizer Bands (-10 dB to +10 dB)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = u.text
                            )
                            TextButton(
                                onClick = { eq.setPreset(0) },
                                enabled = eq.enabled,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("Reset Flat", fontSize = 11.sp, color = u.accentText, fontWeight = FontWeight.Bold)
                            }
                        }

                        // 5 Sliders Row
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            for (i in 0 until 5) {
                                val currentLevel = levels.getOrElse(i) { 0 }
                                VerticalEqSlider(
                                    level = currentLevel,
                                    onLevelChange = { newLevel ->
                                        eq.setBand(i, newLevel)
                                    },
                                    frequency = bandNames.getOrElse(i) { "" },
                                    category = bandDesc.getOrElse(i) { "" },
                                    enabled = eq.enabled,
                                    accent = u.accent,
                                    textColor = u.text,
                                    dimColor = u.dim,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // Bass Boost & Virtualizer 3D Audio Cards
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Bass Boost Card
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(18.dp))
                                .background(u.glassHi)
                                .border(1.dp, u.border, RoundedCornerShape(18.dp))
                                .padding(12.dp)
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Bass Boost", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = u.text)
                                Text(
                                    "${eq.bassStrength / 10}%",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (eq.bassStrength > 0) u.accentText else u.dim
                                )
                            }
                            Text(
                                "Deep low-end punch",
                                fontSize = 10.sp,
                                color = u.dim
                            )
                            Spacer(Modifier.height(4.dp))
                            Slider(
                                value = eq.bassStrength.toFloat(),
                                onValueChange = { eq.setBass(it.toInt()) },
                                valueRange = 0f..1000f,
                                enabled = eq.enabled,
                                colors = SliderDefaults.colors(
                                    thumbColor = u.accent,
                                    activeTrackColor = u.accent,
                                    inactiveTrackColor = u.border
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            // Quick buttons
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                listOf(0 to "Off", 500 to "50%", 1000 to "Max").forEach { (v, lbl) ->
                                    val sel = (eq.bassStrength == v) || (v == 500 && eq.bassStrength in 400..600)
                                    Box(
                                        Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (sel) u.accent.copy(alpha = 0.2f) else Color.Transparent)
                                            .clickable(enabled = eq.enabled) { eq.setBass(v) }
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            lbl,
                                            fontSize = 10.sp,
                                            color = if (sel) u.accentText else u.dim,
                                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }

                        // Virtualizer 3D Card
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(18.dp))
                                .background(u.glassHi)
                                .border(1.dp, u.border, RoundedCornerShape(18.dp))
                                .padding(12.dp)
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Virtualizer", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = u.text)
                                Text(
                                    "${eq.virtualizerStrength / 10}%",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (eq.virtualizerStrength > 0) u.accentText else u.dim
                                )
                            }
                            Text(
                                "3D spatial soundstage",
                                fontSize = 10.sp,
                                color = u.dim
                            )
                            Spacer(Modifier.height(4.dp))
                            Slider(
                                value = eq.virtualizerStrength.toFloat(),
                                onValueChange = { eq.setVirtualizer(it.toInt()) },
                                valueRange = 0f..1000f,
                                enabled = eq.enabled,
                                colors = SliderDefaults.colors(
                                    thumbColor = u.accent,
                                    activeTrackColor = u.accent,
                                    inactiveTrackColor = u.border
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            // Quick buttons
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                listOf(0 to "Off", 500 to "50%", 1000 to "3D").forEach { (v, lbl) ->
                                    val sel = (eq.virtualizerStrength == v) || (v == 500 && eq.virtualizerStrength in 400..600)
                                    Box(
                                        Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (sel) u.accent.copy(alpha = 0.2f) else Color.Transparent)
                                            .clickable(enabled = eq.enabled) { eq.setVirtualizer(v) }
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            lbl,
                                            fontSize = 10.sp,
                                            color = if (sel) u.accentText else u.dim,
                                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Bottom Action Button: Done
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = u.accent,
                        contentColor = u.onAccent
                    )
                ) {
                    Text("Apply & Close", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}

/**
 * Interactive vertical equalizer slider with touch and drag gestures,
 * center 0dB tick indicator, and glowing thumb knob.
 */
@Composable
fun VerticalEqSlider(
    level: Int,
    onLevelChange: (Int) -> Unit,
    frequency: String,
    category: String,
    enabled: Boolean,
    accent: Color,
    textColor: Color,
    dimColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // dB display badge on top
        Text(
            text = if (level > 0) "+$level" else "$level",
            fontSize = 11.sp,
            fontWeight = if (level != 0) FontWeight.Bold else FontWeight.Normal,
            color = if (level != 0 && enabled) accent else dimColor
        )

        // Interactive vertical track area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures { offset ->
                        val h = size.height.toFloat()
                        if (h > 0) {
                            val norm = 1f - (offset.y / h).coerceIn(0f, 1f)
                            val newLevel = (-10 + norm * 20).roundToInt().coerceIn(-10, 10)
                            onLevelChange(newLevel)
                        }
                    }
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectDragGestures { change, _ ->
                        change.consume()
                        val h = size.height.toFloat()
                        if (h > 0) {
                            val norm = 1f - (change.position.y / h).coerceIn(0f, 1f)
                            val newLevel = (-10 + norm * 20).roundToInt().coerceIn(-10, 10)
                            onLevelChange(newLevel)
                        }
                    }
                }
                .semantics {
                    contentDescription = "$frequency band: $level dB"
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val midY = h / 2f
                val trackWidth = 6.dp.toPx()
                val centerX = w / 2f

                // Track Background
                drawRoundRect(
                    color = Color.Gray.copy(alpha = 0.2f),
                    topLeft = Offset(centerX - trackWidth / 2f, 0f),
                    size = androidx.compose.ui.geometry.Size(trackWidth, h),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackWidth / 2f)
                )

                // 0dB Center horizontal tick
                drawLine(
                    color = Color.Gray.copy(alpha = 0.45f),
                    start = Offset(centerX - 8.dp.toPx(), midY),
                    end = Offset(centerX + 8.dp.toPx(), midY),
                    strokeWidth = 1.5.dp.toPx()
                )

                // Thumb Y calculation (level: -10 to +10)
                val thumbY = midY - (level / 10f) * (midY - 10.dp.toPx())

                // Active track from midY to thumbY
                val fillTop = minOf(midY, thumbY)
                val fillHeight = kotlin.math.abs(thumbY - midY)
                if (fillHeight > 0) {
                    drawRoundRect(
                        color = if (enabled) accent else dimColor,
                        topLeft = Offset(centerX - trackWidth / 2f, fillTop),
                        size = androidx.compose.ui.geometry.Size(trackWidth, fillHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackWidth / 2f)
                    )
                }

                // Thumb Knob
                val thumbRadius = 9.dp.toPx()
                drawCircle(
                    color = (if (enabled) accent else dimColor).copy(alpha = 0.25f),
                    radius = thumbRadius + 3.dp.toPx(),
                    center = Offset(centerX, thumbY)
                )
                drawCircle(
                    color = if (enabled) accent else dimColor,
                    radius = thumbRadius,
                    center = Offset(centerX, thumbY)
                )
                drawCircle(
                    color = Color.White,
                    radius = 3.5.dp.toPx(),
                    center = Offset(centerX, thumbY)
                )
            }
        }

        // Frequency Label & Category
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = frequency,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
            Text(
                text = category,
                fontSize = 9.sp,
                color = dimColor
            )
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
                    .background(if (u.light) Color.White else Color(0xFF16171B))
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
        containerColor = if (u.light) Color.White else Color(0xFF18191E),
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
        containerColor = if (u.light) Color.White else Color(0xFF18191E),
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

