package com.offline.player

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

class Prefs(ctx: Context) {
    private val appCtx = ctx.applicationContext
    private val p: SharedPreferences = appCtx.getSharedPreferences("fr", Context.MODE_PRIVATE)
    val raw: SharedPreferences get() = p
    val wallpaperFile = File(appCtx.filesDir, "wallpaper.jpg")
    val avatarFile = File(appCtx.filesDir, "avatar.jpg")

    /** 0: Violet Glass, 1: AMOLED Stealth, 2: Crystal Light, 3: Cyber Neon, 4: Sunset Amber */
    var mode by mutableIntStateOf(p.getInt("mode", 0)); private set
    /** Index into Accents */
    var accent by mutableIntStateOf(p.getInt("theme", 5).coerceIn(0, Accents.lastIndex)); private set
    var sort by mutableIntStateOf(p.getInt("sort", 0).coerceIn(0, sorts.lastIndex)); private set
    var speed by mutableIntStateOf(p.getInt("speed", 2).coerceIn(0, speeds.lastIndex)); private set // default 1.0x
    var blur by mutableStateOf(p.getBoolean("blur", true)); private set
    var dynamic by mutableStateOf(p.getBoolean("dynamic", true)); private set
    var motion by mutableIntStateOf(p.getInt("motion", systemMotion())); private set
    var artShape by mutableIntStateOf(p.getInt("artshape", 2)); private set // 0 Rounded, 1 Square, 2 Circle
    var seekStyle by mutableIntStateOf(p.getInt("seekstyle", 0)); private set // 0 Waveform, 1 Slider
    var crossfadeSec by mutableIntStateOf(p.getInt("crossfade", 0)); private set // 0 (off), 2, 4, 6, 8
    var visualizerOn by mutableStateOf(p.getBoolean("vis_on", true)); private set
    var beatShakeMode by mutableIntStateOf(p.getInt("beat_shake", 2)); private set // 0 Off, 1 Subtle, 2 Dynamic, 3 Wild/Party
    var beatShakeHaptics by mutableStateOf(p.getBoolean("beat_haptics", false)); private set
    var userName by mutableStateOf(p.getString("username", "") ?: ""); private set
    var avatarRev by mutableIntStateOf(if (avatarFile.exists()) 1 else 0); private set
    var wallpaperRev by mutableIntStateOf(if (wallpaperFile.exists()) 1 else 0); private set

    var favs by mutableStateOf(
        (p.getStringSet("favs", emptySet()) ?: emptySet()).mapNotNull { it.toLongOrNull() }.toSet()
    ); private set

    var excludedFolders by mutableStateOf(
        p.getStringSet("excluded_folders", emptySet()) ?: emptySet()
    ); private set

    private fun systemMotion(): Int = try {
        if (Settings.Global.getFloat(appCtx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f) 2 else 0
    } catch (e: Exception) { 0 }

    @JvmName("applyMode") fun setMode(v: Int) { mode = v.coerceIn(0, 4); p.edit().putInt("mode", mode).apply() }
    @JvmName("applyAccent") fun setAccent(v: Int) { accent = v.coerceIn(0, Accents.lastIndex); p.edit().putInt("theme", accent).apply() }
    @JvmName("applySort") fun setSort(v: Int) { sort = v; p.edit().putInt("sort", v).apply() }
    @JvmName("applySpeed") fun setSpeed(v: Int) { speed = v.coerceIn(0, speeds.lastIndex); p.edit().putInt("speed", speed).apply() }
    @JvmName("applyBlur") fun setBlur(v: Boolean) { blur = v; p.edit().putBoolean("blur", v).apply() }
    @JvmName("applyDynamic") fun setDynamic(v: Boolean) { dynamic = v; p.edit().putBoolean("dynamic", v).apply() }
    @JvmName("applyMotion") fun setMotion(v: Int) { motion = v; p.edit().putInt("motion", v).apply() }
    @JvmName("applyArtShape") fun setArtShape(v: Int) { artShape = v; p.edit().putInt("artshape", v).apply() }
    @JvmName("applySeekStyle") fun setSeekStyle(v: Int) { seekStyle = v; p.edit().putInt("seekstyle", v).apply() }
    @JvmName("applyCrossfade") fun setCrossfade(v: Int) { crossfadeSec = v; p.edit().putInt("crossfade", v).apply() }
    @JvmName("applyVisualizer") fun setVisualizer(v: Boolean) { visualizerOn = v; p.edit().putBoolean("vis_on", v).apply() }
    @JvmName("applyBeatShake") fun setBeatShake(v: Int) { beatShakeMode = v.coerceIn(0, 3); p.edit().putInt("beat_shake", beatShakeMode).apply() }
    @JvmName("applyBeatHaptics") fun setBeatHaptics(v: Boolean) { beatShakeHaptics = v; p.edit().putBoolean("beat_haptics", v).apply() }
    @JvmName("applyUserName") fun setUserName(v: String) { userName = v.trim(); p.edit().putString("username", userName).apply() }

    fun toggleFolderExclude(folder: String) {
        val updated = if (folder in excludedFolders) excludedFolders - folder else excludedFolders + folder
        excludedFolders = updated
        p.edit().putStringSet("excluded_folders", updated).apply()
    }

    fun avatarChanged() { avatarRev = if (avatarFile.exists()) avatarRev + 1 else 0 }
    fun clearAvatar() { avatarFile.delete(); avatarChanged() }
    fun wallpaperChanged() { wallpaperRev = if (wallpaperFile.exists()) wallpaperRev + 1 else 0 }
    fun clearWallpaper() { wallpaperFile.delete(); wallpaperChanged() }

    fun toggleFav(id: Long) {
        favs = if (id in favs) favs - id else favs + id
        p.edit().putStringSet("favs", favs.map { it.toString() }.toSet()).apply()
    }

    fun resetDefaults() {
        p.edit().remove("mode").remove("theme").remove("sort").remove("speed").remove("blur")
            .remove("dynamic").remove("motion").remove("artshape").remove("seekstyle").remove("crossfade")
            .remove("beat_shake").remove("beat_haptics").apply()
        clearWallpaper()
        mode = 0; accent = 5; sort = 0; speed = 2; blur = true; dynamic = true; motion = systemMotion()
        artShape = 2; seekStyle = 0; crossfadeSec = 0; visualizerOn = true; beatShakeMode = 2; beatShakeHaptics = false
    }

    fun saveSession(ids: String, idx: Int, pos: Long) {
        p.edit().putString("s_ids", ids).putInt("s_idx", idx).putLong("s_pos", pos).apply()
    }
    fun sessionIds(): List<Long> = (p.getString("s_ids", "") ?: "").split(',').mapNotNull { it.toLongOrNull() }
    fun sessionIdx(): Int = p.getInt("s_idx", 0)
    fun sessionPos(): Long = p.getLong("s_pos", 0L)
}

data class Playlist(val id: Long, val name: String, val songIds: List<Long>)

class PlaylistStore(private val p: SharedPreferences) {
    var lists by mutableStateOf(load()); private set

