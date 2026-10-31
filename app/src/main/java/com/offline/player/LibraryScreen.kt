package com.offline.player

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Fetches all music files from the device using Android MediaStore APIs.
 * Runs on [Dispatchers.IO] and returns a list of [Song] models.
 */
suspend fun queryMediaStoreAudioFiles(context: Context): List<Song> = withContext(Dispatchers.IO) {
    val songs = mutableListOf<Song>()
    val contentResolver = context.contentResolver
    val uri: Uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

    val pathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Audio.Media.RELATIVE_PATH
    } else {
        MediaStore.Audio.Media.DATA
    }

    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.ALBUM_ID,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.DATE_ADDED,
        MediaStore.Audio.Media.TRACK,
        MediaStore.Audio.Media.YEAR,
        MediaStore.Audio.Media.SIZE,
        pathColumn
    )

    val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
    val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

    try {
        contentResolver.query(uri, projection, selection, null, sortOrder)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val yearCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val pathCol = cursor.getColumnIndexOrThrow(pathColumn)

            while (cursor.moveToNext()) {
                val duration = cursor.getLong(durationCol)
                // Filter out notification chimes and ringtones shorter than 10 seconds
                if (duration < 10_000L) continue

                val id = cursor.getLong(idCol)
                val rawPath = cursor.getString(pathCol) ?: ""
                val folderPath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    rawPath.trimEnd('/')
                } else {
                    File(rawPath).parent ?: ""
                }
                val folderName = if (folderPath.isNotBlank()) {
                    File(folderPath).name.ifBlank { "Music" }
                } else "Music"

                val songTitle = cursor.getString(titleCol)?.takeIf { it.isNotBlank() } ?: "Unknown Title"
                val songArtist = cursor.getString(artistCol)?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "Unknown Artist"
                val songAlbum = cursor.getString(albumCol)?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "Unknown Album"
                val albumId = cursor.getLong(albumIdCol)
                val dateAdded = cursor.getLong(dateAddedCol)
                val trackNumber = cursor.getInt(trackCol)
                val releaseYear = cursor.getInt(yearCol)
                val fileSize = cursor.getLong(sizeCol)

                val contentUri = ContentUris.withAppendedId(uri, id)

                songs.add(
                    Song(
                        id = id,
                        title = songTitle,
                        artist = songArtist,
                        album = songAlbum,
                        albumId = albumId,
                        durationMs = duration,
                        uri = contentUri,
                        dateAdded = dateAdded,
                        track = trackNumber,
                        year = releaseYear,
                        folderPath = folderPath,
                        folderName = folderName,
                        sizeBytes = fileSize
                    )
                )
            }
        }
    } catch (_: SecurityException) {
        // Handled via permission state in UI
    } catch (_: Exception) {
        // Gracefully catch potential OEM MediaStore quirks
    }

    songs
}

/**
 * Returns required storage permission based on Android SDK level.
 */
fun getAudioStoragePermission(): String {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
}

/**
 * Checks whether audio storage permission is currently granted.
 */
