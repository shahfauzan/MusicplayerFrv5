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
}