    private fun load(): List<Playlist> = try {
        val arr = JSONArray(p.getString("playlists", "[]"))
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            val ids = o.getJSONArray("ids")
            Playlist(o.getLong("id"), o.getString("name"), List(ids.length()) { ids.getLong(it) })
        }
    } catch (e: Exception) { emptyList() }

    private fun save(l: List<Playlist>) {
        lists = l
        val arr = JSONArray()
        l.forEach {
            val o = JSONObject()
            o.put("id", it.id); o.put("name", it.name); o.put("ids", JSONArray(it.songIds))
            arr.put(o)
        }
        p.edit().putString("playlists", arr.toString()).apply()
    }

    private fun update(id: Long, f: (Playlist) -> Playlist) = save(lists.map { if (it.id == id) f(it) else it })

    fun create(name: String, ids: List<Long> = emptyList()): Playlist {
        var id = System.currentTimeMillis()
        while (lists.any { it.id == id }) id++
        val pl = Playlist(id, name.trim().ifBlank { "Playlist" }, ids.distinct())
        save(lists + pl)
        return pl
    }
    fun rename(id: Long, name: String) = update(id) { it.copy(name = name.trim().ifBlank { it.name }) }
    fun delete(id: Long) = save(lists.filter { it.id != id })
    fun add(id: Long, ids: List<Long>): Int {
        var added = 0
        update(id) { pl ->
            val fresh = ids.filter { it !in pl.songIds }.distinct()
            added = fresh.size
            pl.copy(songIds = pl.songIds + fresh)
        }
        return added
    }
    fun remove(id: Long, songId: Long) = update(id) { it.copy(songIds = it.songIds - songId) }
    fun moveTo(id: Long, fromId: Long, toId: Long) = update(id) { pl ->
        val l = pl.songIds.toMutableList()
        val to = l.indexOf(toId)
        if (l.remove(fromId) && to >= 0) { l.add(to.coerceAtMost(l.size), fromId) } else return@update pl
        pl.copy(songIds = l)
    }
    fun keepOnly(id: Long, valid: Set<Long>) = update(id) { it.copy(songIds = it.songIds.filter { s -> s in valid }) }
}

data class HistoryItem(val songId: Long, val timestamp: Long)

class HistoryStore(ctx: Context) {
    private val p: SharedPreferences = ctx.applicationContext.getSharedPreferences("fr_history", Context.MODE_PRIVATE)

    var plays by mutableStateOf(loadPlays()); private set
    var history by mutableStateOf(loadHistory()); private set
    var totalSeconds by mutableLongStateOf(p.getLong("total_listening_sec", 0L)); private set

    private fun loadPlays(): Map<Long, Int> = (p.getString("plays_count", "") ?: "").split(';').mapNotNull {
        val a = it.split(':')
        val id = a.getOrNull(0)?.toLongOrNull(); val n = a.getOrNull(1)?.toIntOrNull()
        if (id != null && n != null) id to n else null
    }.toMap()

