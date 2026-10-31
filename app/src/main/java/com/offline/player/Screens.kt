package com.offline.player

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed as gridItemsIndexed
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val GOLD_PLAYS = 20
private const val RECENT_COUNT = 30

private class HomeCard(
    val key: String, val title: String, val subtitle: String,
    val art: Any?, val gold: Boolean, val open: () -> Unit
)

private val homeChips = listOf("All", "Trending", "Recent", "New Artist", "New Releases")

@Composable
private fun dialogColor(): Color = if (LocalUi.current.light) Color.White else Color(0xFF1E1E2A)

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val u = LocalUi.current
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = dialogColor(),
        titleContentColor = u.text, textContentColor = u.dim,
        title = { Text(title) }, text = { Text(text) },
        confirmButton = { TextButton(onConfirm) { Text(confirm, color = u.accentText, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel", color = u.dim) } }
    )
}

@Composable
fun NameDialog(title: String, initial: String, confirm: String = "Save", onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    val u = LocalUi.current
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = dialogColor(), titleContentColor = u.text,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                name, { name = it.take(60) }, singleLine = true, label = { Text("Name") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = u.text, unfocusedTextColor = u.text, cursorColor = u.accent,
                    focusedBorderColor = u.accent, unfocusedBorderColor = u.border,
                    focusedLabelColor = u.accentText, unfocusedLabelColor = u.dim
                )
            )
        },
        confirmButton = { TextButton({ if (name.isNotBlank()) onConfirm(name) }, enabled = name.isNotBlank()) { Text(confirm, color = u.accentText, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel", color = u.dim) } }
    )
}

@Composable
fun AddToPlaylistDialog(app: AppState) {
    val songs = app.pickFor ?: return
    val u = LocalUi.current
    val ctx = LocalContext.current
    var creating by remember { mutableStateOf(false) }
    val label = if (songs.size == 1) "“${songs[0].title}”" else "${songs.size} tracks"

    if (creating) {
        NameDialog("New playlist", "", "Create", { name ->
            app.pls.create(name, songs.map { it.id })
            Toast.makeText(ctx, "Added $label to $name", Toast.LENGTH_SHORT).show()
            creating = false
            app.pickFor = null
        }, { creating = false })
        return
    }

    AlertDialog(
        onDismissRequest = { app.pickFor = null }, containerColor = dialogColor(), titleContentColor = u.text,
        title = { Text("Add to playlist") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                TextButton({ creating = true }, Modifier.fillMaxWidth()) {
                    Text("＋ Create New Playlist", color = u.accentText, fontWeight = FontWeight.Bold)
                }
                app.pls.lists.forEach { pl ->
                    TextButton({
                        val n = app.pls.add(pl.id, songs.map { it.id })
                        Toast.makeText(ctx, if (n == 0) "Already in ${pl.name}" else "Added $n songs to ${pl.name}", Toast.LENGTH_SHORT).show()
                        app.pickFor = null
                    }, Modifier.fillMaxWidth()) {
                        Text("${pl.name}  ·  ${pl.songIds.size} songs", color = u.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton({ app.pickFor = null }) { Text("Cancel", color = u.dim) } }
    )
}

@Composable
fun SongPickerDialog(app: AppState, existing: Set<Long>, onAdd: (List<Long>) -> Unit, onDismiss: () -> Unit) {
    val u = LocalUi.current
    var q by remember { mutableStateOf("") }
    var sel by remember { mutableStateOf(setOf<Long>()) }
    val all = remember(app.songs, existing) { sortSongs(app.songs.filter { it.id !in existing }, 0) }
    val shown = remember(all, q) {
        val k = q.trim().lowercase()
        if (k.isEmpty()) all else all.filter { it.key.contains(k) }
    }

    AlertDialog(
        onDismissRequest = onDismiss, containerColor = dialogColor(), titleContentColor = u.text,
        title = { Text("Add songs to playlist") },
        text = {
            Column {
                SearchField(q, { q = it })
                Spacer(Modifier.height(8.dp))
                if (shown.isEmpty()) Text("No songs to add.", color = u.dim)
                LazyColumn(Modifier.heightIn(max = 340.dp)) {
                    items(shown, key = { it.id }) { s ->
                        val on = s.id in sel
                        Row(
                            Modifier.fillMaxWidth().themeClickable(onClickLabel = if (on) "Deselect ${s.title}" else "Select ${s.title}") {
                                sel = if (on) sel - s.id else sel + s.id
                            }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(on, null, colors = CheckboxDefaults.colors(checkedColor = u.accent, checkmarkColor = u.onAccent, uncheckedColor = u.dim))
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = u.text)
                                Text(s.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = u.dim)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton({ onAdd(sel.toList()) }, enabled = sel.isNotEmpty()) { Text("Add ${sel.size}", color = u.accentText, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel", color = u.dim) } }
    )
}

@Composable
fun SearchField(q: String, onQ: (String) -> Unit, modifier: Modifier = Modifier) {
    val u = LocalUi.current
    OutlinedTextField(
        value = q,
        onValueChange = onQ,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text("Search songs, artists, albums, folders...", color = u.dim, fontSize = 14.sp) },
        leadingIcon = { Ico(P.Search, u.dim) },
        trailingIcon = { if (q.isNotEmpty()) IconBtn(P.Close, "Clear search", { onQ("") }, tint = u.dim) },
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = u.accent, unfocusedBorderColor = u.border,
            focusedTextColor = u.text, unfocusedTextColor = u.text, cursorColor = u.accent,
            focusedContainerColor = u.glassLo, unfocusedContainerColor = u.glassLo
        )
    )
}

@Composable
fun SongMenu(app: AppState, s: Song, extras: List<Pair<String, () -> Unit>> = emptyList()) {
    val ctx = LocalContext.current
    var open by remember { mutableStateOf(false) }
    var tagDialog by remember { mutableStateOf(false) }
    val fav = s.id in app.prefs.favs

    Box {
        IconBtn(P.More, "More options for ${s.title}", { open = true }, tint = LocalUi.current.dim)
        DropdownMenu(open, { open = false }) {
            DropdownMenuItem(text = { Text("Play next") }, onClick = {
                open = false
                app.ps.playNext(s)
                Toast.makeText(ctx, "Playing next: ${s.title}", Toast.LENGTH_SHORT).show()
            })
            DropdownMenuItem(text = { Text("Add to queue") }, onClick = {
                open = false
                app.ps.addToQueue(s)
                Toast.makeText(ctx, "Added to queue: ${s.title}", Toast.LENGTH_SHORT).show()
            })
            DropdownMenuItem(text = { Text("Add to playlist") }, onClick = {
                open = false
                app.pickFor = listOf(s)
            })
            DropdownMenuItem(text = { Text(if (fav) "Remove from favorites" else "Add to favorites") }, onClick = {
                open = false
                app.prefs.toggleFav(s.id)
            })
            DropdownMenuItem(text = { Text("Edit tags / info") }, onClick = {
                open = false
                tagDialog = true
            })
            DropdownMenuItem(text = { Text(if (app.vault.isHidden(s.id)) "Unhide from Vault" else "Hide in Private Vault") }, onClick = {
                open = false
                app.vault.toggleHide(s.id)
                app.refreshHiddenState()
                Toast.makeText(ctx, if (app.vault.isHidden(s.id)) "Moved to Private Vault" else "Restored to Library", Toast.LENGTH_SHORT).show()
            })
            extras.forEach { (label, f) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { open = false; f() })
            }
        }
    }

    if (tagDialog) {
        TagEditorDialog(app, s) { tagDialog = false }
    }
}

@Composable
fun Collage(songs: List<Song>, sz: Dp) {
    val arts = remember(songs) { songs.distinctBy { it.albumId }.take(4) }
    if (arts.size < 4) {
        Art(arts.firstOrNull()?.art, songs.firstOrNull()?.title ?: "Playlist", sz, 12.dp)
    } else {
        Column(Modifier.size(sz).clip(RoundedCornerShape(12.dp))) {
            for (r in 0..1) Row {
                for (c in 0..1) {
                    val a = arts[r * 2 + c]
                    Art(a.art, a.title, sz / 2, 0.dp, plain = true)
                }
            }
        }
    }
}

@Composable
fun Message(text: String, action: (@Composable () -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text, color = LocalUi.current.dim, fontSize = 15.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(14.dp))
            action()
        }
    }
}

// ───────────────────────── Home Screen ─────────────────────────

@Composable
fun HomeScreen(
    app: AppState,
    chip: Int,
    onChip: (Int) -> Unit,
    loaded: Boolean,
    granted: Boolean,
    onRequest: () -> Unit,
    onSettings: () -> Unit,
    onOpen: (Detail) -> Unit,
    onSearch: () -> Unit,
    onLiked: () -> Unit,
    onHistory: () -> Unit,
    onEqualizer: () -> Unit,
    onProfile: () -> Unit
) {
    val u = LocalUi.current
    val ps = app.ps
    val plays = app.history.plays

    val cards = remember(chip, app.songs, app.pls.lists, plays) {
        fun total(l: List<Song>) = l.sumOf { plays[it.id] ?: 0 }
        fun songCards(list: List<Song>) = list.mapIndexed { i, s ->
            HomeCard("s${s.id}", s.title, s.artist, s.art, false) { ps.playSongs(list, i) }
        }
        when (chip) {
            1 -> songCards(app.songs.filter { (plays[it.id] ?: 0) > 0 }.sortedByDescending { plays[it.id] ?: 0 }.take(RECENT_COUNT))
            2 -> songCards(app.songs.sortedWith(compareByDescending<Song> { it.dateAdded }.thenByDescending { it.id }).take(RECENT_COUNT))
            3 -> app.songs.groupBy { it.artist }.entries
                .sortedByDescending { e -> e.value.minOf { it.dateAdded } }.take(RECENT_COUNT)
                .map { (name, l) -> HomeCard("r$name", name, "${l.size} songs", l.first().art, total(l) >= GOLD_PLAYS) { onOpen(Detail.Artist(name)) } }
            4 -> app.songs.groupBy { it.albumId }.entries
                .sortedWith(compareByDescending<Map.Entry<Long, List<Song>>> { e -> e.value.maxOf { it.year } }.thenByDescending { e -> e.value.maxOf { it.dateAdded } })
                .take(RECENT_COUNT)
                .map { (id, l) -> HomeCard("a$id", l.first().album.ifBlank { "Unknown album" }, "${l.size} songs", l.first().art, total(l) >= GOLD_PLAYS) { onOpen(Detail.Album(id)) } }
            else -> {
                val pls = app.pls.lists.map { pl ->
                    val l = pl.songIds.mapNotNull { app.byId[it] }
                    HomeCard("p${pl.id}", pl.name, "${l.size} songs", l.firstOrNull()?.art, total(l) >= GOLD_PLAYS) { onOpen(Detail.PL(pl.id)) }
                }
                val albums = app.songs.groupBy { it.albumId }.map { (id, l) ->
                    HomeCard("a$id", l.first().album.ifBlank { "Unknown album" }, "${l.size} songs", l.first().art, total(l) >= GOLD_PLAYS) { onOpen(Detail.Album(id)) }
                }.sortedBy { it.title.lowercase() }
                pls + albums
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        // App Bar with Quick Actions
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(app, onProfile)
            Spacer(Modifier.weight(1f))
            GlassCircleBtn(P.Search, "Search music", onSearch)
            Spacer(Modifier.width(6.dp))
            GlassCircleBtn(P.HeartOutline, "Liked songs", onLiked)
            Spacer(Modifier.width(6.dp))
            GlassCircleBtn(P.Equalizer, "Equalizer", onEqualizer)
            Spacer(Modifier.width(6.dp))
            GlassCircleBtn(P.History, "Listening stats", onHistory)
        }

        Text(
            if (app.prefs.userName.isBlank()) "Welcome to FR Player" else "Hello, ${app.prefs.userName}",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = u.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(homeChips) { i, n ->
                Chip(n, chip == i) { onChip(i) }
            }
        }

        when {
            !granted -> Message("Allow music permission to find and play your local audio files.") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Grant access", true, onRequest)
                    Chip("App settings", onClick = onSettings)
                }
            }
            cards.isEmpty() && loaded -> Message(
                when {
                    app.songs.isEmpty() -> "No audio files detected on this device."
                    chip == 1 -> "No trending tracks yet. Songs played for 30s+ show here."
                    else -> "No songs match this category."
                }
            )
            else -> LazyVerticalStaggeredGrid(
                StaggeredGridCells.Fixed(2),
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
                verticalItemSpacing = 12.dp,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                gridItemsIndexed(cards, key = { _, c -> c.key }) { i, c ->
                    val h = listOf(220, 165, 185, 210, 165, 195)[i % 6]
                    HomeTile(c, h)
                }
            }
        }
    }
}

@Composable
private fun HomeTile(c: HomeCard, heightDp: Int) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(heightDp.dp)
            .clip(RoundedCornerShape(22.dp))
            .themeClickable(onClickLabel = "Open ${c.title}", onClick = c.open)
    ) {
        ArtFill(c.art, c.title, Modifier.fillMaxSize())
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(96.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.75f))))
        )
        if (c.gold) {
            val pill = RoundedCornerShape(50)
            Text(
                "Gold Record", fontSize = 11.sp, color = Color.White,
                modifier = Modifier.padding(10.dp).clip(pill).background(Color.Black.copy(0.40f))
                    .border(1.dp, Color.White.copy(0.5f), pill).padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
        Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
            Text(c.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(c.subtitle, fontSize = 11.sp, color = Color.White.copy(0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Avatar(app: AppState, onClick: () -> Unit) {
    val u = LocalUi.current
    val name = app.prefs.userName
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(u.accent)
            .semantics { contentDescription = "Profile avatar. Opens settings." }
            .themeClickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (app.prefs.avatarRev > 0) {
            AsyncImage(app.prefs.avatarFile, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Text(name.take(1).uppercase().ifBlank { "FR" }, color = u.onAccent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ───────────────────────── My Music Screen ─────────────────────────

private val musicChips = listOf("All", "Albums", "Artists", "Playlists", "Folders", "Downloads", "Liked", "Vault")

class AlbumItem(val id: Long, val name: String, val artist: String, val songs: List<Song>)

@Composable
fun MyMusicScreen(
    app: AppState,
    results: List<Song>,
    query: String,
    onQuery: (String) -> Unit,
    chip: Int,
    onChip: (Int) -> Unit,
    searchOn: Boolean,
    onSearchToggle: () -> Unit,
    loaded: Boolean,
    granted: Boolean,
    onRequest: () -> Unit,
    onSettings: () -> Unit,
    onBack: () -> Unit,
    onLiked: () -> Unit,
    onOpen: (Detail) -> Unit
) {
    val u = LocalUi.current
    val ps = app.ps
    val favs = app.prefs.favs
    var showVaultAuth by remember { mutableStateOf(false) }

    val list = remember(results, chip, favs) {
        when (chip) {
            5 -> results.filter { it.inDownloads }
            6 -> results.filter { it.id in favs }
            else -> results
        }
    }
    val liked = chip == 6

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassCircleBtn(P.ChevronLeft, "Back to Home", onBack)
            Text(
                "My Music",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = u.text,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            GlassCircleBtn(if (searchOn) P.Close else P.Search, if (searchOn) "Close search" else "Search", onSearchToggle)
            Spacer(Modifier.width(8.dp))
            GlassCircleBtn(if (liked) P.Heart else P.HeartOutline, "Liked songs", onLiked, tint = if (liked) u.accent else u.text)
        }

        if (searchOn) {
            SearchField(query, onQuery, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(musicChips) { i, n ->
                Chip(n, chip == i) {
                    if (i == 7) {
                        showVaultAuth = true
                    } else {
                        onChip(i)
                    }
                }
            }
            if (chip == 0 || chip == 5 || chip == 6) item {
                var sortOpen by remember { mutableStateOf(false) }
                Box {
                    Chip("Sort: ${sorts[app.prefs.sort]}") { sortOpen = true }
                    DropdownMenu(sortOpen, { sortOpen = false }) {
                        sorts.forEachIndexed { i, n ->
                            DropdownMenuItem(text = { Text(n) }, onClick = {
                                sortOpen = false
                                app.prefs.setSort(i)
                            })
                        }
                    }
                }
            }
            if (query.isNotBlank() || (chip == 0 && app.prefs.sort != 0)) item {
                Chip("Reset Filter") { onQuery(""); app.prefs.setSort(0) }
            }
        }

        when {
            !granted -> Message("Allow music permission to find your songs.") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Allow access", true, onRequest)
                    Chip("App settings", onClick = onSettings)
                }
            }
            chip == 3 -> PlaylistsList(app, onOpen)
            chip == 4 -> {
                val folders = remember(results) { groupSongsByFolder(results) }
                if (folders.isEmpty() && loaded) Message("No music folders found.")
                LazyColumn(Modifier.fillMaxSize()) {
                    items(folders, key = { it.path }) { f ->
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .themeClickable(onClickLabel = "Open folder ${f.name}") {
                                    onOpen(Detail.Folder(f.path, f.name))
                                }.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(50.dp).clip(RoundedCornerShape(14.dp)).background(u.accent.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Ico(P.Folder, u.accentText, 26.dp)
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, color = u.text)
                                Text("${f.songs.size} songs · ${f.path}", maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp, color = u.dim)
                            }
                        }
                    }
                }
            }
            chip == 1 -> {
                val albums = remember(results) {
                    results.groupBy { it.albumId }.map { (id, l) ->
                        val artists = l.map { it.artist }.distinct()
                        AlbumItem(id, l.first().album.ifBlank { "Unknown album" }, if (artists.size == 1) artists[0] else "Various artists", l)
                    }.sortedBy { it.name.lowercase() }
                }
                if (albums.isEmpty() && loaded) Message(if (query.isBlank()) "No albums found." else "No results for “$query”.")
                LazyColumn(Modifier.fillMaxSize()) {
                    items(albums, key = { it.id }) { a ->
                        BrowseRow(a.songs.first(), a.name, "${a.artist} · ${a.songs.size} songs") { onOpen(Detail.Album(a.id)) }
                    }
                }
            }
            chip == 2 -> {
                val artists = remember(results) { results.groupBy { it.artist }.toList().sortedBy { it.first.lowercase() } }
                if (artists.isEmpty() && loaded) Message(if (query.isBlank()) "No artists found." else "No results for “$query”.")
                LazyColumn(Modifier.fillMaxSize()) {
                    items(artists, key = { it.first }) { (name, l) ->
                        BrowseRow(l.first(), name, "${l.size} songs · ${l.map { it.albumId }.distinct().size} albums") { onOpen(Detail.Artist(name)) }
                    }
                }
            }
            else -> {
                if (list.isEmpty() && loaded) {
                    when {
                        query.isNotBlank() -> Message("No results for “$query”.")
                        app.songs.isEmpty() -> Message("No songs found on this device.")
                        chip == 5 -> Message("No audio files in your Downloads folder.")
                        chip == 6 -> Message("No liked songs yet. Tap the heart icon on any song.")
                    }
                }
                LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(list, key = { _, s -> s.id }) { i, s ->
                        MusicRow(app, s, ps.current?.mediaId == s.id.toString(), { ps.playSongs(list, i) })
                    }
                }
            }
        }
    }

    if (showVaultAuth) {
        VaultPinDialog(
            vault = app.vault,
            onSuccess = {
                showVaultAuth = false
                onOpen(Detail.Vault)
            },
            onDismiss = { showVaultAuth = false }
        )
    }
}

@Composable
fun MusicRow(app: AppState, s: Song, current: Boolean, onClick: () -> Unit, extras: List<Pair<String, () -> Unit>> = emptyList()) {
    val u = LocalUi.current
    val ps = app.ps
    val act = { if (current) ps.toggle() else onClick() }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(16.dp))
            .then(if (current) Modifier.background(u.accent.copy(alpha = 0.18f)) else Modifier)
            .themeClickable(onClickLabel = if (current) "Play or pause ${s.title}" else "Play ${s.title}") { act() }
            .padding(start = 6.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Art(s.art, s.title, 48.dp, 10.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                s.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = if (current) u.accentText else u.text
            )
            Text(
                s.artist,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 12.sp,
                color = u.dim
            )
        }
        Text(fmt(s.durationMs), fontSize = 12.sp, color = u.dim)
        SongMenu(app, s, extras)
        Box(
            Modifier.size(46.dp).clip(CircleShape)
                .semantics { contentDescription = if (current && ps.playing) "Pause" else "Play ${s.title}" }
                .themeClickable(role = Role.Button) { act() },
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(u.glassHi).border(1.dp, u.border, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Ico(if (current && ps.playing) P.Pause else P.Play, u.text, 20.dp)
            }
        }
    }
}

@Composable
private fun BrowseRow(song: Song, title: String, subtitle: String, onClick: () -> Unit) {
    val u = LocalUi.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(16.dp))
            .themeClickable(onClickLabel = "Open $title", onClick = onClick)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Art(song.art, title, 54.dp, 12.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, color = u.text)
            Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = u.dim)
        }
    }
}

