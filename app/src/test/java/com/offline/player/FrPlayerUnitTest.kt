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
        assertEquals("Titanium Slate", ThemeProfiles[0].name)
        assertEquals("AMOLED Obsidian", ThemeProfiles[1].name)
        assertEquals("Pearl White", ThemeProfiles[2].name)
        assertEquals("Frosted Silver", ThemeProfiles[3].name)
        assertEquals("Matte Graphite", ThemeProfiles[4].name)
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

    @Test
    fun testParseYouTubeLinks() {
        val ytWatch = parseOnlineLink("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        assertTrue(ytWatch != null)
        assertEquals("YouTube", ytWatch?.platform)
        assertTrue(ytWatch?.embed?.contains("dQw4w9WgXcQ") == true)
        assertTrue(ytWatch?.embed?.contains("enablejsapi=1") == true)

        val ytShort = parseOnlineLink("https://youtu.be/dQw4w9WgXcQ")
        assertTrue(ytShort != null)
        assertEquals("YouTube", ytShort?.platform)
        assertTrue(ytShort?.embed?.contains("dQw4w9WgXcQ") == true)
    }

    @Test
    fun testParseSpotifyLinks() {
        val spotifyTrack = parseOnlineLink("https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT")
        assertTrue(spotifyTrack != null)
        assertEquals("Spotify", spotifyTrack?.platform)
        assertTrue(spotifyTrack?.embed?.contains("4cOdK2wGLETKBW3PvgPWqT") == true)
    }

    @Test
    fun testAudioVisualizerManagerSafety() {
        // Must safely bind/release without throwing exceptions
        AudioVisualizerManager.release()
        assertEquals(0f, AudioVisualizerManager.energy, 0.001f)
        assertEquals(36, AudioVisualizerManager.waveBytes.size)
    }

    @Test
    fun testSeekProgressCalculation() {
        val durationMs = 200_000L // 200 seconds
        val positionMs = 50_000L   // 50 seconds
        val progress = (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
        assertEquals(0.25f, progress, 0.001f)

        // Scrub ratio to target position calculation
        val scrubRatio = 0.75f
        val targetMs = (scrubRatio * durationMs).toLong()
        assertEquals(150_000L, targetMs)
    }

    @Test
    fun testQuickSeekJumps() {
        val duration = 180_000L
        val current = 15_000L

        val rewound = (current - 10_000L).coerceAtLeast(0L)
        assertEquals(5_000L, rewound)

        val forwarded = (current + 10_000L).coerceAtMost(duration)
        assertEquals(25_000L, forwarded)

        val clampedRewind = (5_000L - 10_000L).coerceAtLeast(0L)
        assertEquals(0L, clampedRewind)
    }
}
