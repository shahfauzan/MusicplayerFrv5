package com.offline.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun VideoScreen(
    app: AppState,
    onPlayVideo: (VideoItem, List<VideoItem>) -> Unit,
    onRequestPermission: () -> Unit
) {
    val ctx = LocalContext.current
    val u = LocalUi.current
    val ps = app.ps

    var videos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var folders by remember { mutableStateOf<List<VideoFolder>>(emptyList()) }
    var selectedFolder by remember { mutableStateOf<VideoFolder?>(null) }
    var subTab by remember { mutableIntStateOf(0) } // 0 Folders, 1 All Videos
    var isLoading by remember { mutableStateOf(true) }

    fun refresh() {
        isLoading = true
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).run {
            val list = VideoManager.loadVideos(ctx)
            val grouped = VideoManager.groupByFolder(list)
            videos = list
            folders = grouped
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val list = VideoManager.loadVideos(ctx)
            val grouped = VideoManager.groupByFolder(list)
            withContext(Dispatchers.Main) {
                videos = list
                folders = grouped
                isLoading = false
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        // ── Sub-header with Folder vs All tabs ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(
                    "Folders (${folders.size})",
                    selected = subTab == 0 && selectedFolder == null
                ) {
                    subTab = 0
                    selectedFolder = null
                }
                Chip(
                    "All Videos (${videos.size})",
                    selected = subTab == 1 && selectedFolder == null
                ) {
                    subTab = 1
                    selectedFolder = null
                }
            }

            if (selectedFolder != null) {
                Chip(
                    "← Back to Folders",
                    selected = true
                ) {
                    selectedFolder = null
                }
            }
        }

        when {
            isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Loading videos...", color = u.dim, fontSize = 14.sp)
                }
            }
            videos.isEmpty() -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(u.accent.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Ico(P.Folder, u.accentText, 32.dp)
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "No Videos Found",
                            color = u.text,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Ensure video permission is granted to browse and play device videos in background.",
                            color = u.dim,
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(Modifier.height(14.dp))
                        Chip("Grant Permission / Refresh", true, onClick = {
                            onRequestPermission()
                            refresh()
                        })
                    }
                }
            }
            selectedFolder != null -> {
                // Showing videos inside a specific folder
                val currentFolder = selectedFolder!!
                Column(Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                currentFolder.name,
                                color = u.text,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "${currentFolder.count} videos · ${currentFolder.path}",
                                color = u.dim,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(currentFolder.videos, key = { it.id }) { vid ->
                            VideoCardItem(
                                video = vid,
                                isPlaying = ps.current?.mediaId == "video_${vid.id}" && ps.playing,
                                onPlay = { onPlayVideo(vid, currentFolder.videos) },
                                onPlayBg = {
                                    val c = ps.controller ?: return@VideoCardItem
                                    c.setMediaItem(vid.toMediaItem())
                                    c.prepare()
                                    c.play()
                                }
                            )
                        }
                    }
                }
            }
            subTab == 0 -> {
                // Folders Grid View
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(folders, key = { it.path }) { folder ->
                        FolderCardItem(
                            folder = folder,
                            onClick = { selectedFolder = folder }
                        )
                    }
                }
            }
            else -> {
                // All Videos List View
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(videos, key = { it.id }) { vid ->
                        VideoCardItem(
                            video = vid,
                            isPlaying = ps.current?.mediaId == "video_${vid.id}" && ps.playing,
                            onPlay = { onPlayVideo(vid, videos) },
                            onPlayBg = {
                                val c = ps.controller ?: return@VideoCardItem
                                c.setMediaItem(vid.toMediaItem())
                                c.prepare()
                                c.play()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderCardItem(
    folder: VideoFolder,
    onClick: () -> Unit
) {
    val u = LocalUi.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .glass(18.dp)
            .border(1.dp, if (u.light) Color.Black.copy(0.08f) else Color.White.copy(0.16f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(105.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF1E2024)),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = folder.thumbUri,
                contentDescription = folder.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    "${folder.count} vids",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Ico(P.Folder, u.accentText, 18.dp)
            Spacer(Modifier.width(6.dp))
            Text(
                text = folder.name,
                color = u.text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun VideoCardItem(
    video: VideoItem,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onPlayBg: () -> Unit
) {
    val u = LocalUi.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .glass(16.dp)
            .border(
                1.dp,
                if (isPlaying) u.accent else if (u.light) Color.Black.copy(0.08f) else Color.White.copy(0.14f),
                RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onPlay)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail with 16:9 ratio and duration badge
        Box(
            modifier = Modifier
                .width(96.dp)
                .height(60.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF18191E)),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = video.uri,
                contentDescription = video.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Duration badge
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.8f))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    text = fmt(video.duration),
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (isPlaying) {
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    Ico(P.Play, u.accent, 16.dp)
                }
            }
        }

        Spacer(Modifier.width(12.dp))

        // Info
        Column(Modifier.weight(1f)) {
            Text(
                text = video.title,
                color = if (isPlaying) u.accentText else u.text,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (video.resolutionLabel.isNotBlank()) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(u.dim.copy(alpha = 0.15f))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            video.resolutionLabel,
                            color = u.text,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(
                    text = "${video.formattedSize} · ${video.folderName}",
                    color = u.dim,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        // Quick action: Play in background audio
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(u.accent.copy(alpha = 0.15f))
                .clickable(
                    onClickLabel = "Play in background audio",
                    onClick = onPlayBg
                ),
            contentAlignment = Alignment.Center
        ) {
            Ico(P.Headphones, u.accentText, 18.dp)
        }
    }
}