// ───────────────────────── Playlists List ─────────────────────────

@Composable
fun PlaylistsList(app: AppState, onOpen: (Detail) -> Unit) {
    val u = LocalUi.current
    var creating by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${app.pls.lists.size} Playlists", color = u.dim, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Chip("＋ New Playlist", true) { creating = true }
        }

        if (app.pls.lists.isEmpty()) {
            Message("No custom playlists yet. Tap 'New Playlist' to create one.")
        }

        LazyColumn(Modifier.fillMaxSize()) {
            items(app.pls.lists, key = { it.id }) { pl ->
                val songs = remember(pl, app.byId) { pl.songIds.mapNotNull { app.byId[it] } }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .themeClickable(onClickLabel = "Open ${pl.name}") { onOpen(Detail.PL(pl.id)) }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Collage(songs, 54.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(pl.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, color = u.text)
                        Text("${pl.songIds.size} tracks", fontSize = 12.sp, color = u.dim)
                    }
                }
            }
        }
    }

    if (creating) {
        NameDialog("New Playlist", "", "Create", { app.pls.create(it); creating = false }, { creating = false })
    }
}

// ───────────────────────── Detail Screen ─────────────────────────

@Composable
fun DetailScreen(app: AppState, d: Detail, onBack: () -> Unit) {
    val u = LocalUi.current
    val ps = app.ps
    val pl = if (d is Detail.PL) app.pls.lists.firstOrNull { it.id == d.id } else null

    val songs = remember(d, app.songs, app.rawSongs, pl) {
        when (d) {
            is Detail.Album -> app.songs.filter { it.albumId == d.id }.sortedWith(compareBy<Song>({ it.track % 1000 }, { it.title.lowercase() }))
            is Detail.Artist -> sortSongs(app.songs.filter { it.artist == d.name }, 0)
            is Detail.PL -> pl?.songIds?.mapNotNull { app.byId[it] } ?: emptyList()
            is Detail.Folder -> app.songs.filter { it.folderPath == d.path || it.folderName == d.name }
            Detail.Favorites -> app.songs.filter { it.id in app.prefs.favs }
            Detail.Vault -> app.rawSongs.filter { app.vault.isHidden(it.id) }
            Detail.History -> app.history.history.mapNotNull { app.byId[it.songId] }
        }
    }

    val title = when (d) {
        is Detail.Album -> songs.firstOrNull()?.album?.ifBlank { "Unknown album" } ?: "Album"
        is Detail.Artist -> d.name
        is Detail.PL -> pl?.name ?: "Playlist"
        is Detail.Folder -> d.name
        Detail.Favorites -> "Liked Songs"
        Detail.Vault -> "Private Vault"
        Detail.History -> "Playback History"
    }

    var rename by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBtn(P.Back, "Back", onBack)
            Spacer(Modifier.weight(1f))
            if (pl != null) {
                var open by remember { mutableStateOf(false) }
                Box {
                    IconBtn(P.More, "Playlist options", { open = true })
                    DropdownMenu(open, { open = false }) {
                        DropdownMenuItem(text = { Text("Rename") }, onClick = { open = false; rename = true })
                        DropdownMenuItem(text = { Text("Delete playlist") }, onClick = { open = false; delete = true })
                    }
                }
            }
        }

        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            if (pl != null) Collage(songs, 100.dp) else Art(songs.firstOrNull()?.art, title, 100.dp, 16.dp)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, color = u.text)
                Text("${songs.size} songs · ${songs.sumOf { it.durationMs } / 60000} min", fontSize = 13.sp, color = u.dim)
            }
        }

        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("▶ Play All", true) { ps.playSongs(songs, 0, false) }
            Chip("🔀 Shuffle") { if (songs.isNotEmpty()) ps.playSongs(songs, songs.indices.random(), true) }
            if (pl != null) Chip("＋ Add Songs") { picker = true }
        }

        if (songs.isEmpty()) {
            Message(if (pl != null) "This playlist is empty. Tap 'Add Songs' to add tracks." else "No songs found.")
        }

        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(songs, key = { _, s -> s.id }) { i, s ->
                val extras = if (pl == null) emptyList() else buildList<Pair<String, () -> Unit>> {
                    if (i > 0) add("Move up" to { app.pls.moveTo(pl.id, s.id, songs[i - 1].id) })
                    if (i < songs.lastIndex) add("Move down" to { app.pls.moveTo(pl.id, s.id, songs[i + 1].id) })
                    add("Remove from playlist" to { app.pls.remove(pl.id, s.id) })
                }
                MusicRow(app, s, ps.current?.mediaId == s.id.toString(), { ps.playSongs(songs, i) }, extras)
            }
        }
    }

    if (pl != null && rename) {
        NameDialog("Rename playlist", pl.name, "Rename", { app.pls.rename(pl.id, it); rename = false }, { rename = false })
    }
    if (pl != null && delete) {
        ConfirmDialog("Delete “${pl.name}”?", "The playlist will be deleted. Audio files on your device will remain untouched.", "Delete",
            { delete = false; app.pls.delete(pl.id); onBack() }, { delete = false })
    }
    if (pl != null && picker) {
        SongPickerDialog(app, pl.songIds.toSet(), { ids ->
            app.pls.add(pl.id, ids)
            picker = false
        }, { picker = false })
    }
}