fun isAudioStoragePermissionGranted(context: Context): Boolean {
    val permission = getAudioStoragePermission()
    return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

/**
 * Integrated overload of [LibraryScreen] for use with [AppState].
 */
@Composable
fun LibraryScreen(
    app: AppState,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null
) {
    val currentMediaId = app.ps.current?.mediaId
    val currentSongId = currentMediaId?.toLongOrNull()

    LibraryScreen(
        modifier = modifier,
        currentSongId = currentSongId,
        isPlaying = app.ps.playing,
        onSongClick = { song, playlist, index ->
            app.ps.playSongs(playlist, index)
        },
        onTogglePlay = {
            app.ps.toggle()
        },
        onBack = onBack
    )
}

/**
 * Primary [LibraryScreen] component.
 *
 * Fetches and displays local music files using Android's [MediaStore] APIs
 * and presents them in a performant, styled [LazyColumn] list.
 */
@Composable
fun LibraryScreen(
    modifier: Modifier = Modifier,
    currentSongId: Long? = null,
    isPlaying: Boolean = false,
    onSongClick: ((song: Song, playlist: List<Song>, index: Int) -> Unit)? = null,
    onTogglePlay: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val ui = LocalUi.current
    val focusManager = LocalFocusManager.current

    val requiredPermission = remember { getAudioStoragePermission() }
    var hasPermission by remember { mutableStateOf(isAudioStoragePermissionGranted(context)) }

    var rawSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var sortIndex by remember { mutableIntStateOf(0) } // 0: Title, 1: Artist, 2: Newest, 3: Duration

    val sortLabels = listOf("Title", "Artist", "Newest", "Duration")

    // Launcher for runtime media permissions
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    // Function to reload songs from MediaStore
    val reloadSongs: () -> Unit = {
        if (hasPermission) {
            isLoading = true
        }
    }

    // Query MediaStore when permission is granted or reload triggered
    LaunchedEffect(hasPermission, isLoading) {
        if (hasPermission) {
            isLoading = true
            val fetched = queryMediaStoreAudioFiles(context)
            rawSongs = fetched
            isLoading = false
        }
    }

    // Filter and sort the songs list
    val displayedSongs = remember(rawSongs, searchQuery, sortIndex) {
        val filtered = if (searchQuery.isBlank()) {
            rawSongs
        } else {
            val q = searchQuery.trim().lowercase()
            rawSongs.filter { s ->
                s.title.lowercase().contains(q) ||
                s.artist.lowercase().contains(q) ||
                s.album.lowercase().contains(q)
            }
        }

        when (sortIndex) {
            1 -> filtered.sortedWith(compareBy<Song>({ it.artist.lowercase() }, { it.title.lowercase() }))
            2 -> filtered.sortedWith(compareByDescending<Song> { it.dateAdded }.thenByDescending { it.id })
            3 -> filtered.sortedByDescending { it.durationMs }
            else -> filtered.sortedBy { it.title.lowercase() }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("library_screen")
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                IconBtn(
                    path = P.Back,
                    desc = "Back",
                    onClick = onBack,
                    modifier = Modifier.testTag("library_back_button")
                )
                Spacer(Modifier.width(8.dp))
            }

            Column(Modifier.weight(1f)) {
                Text(
                    text = "Music Library",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = ui.text
                )
                Text(
                    text = if (isLoading) "Scanning device..." else "${displayedSongs.size} tracks found",
                    fontSize = 12.sp,
                    color = ui.dim
                )
            }

            // Rescan button
            IconButton(
                onClick = {
                    if (hasPermission) {
                        isLoading = true
                    } else {
                        permissionLauncher.launch(requiredPermission)
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("library_rescan_button")
            ) {
                Ico(path = P.Repeat, tint = ui.accentText, sz = 20.dp)
            }
        }

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .testTag("library_search_input"),
            placeholder = { Text("Search songs, artists, albums...", color = ui.dim) },
            leadingIcon = {
                Ico(path = P.Search, tint = ui.dim, sz = 18.dp)
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { searchQuery = "" },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Ico(path = P.Close, tint = ui.dim, sz = 16.dp)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = ui.text,
                unfocusedTextColor = ui.text,
                cursorColor = ui.accent,
                focusedBorderColor = ui.accent,
                unfocusedBorderColor = ui.border,
                focusedContainerColor = ui.glassHi,
                unfocusedContainerColor = ui.glassLo
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
        )

        // Sorting Chips & Action Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            sortLabels.forEachIndexed { index, label ->
                val selected = sortIndex == index
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (selected) ui.accent else ui.glassHi)
                        .border(1.dp, if (selected) ui.accent else ui.border, RoundedCornerShape(16.dp))
                        .clickable { sortIndex = index }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .testTag("sort_chip_$label"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) ui.onAccent else ui.text
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // Quick Shuffle Button
            if (displayedSongs.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(ui.accent.copy(alpha = 0.15f))
                        .border(1.dp, ui.accent.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                        .clickable {
                            val randomIndex = displayedSongs.indices.random()
                            onSongClick?.invoke(displayedSongs[randomIndex], displayedSongs, randomIndex)
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("library_shuffle_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Ico(path = P.Shuffle, tint = ui.accentText, sz = 14.dp)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Shuffle",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ui.accentText
                        )
                    }
                }
            }
        }

        // Main Content Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when {
                // Case 1: Permission Not Granted
                !hasPermission -> {
                    PermissionRequiredCard(
                        requiredPermission = requiredPermission,
                        onRequest = { permissionLauncher.launch(requiredPermission) },
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp)
                    )
                }

                // Case 2: Loading State
                isLoading && rawSongs.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = ui.accent,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Reading audio files from storage...",
                            fontSize = 13.sp,
                            color = ui.dim
                        )
                    }
                }

                // Case 3: Empty List
                displayedSongs.isEmpty() -> {
                    EmptyLibraryCard(
                        isFiltered = searchQuery.isNotBlank(),
                        onClearSearch = { searchQuery = "" },
                        onRescan = { reloadSongs() },
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp)
                    )
                }

                // Case 4: Songs List
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("library_list"),
                        contentPadding = PaddingValues(bottom = 96.dp)
                    ) {
                        itemsIndexed(
                            items = displayedSongs,
                            key = { _, song -> song.id }
                        ) { index, song ->
                            val isCurrent = currentSongId == song.id
                            LibrarySongRow(
                                song = song,
                                isCurrent = isCurrent,
                                isPlaying = isPlaying,
                                onClick = {
                                    if (isCurrent && onTogglePlay != null) {
                                        onTogglePlay()
                                    } else {
                                        onSongClick?.invoke(song, displayedSongs, index)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Individual song row inside [LibraryScreen]'s LazyColumn.
 */
@Composable
fun LibrarySongRow(
    song: Song,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ui = LocalUi.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (isCurrent) ui.accent.copy(alpha = 0.18f) else Color.Transparent)
            .clickable(
                onClickLabel = if (isCurrent) "Toggle playback of ${song.title}" else "Play ${song.title}",
                onClick = onClick
            )
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag("library_song_item_${song.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Album Artwork
        Art(
            model = song.art,
            seed = song.title,
            size = 46.dp,
            radius = 10.dp
        )

        Spacer(Modifier.width(12.dp))

        // Title and Artist Info
        Column(Modifier.weight(1f)) {
            Text(
                text = song.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 14.sp,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                color = if (isCurrent) ui.accentText else ui.text
            )
            Text(
                text = "${song.artist} • ${song.album.ifBlank { song.folderName }}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 12.sp,
                color = ui.dim
            )
        }

        Spacer(Modifier.width(8.dp))

        // Duration text
        Text(
            text = fmt(song.durationMs),
            fontSize = 12.sp,
            color = ui.dim
        )

        Spacer(Modifier.width(8.dp))

        // Play/Pause Action Indicator Button
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .semantics {
                    contentDescription = if (isCurrent && isPlaying) "Pause ${song.title}" else "Play ${song.title}"
                }
                .clickable(role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(if (isCurrent) ui.accent else ui.glassHi)
                    .border(1.dp, if (isCurrent) ui.accent else ui.border, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Ico(
                    path = if (isCurrent && isPlaying) P.Pause else P.Play,
                    tint = if (isCurrent) ui.onAccent else ui.text,
                    sz = 16.dp
                )
            }
        }
    }
}

/**
 * Displayed when storage / audio permissions are not yet granted.
 */
@Composable
private fun PermissionRequiredCard(
    requiredPermission: String,
    onRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ui = LocalUi.current

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = ui.glassLo),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(ui.border))
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(ui.accent.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Ico(path = P.Note, tint = ui.accentText, sz = 28.dp)
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Permission Needed",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = ui.text
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "To browse and play audio files stored on this device, please allow access to your media files.",
                fontSize = 13.sp,
                color = ui.dim,
                lineHeight = 18.sp
            )

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = onRequest,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ui.accent,
                    contentColor = ui.onAccent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("grant_permission_button")
            ) {
                Text(
                    text = "Grant Audio Permission",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

/**
 * Displayed when no matching songs are found in storage or after search.
 */
@Composable
private fun EmptyLibraryCard(
    isFiltered: Boolean,
    onClearSearch: () -> Unit,
    onRescan: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ui = LocalUi.current

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(ui.glassHi)
                .border(1.dp, ui.border, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Ico(path = if (isFiltered) P.Search else P.Note, tint = ui.dim, sz = 28.dp)
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = if (isFiltered) "No results found" else "No audio files detected",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = ui.text
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = if (isFiltered) {
                "Try searching for another artist, title, or folder name."
            } else {
                "Make sure your music files are placed in standard folders (like Music, Downloads, or Internal Storage)."
            },
            fontSize = 13.sp,
            color = ui.dim,
            lineHeight = 18.sp
        )

        Spacer(Modifier.height(16.dp))

        if (isFiltered) {
            TextButton(
                onClick = onClearSearch,
                modifier = Modifier.height(48.dp)
            ) {
                Text(
                    text = "Clear Search",
                    color = ui.accentText,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            Button(
                onClick = onRescan,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ui.accent,
                    contentColor = ui.onAccent
                ),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("empty_rescan_button")
            ) {
                Text(
                    text = "Rescan Device",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
