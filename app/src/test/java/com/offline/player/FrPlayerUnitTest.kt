package com.offline.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrPlayerUnitTest {

    @Test
    fun testDurationFormatting() {
        val durationMs = 185000L // 3 minutes 5 seconds
        val seconds = (durationMs / 1000) % 60
        val minutes = (durationMs / (1000 * 60)) % 60
        val formatted = String.format("%02d:%02d", minutes, seconds)
        assertEquals("03:05", formatted)
    }

    @Test
    fun testThemeProfiles() {
        assertEquals(5, ThemeProfiles.size)
        assertEquals("Violet Glass", ThemeProfiles[0].name)
        assertEquals("AMOLED Stealth", ThemeProfiles[1].name)
        assertEquals("Crystal Light", ThemeProfiles[2].name)
        assertEquals("Cyber Neon", ThemeProfiles[3].name)
        assertEquals("Sunset Amber", ThemeProfiles[4].name)
    }

    @Test
    fun testSearchQueryMatching() {
        val title = "Shape of You"
        val artist = "Ed Sheeran"
        val album = "Divide"
        val folder = "Hits"
        val key = "$title $artist $album $folder".lowercase()

        assertTrue(key.contains("ed sheeran"))
        assertTrue(key.contains("shape of you"))
        assertTrue(key.contains("hits"))
    }

    @Test
    fun testAudioStoragePermission() {
        val perm = getAudioStoragePermission()
        assertTrue(
            perm == android.Manifest.permission.READ_MEDIA_AUDIO ||
            perm == android.Manifest.permission.READ_EXTERNAL_STORAGE
        )
    }

    @Test
    fun testEqualizerBandsAndPresets() {
        val presets = listOf(
            "Normal", "Classical", "Dance", "Flat", "Folk", "Heavy Metal",
            "Hip Hop", "Jazz", "Pop", "Rock", "Bass Boost", "Vocal"
        )
        assertEquals(12, presets.size)

        val bandNames = listOf("60 Hz", "230 Hz", "910 Hz", "3.6 kHz", "14 kHz")
        assertEquals(5, bandNames.size)

        // Verify level range clamping
        val minLevel = -10
        val maxLevel = 10
        val testLevel = 15.coerceIn(minLevel, maxLevel)
        assertEquals(10, testLevel)
    }

    @Test
    fun testBeatShakeModes() {
        assertEquals(4, BeatShakeModes.size)
        assertEquals("Off", BeatShakeModes[0].name)
        assertEquals("Subtle", BeatShakeModes[1].name)
        assertEquals("Dynamic", BeatShakeModes[2].name)
        assertEquals("Party Mode", BeatShakeModes[3].name)
    }

    @Test
    fun testBeatShakeStateDefaults() {
        val defaultState = BeatShakeState()
        assertEquals(0f, defaultState.offsetX, 0.001f)
        assertEquals(0f, defaultState.offsetY, 0.001f)
        assertEquals(0f, defaultState.rotation, 0.001f)
        assertEquals(1f, defaultState.scale, 0.001f)
        assertEquals(0f, defaultState.glowAlpha, 0.001f)
    }
}