    private fun loadHistory(): List<HistoryItem> = try {
        val arr = JSONArray(p.getString("history_items", "[]"))
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            HistoryItem(o.getLong("id"), o.getLong("t"))
        }
    } catch (e: Exception) { emptyList() }

    fun recordPlay(songId: Long, durationSec: Long) {
        val newPlays = plays + (songId to ((plays[songId] ?: 0) + 1))
        plays = newPlays
        p.edit().putString("plays_count", newPlays.entries.joinToString(";") { "${it.key}:${it.value}" }).apply()

        val newHistory = (listOf(HistoryItem(songId, System.currentTimeMillis())) + history).take(200)
        history = newHistory
        val arr = JSONArray()
        newHistory.forEach {
            val o = JSONObject()
            o.put("id", it.songId); o.put("t", it.timestamp)
            arr.put(o)
        }
        totalSeconds += durationSec
        p.edit().putString("history_items", arr.toString())
            .putLong("total_listening_sec", totalSeconds)
            .apply()
    }

    fun clearHistory() {
        history = emptyList()
        plays = emptyMap()
        totalSeconds = 0L
        p.edit().remove("history_items").remove("plays_count").remove("total_listening_sec").apply()
    }
}

class LyricsStore(ctx: Context) {
    private val p: SharedPreferences = ctx.applicationContext.getSharedPreferences("fr_lyrics", Context.MODE_PRIVATE)

    fun getLyrics(songId: Long): String? = p.getString("lyric_$songId", null)
    fun saveLyrics(songId: Long, lyrics: String) {
        p.edit().putString("lyric_$songId", lyrics.trim()).apply()
    }
}

class VaultStore(ctx: Context) {
    private val p: SharedPreferences = ctx.applicationContext.getSharedPreferences("fr_vault", Context.MODE_PRIVATE)

    var hiddenIds by mutableStateOf(
        (p.getStringSet("vault_ids", emptySet()) ?: emptySet()).mapNotNull { it.toLongOrNull() }.toSet()
    ); private set

    fun getPin(): String = p.getString("vault_pin", "1234") ?: "1234"
    fun setPin(pin: String) { p.edit().putString("vault_pin", pin.trim()).apply() }

    fun toggleHide(songId: Long) {
        val updated = if (songId in hiddenIds) hiddenIds - songId else hiddenIds + songId
        hiddenIds = updated
        p.edit().putStringSet("vault_ids", updated.map { it.toString() }.toSet()).apply()
    }

    fun isHidden(songId: Long): Boolean = songId in hiddenIds
}

object BackupManager {
    fun createBackupJson(
        prefs: Prefs,
        playlists: List<Playlist>,
        history: HistoryStore,
        vault: VaultStore
    ): String {
        val root = JSONObject()
        root.put("version", 2)
        root.put("timestamp", System.currentTimeMillis())

        val pObj = JSONObject()
        pObj.put("mode", prefs.mode)
        pObj.put("accent", prefs.accent)
        pObj.put("sort", prefs.sort)
        pObj.put("artshape", prefs.artShape)
        pObj.put("seekstyle", prefs.seekStyle)
        pObj.put("username", prefs.userName)
        pObj.put("favs", JSONArray(prefs.favs.toList()))
        root.put("preferences", pObj)

        val plArr = JSONArray()
        playlists.forEach {
            val o = JSONObject()
            o.put("id", it.id); o.put("name", it.name); o.put("ids", JSONArray(it.songIds))
            plArr.put(o)
        }
        root.put("playlists", plArr)

        val vArr = JSONArray(vault.hiddenIds.toList())
        root.put("vault", vArr)

        return root.toString(2)
    }

    fun restoreBackupJson(
        jsonString: String,
        prefs: Prefs,
        pls: PlaylistStore,
        vault: VaultStore
    ): Boolean = try {
        val root = JSONObject(jsonString)
        if (root.has("preferences")) {
            val pObj = root.getJSONObject("preferences")
            if (pObj.has("mode")) prefs.setMode(pObj.getInt("mode"))
            if (pObj.has("accent")) prefs.setAccent(pObj.getInt("accent"))
            if (pObj.has("sort")) prefs.setSort(pObj.getInt("sort"))
            if (pObj.has("artshape")) prefs.setArtShape(pObj.getInt("artshape"))
            if (pObj.has("seekstyle")) prefs.setSeekStyle(pObj.getInt("seekstyle"))
            if (pObj.has("username")) prefs.setUserName(pObj.getString("username"))
            if (pObj.has("favs")) {
                val fArr = pObj.getJSONArray("favs")
                for (i in 0 until fArr.length()) {
                    prefs.toggleFav(fArr.getLong(i))
                }
            }
        }
        if (root.has("playlists")) {
            val plArr = root.getJSONArray("playlists")
            for (i in 0 until plArr.length()) {
                val o = plArr.getJSONObject(i)
                val name = o.getString("name")
                val ids = o.getJSONArray("ids")
                val songIds = List(ids.length()) { ids.getLong(it) }
                pls.create(name, songIds)
            }
        }
        if (root.has("vault")) {
            val vArr = root.getJSONArray("vault")
            for (i in 0 until vArr.length()) {
                vault.toggleHide(vArr.getLong(i))
            }
        }
        true
    } catch (e: Exception) { false }
}
