package com.offline.player

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class EqualizerManager(ctx: Context) {
    private val p: SharedPreferences = ctx.applicationContext.getSharedPreferences("fr_eq", Context.MODE_PRIVATE)

    var enabled by mutableStateOf(p.getBoolean("eq_on", false)); private set
    var presetIndex by mutableIntStateOf(p.getInt("eq_preset", 0)); private set
    var bassStrength by mutableIntStateOf(p.getInt("eq_bass", 0)); private set
    var virtualizerStrength by mutableIntStateOf(p.getInt("eq_virt", 0)); private set
    var bandLevels by mutableStateOf(loadBandLevels()); private set
    var isSupported by mutableStateOf(true); private set

    private var eq: Equalizer? = null
    private var bass: BassBoost? = null
    private var virt: Virtualizer? = null
    private var currentSessionId: Int = 0

    val presets = listOf(
        "Normal", "Classical", "Dance", "Flat", "Folk", "Heavy Metal",
        "Hip Hop", "Jazz", "Pop", "Rock", "Bass Boost", "Vocal"
    )

    fun bind(audioSessionId: Int) {
        if (audioSessionId <= 0 || audioSessionId == currentSessionId) return
        currentSessionId = audioSessionId
        release()
        try {
            eq = Equalizer(0, audioSessionId).apply {
                this.enabled = this@EqualizerManager.enabled
            }
            bass = BassBoost(0, audioSessionId).apply {
                this.enabled = this@EqualizerManager.enabled
                if (strengthSupported) setStrength(bassStrength.toShort())
            }
            virt = Virtualizer(0, audioSessionId).apply {
                this.enabled = this@EqualizerManager.enabled
                if (strengthSupported) setStrength(virtualizerStrength.toShort())
            }
            applyBands()
            isSupported = true
        } catch (e: Exception) {
            isSupported = false
            eq = null
            bass = null
            virt = null
        }
    }

    private fun loadBandLevels(): List<Int> {
        val s = p.getString("eq_bands", "") ?: ""
        if (s.isNotBlank()) {
            val list = s.split(',').mapNotNull { it.toIntOrNull() }
            if (list.size >= 5) return list.take(5)
        }
        return listOf(0, 0, 0, 0, 0)
    }

    fun setEnabledState(on: Boolean) {
        enabled = on
        p.edit().putBoolean("eq_on", on).apply()
        eq?.enabled = on
        bass?.enabled = on
        virt?.enabled = on
    }

    fun setBand(bandIndex: Int, level: Int) {
        if (bandIndex !in bandLevels.indices) return
        val updated = bandLevels.toMutableList()
        updated[bandIndex] = level
        bandLevels = updated
        p.edit().putString("eq_bands", updated.joinToString(",")).apply()

        eq?.let {
            if (bandIndex < it.numberOfBands) {
                try {
                    it.setBandLevel(bandIndex.toShort(), (level * 100).toShort())
                } catch (e: Exception) {}
            }
        }
    }

    fun setPreset(index: Int) {
        presetIndex = index
        p.edit().putInt("eq_preset", index).apply()
        val target = when (index) {
            1 -> listOf(3, 2, -1, 2, 4)       // Classical
            2 -> listOf(5, 2, 1, 3, 4)        // Dance
            3 -> listOf(0, 0, 0, 0, 0)        // Flat
            4 -> listOf(2, 1, 0, 1, -1)       // Folk
            5 -> listOf(4, 2, -1, 3, 5)       // Heavy Metal
            6 -> listOf(5, 3, 0, 2, 3)        // Hip Hop
            7 -> listOf(3, 1, -1, 2, 4)       // Jazz
            8 -> listOf(-1, 2, 5, 2, -1)      // Pop
            9 -> listOf(4, 2, -1, 2, 5)       // Rock
            10 -> listOf(8, 5, 1, 0, 0)       // Bass Boost
            11 -> listOf(-2, 0, 4, 3, 1)      // Vocal
            else -> listOf(0, 0, 0, 0, 0)     // Normal
        }
        target.forEachIndexed { i, v -> setBand(i, v) }
    }

    fun setBass(strength: Int) {
        bassStrength = strength
        p.edit().putInt("eq_bass", strength).apply()
        bass?.let {
            try {
                if (it.strengthSupported) it.setStrength(strength.toShort())
            } catch (e: Exception) {}
        }
    }

    fun setVirtualizer(strength: Int) {
        virtualizerStrength = strength
        p.edit().putInt("eq_virt", strength).apply()
        virt?.let {
            try {
                if (it.strengthSupported) it.setStrength(strength.toShort())
            } catch (e: Exception) {}
        }
    }

    private fun applyBands() {
        val e = eq ?: return
        try {
            val num = e.numberOfBands.toInt()
            for (i in 0 until minOf(num, bandLevels.size)) {
                e.setBandLevel(i.toShort(), (bandLevels[i] * 100).toShort())
            }
        } catch (ex: Exception) {}
    }

    fun release() {
        try { eq?.release() } catch (e: Exception) {}
        try { bass?.release() } catch (e: Exception) {}
        try { virt?.release() } catch (e: Exception) {}
        eq = null; bass = null; virt = null
    }
}
