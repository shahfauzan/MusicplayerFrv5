# FR Music Player - Android Offline Player

FR Music Player is a modern, high-performance offline music player for Android built with Jetpack Compose and Material Design 3.

## Features
- **Architecture & Performance**: Background indexing with Coroutines (`Dispatchers.IO`), debounced multi-field search, and smooth 60fps lists.
- **Audio Engine**: Powered by AndroidX Media3 / ExoPlayer with gapless playback, lock-screen controls, and system media notifications.
- **Equalizer & Effects**: 5-band hardware equalizer, Bass Boost (0–1000‰), Virtualizer (0–1000‰), and 12 sound presets.
- **5 Premium Themes**: Violet Glass, AMOLED Stealth, Crystal Light, Cyber Neon, and Sunset Amber.
- **Interactive Avatar Editor**: Full circular crop editor with pinch-to-zoom (1x–5x), 2D pan, rotation, and persistent profile storage.
- **Smart Playlists**: Recently Added, Most Played, Favorites, and full custom playlist management.
- **Lyrics & Tag Editor**: Integrated lyrics viewer/editor and ID3 tag editor for Title, Artist, Album, Year, and Track.
- **Sleep Timer**: Flexible timer intervals (15 to 90 min) with "Stop after current song" option.
- **Audio Spectrum Visualizer**: Hardware session-bound spectrum visualizer with fallback animated waveform.
- **Home-Screen Widget**: `FrMusicWidget` with real-time track info and Play/Pause/Skip controls.
- **Private Audio Vault**: 4-digit PIN-protected vault for private tracks.
- **Backup & Restore**: Export and import playlists and settings via standard JSON.
- **Online Player (`OnlineScreen`)**: Compliant web player embeds for YouTube and Spotify track previews.

## How to Open in Android Studio
1. Unzip the project folder.
2. In Android Studio, select **File > Open...** and select the unzipped directory.
3. Allow Gradle to sync dependencies.
4. Run on an Android device or emulator running Android 7.0 (API level 24) or higher.

## Requirements
- Android SDK: Min API 24 (Android 7.0), Target API 36
- Java / JDK: 17 or higher
- Gradle: 8.9+ with Android Gradle Plugin 8.9.0
- Language: Kotlin 2.0+ with Jetpack Compose (Material 3)