// ───────────────────────── Settings Screen ─────────────────────────

@Composable
private fun SettingCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    val u = LocalUi.current
    Column(Modifier.fillMaxWidth().glass(20.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, fontWeight = FontWeight.Bold, color = u.text, fontSize = 16.sp)
        content()
    }
}

@Composable
private fun SwitchRow(label: String, hint: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val u = LocalUi.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, color = u.text, fontSize = 14.sp)
            if (hint != null) Text(hint, fontSize = 12.sp, color = u.dim)
        }
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = u.accent, checkedThumbColor = u.onAccent))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    app: AppState,
    onRescan: () -> Unit,
    onHistory: () -> Unit,
    onEqualizer: () -> Unit
) {
    val u = LocalUi.current
    val prefs = app.prefs
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmReset by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    ctx.contentResolver.openInputStream(uri)?.use { i ->
                        prefs.wallpaperFile.outputStream().use { o -> i.copyTo(o) }
                    } != null
                } catch (e: Exception) {
                    prefs.wallpaperFile.delete()
                    false
                }
            }
            if (ok) prefs.wallpaperChanged() else Toast.makeText(ctx, "Could not load image", Toast.LENGTH_SHORT).show()
        }
    }

    var nameDialog by remember { mutableStateOf(false) }
    var cropSrc by remember { mutableStateOf<Bitmap?>(null) }
    var cropInit by remember { mutableStateOf<CropState?>(null) }

    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val bmp = withContext(Dispatchers.IO) { decodeSampled(ctx, uri) }
            if (bmp != null) {
                cropInit = null
                cropSrc = bmp
            } else {
                Toast.makeText(ctx, "Could not load image file.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val gap = Arrangement.spacedBy(8.dp)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Settings & Customization", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = u.text, modifier = Modifier.padding(horizontal = 4.dp))

        // 5 Improved UI Themes with rich visual preview cards
        SettingCard("App Theme (5 UI Styles)") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeProfiles.forEach { tp ->
                    val sel = prefs.mode == tp.id
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .then(if (sel) Modifier.background(u.accent.copy(alpha = 0.2f)).border(1.5.dp, u.accent, RoundedCornerShape(14.dp)) else Modifier.background(u.glassLo))
                            .themeClickable { prefs.setMode(tp.id) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Swatch
                        Row(Modifier.size(36.dp).clip(CircleShape)) {
                            tp.previewColors.forEach { c ->
                                Box(Modifier.weight(1f).fillMaxHeight().background(c))
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(tp.name, fontWeight = FontWeight.Bold, color = u.text)
                            Text(tp.subtitle, fontSize = 11.sp, color = u.dim)
                        }
                        RadioButton(
                            selected = sel,
                            onClick = { prefs.setMode(tp.id) },
                            colors = RadioButtonDefaults.colors(selectedColor = u.accent)
                        )
                    }
                }
            }
        }

        // Accent Colors
        SettingCard("Accent Color") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Accents.forEachIndexed { i, (name, c) ->
                    val sel = prefs.accent == i
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .semantics { contentDescription = "$name accent" }
                            .themeClickable(role = Role.RadioButton) { prefs.setAccent(i) },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier.size(34.dp).clip(CircleShape).background(c)
                                .then(if (sel) Modifier.border(3.dp, u.text, CircleShape) else Modifier)
                        )
                    }
                }
            }
            SwitchRow("Dynamic artwork colors", "Adapt player tones from current album art.", prefs.dynamic) { prefs.setDynamic(it) }
            SwitchRow("Blurred cover background", "Soft ambient album artwork blur.", prefs.blur) { prefs.setBlur(it) }
        }

        // Audio Effects & Features
        SettingCard("Audio Settings") {
            Row(horizontalArrangement = gap) {
                Chip("Equalizer & Bass Boost", onClick = onEqualizer)
                Chip("Listening Stats", onClick = onHistory)
            }
            SwitchRow("Live Audio Visualizer", "Animate frequency bars during playback.", prefs.visualizerOn) { prefs.setVisualizer(it) }
            Text("Crossfade Duration", fontSize = 13.sp, color = u.dim)
            FlowRow(horizontalArrangement = gap) {
                listOf("Off" to 0, "2s" to 2, "4s" to 4, "6s" to 6, "8s" to 8).forEach { (label, sec) ->
                    Chip(label, prefs.crossfadeSec == sec) { prefs.setCrossfade(sec) }
                }
            }
            Text("Playback Speed", fontSize = 13.sp, color = u.dim)
            FlowRow(horizontalArrangement = gap) {
                speeds.forEachIndexed { i, s ->
                    Chip("${s}x", prefs.speed == i) { prefs.setSpeed(i) }
                }
            }
        }

        // Custom Background Image
        SettingCard("Custom Wallpaper") {
            FlowRow(horizontalArrangement = gap) {
                Chip(if (prefs.wallpaperRev > 0) "Change wallpaper" else "Choose wallpaper") {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                if (prefs.wallpaperRev > 0) Chip("Remove wallpaper") { prefs.clearWallpaper() }
            }
        }

        // Profile Photo & Name
        SettingCard("User Profile") {
            Text(if (prefs.userName.isBlank()) "No user name set" else "Greeting: Hello, ${prefs.userName}", fontSize = 13.sp, color = u.dim)
            FlowRow(horizontalArrangement = gap) {
                Chip(if (prefs.userName.isBlank()) "Set Name" else "Change Name") { nameDialog = true }
                Chip(if (prefs.avatarRev > 0) "Change Photo" else "Choose Photo") {
                    avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                if (prefs.avatarRev > 0 && AvatarStore.hasSource(ctx)) {
                    Chip("Adjust Crop") {
                        scope.launch {
                            val b = withContext(Dispatchers.IO) { AvatarStore.loadSource(ctx) }
                            if (b != null) {
                                cropInit = AvatarStore.loadCrop(ctx)
                                cropSrc = b
                            }
                        }
                    }
                }
                if (prefs.avatarRev > 0) Chip("Remove Photo") { prefs.clearAvatar(); AvatarStore.clear(ctx) }
            }
        }

        // Seekbar Style & Artwork Shape
        SettingCard("Player Layout & Artwork") {
            Text("Seek Bar Style", fontSize = 13.sp, color = u.dim)
            FlowRow(horizontalArrangement = gap) {
                listOf("Waveform Bars", "Classic Slider").forEachIndexed { i, n ->
                    Chip(n, prefs.seekStyle == i) { prefs.setSeekStyle(i) }
                }
            }
            Text("Now Playing Artwork Shape", fontSize = 13.sp, color = u.dim)
            FlowRow(horizontalArrangement = gap) {
                listOf("Circle (Halo)" to 2, "Rounded Card" to 0, "Square" to 1).forEach { (n, v) ->
                    Chip(n, prefs.artShape == v) { prefs.setArtShape(v) }
                }
            }
            Text("Transitions & Motion", fontSize = 13.sp, color = u.dim)
            FlowRow(horizontalArrangement = gap) {
                listOf("Full", "Reduced", "Off").forEachIndexed { i, n ->
                    Chip(n, prefs.motion == i) { prefs.setMotion(i) }
                }
            }
        }

        // Backup and Restore
        val clipboard = LocalClipboardManager.current
        SettingCard("Backup & Restore") {
            Text("Export or restore your playlists, favorites, custom lyrics, and settings.", fontSize = 12.sp, color = u.dim)
            var restoreDialog by remember { mutableStateOf(false) }
            FlowRow(horizontalArrangement = gap) {
                Chip("Backup to Clipboard") {
                    val json = BackupManager.createBackupJson(prefs, app.pls.lists, app.history, app.vault)
                    clipboard.setText(AnnotatedString(json))
                    Toast.makeText(ctx, "Backup JSON copied to clipboard!", Toast.LENGTH_SHORT).show()
                }
                Chip("Restore Backup") { restoreDialog = true }
            }

            if (restoreDialog) {
                var jsonInput by remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { restoreDialog = false },
                    containerColor = dialogColor(),
                    title = { Text("Restore User Data", color = u.text, fontWeight = FontWeight.Bold) },
                    text = {
                        Column {
                            Text("Paste your backup JSON below:", fontSize = 12.sp, color = u.dim)
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = jsonInput,
                                onValueChange = { jsonInput = it },
                                modifier = Modifier.fillMaxWidth().height(160.dp),
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = u.text, unfocusedTextColor = u.text)
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val ok = BackupManager.restoreBackupJson(jsonInput, prefs, app.pls, app.vault)
                                restoreDialog = false
                                if (ok) Toast.makeText(ctx, "Backup restored successfully!", Toast.LENGTH_SHORT).show()
                                else Toast.makeText(ctx, "Invalid backup format.", Toast.LENGTH_SHORT).show()
                            }
                        ) { Text("Restore", color = u.accentText, fontWeight = FontWeight.Bold) }
                    },
                    dismissButton = {
                        TextButton(onClick = { restoreDialog = false }) { Text("Cancel", color = u.dim) }
                    }
                )
            }
        }

        // Library Rescan & Folders
        SettingCard("Music Library") {
            Text("Default Sorting", fontSize = 13.sp, color = u.dim)
            FlowRow(horizontalArrangement = gap) {
                sorts.forEachIndexed { i, n -> Chip(n, prefs.sort == i) { prefs.setSort(i) } }
            }
            Chip("Rescan Library (${app.songs.size} tracks)", onClick = onRescan)
        }

        Chip("Restore Default Settings") { confirmReset = true }

        // About Card
        Column(
            Modifier.fillMaxWidth().glass(20.dp).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("FR Music Player", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = u.text)
            Text("Version 2.0 (Pro Master Edition)", fontSize = 13.sp, color = u.accentText)
            Text("Developer & Creator: FR SHAH", fontSize = 13.sp, color = u.dim)
            Text("A premium offline audio player crafted with 100% Jetpack Compose and ExoPlayer. Fast, modern, and private.", fontSize = 12.sp, color = u.dim)
        }

        Spacer(Modifier.height(12.dp))
    }

    cropSrc?.let { b ->
        AvatarEditor(b, cropInit, { cropSrc = null }) { out, st ->
            scope.launch {
                val ok = withContext(Dispatchers.IO) { AvatarStore.save(ctx, prefs.avatarFile, b, out, st) }
                if (ok) prefs.avatarChanged() else Toast.makeText(ctx, "Could not save photo", Toast.LENGTH_SHORT).show()
                cropSrc = null
            }
        }
    }

    if (nameDialog) {
        NameDialog("Your Name", prefs.userName, "Save", { prefs.setUserName(it); nameDialog = false }, { nameDialog = false })
    }

    if (confirmReset) {
        ConfirmDialog(
            "Restore Defaults?",
            "Visual themes, animation, seekbar style, and playback speed will reset to defaults. Playlists and favorites are preserved.",
            "Restore",
            { confirmReset = false; prefs.resetDefaults() },
            { confirmReset = false }
        )
    }
}
