# Aircraft

Aircraft is a 2D vertical-scrolling shooter game for Android, written in Kotlin. The player controls a jet plane, fires bullets upward, and destroys enemies while avoiding collisions.

![App Icon](app/src/main/res/mipmap-anydpi-v26/ic_launcher.png)

## Gameplay

- **10 Levels**: Each level has a countdown timer and requires 100+ enemy kills to advance.
- **Time Pressure**: Level 1 starts at 300 seconds; each subsequent level reduces the time by 20 seconds (down to 120 seconds at level 10).
- **Scaling Difficulty**: Enemies get tougher each level with more health, more enemies per row, faster spawn rates, and tighter bullet spacing.
- **Touch Controls**: Drag to move the player jet. Bullets fire automatically.

## Features

- 🎮 **Classic Arcade Shooter**: Intuitive touch-and-drag controls
- ⏱️ **Time-Based Progression**: 10 challenging levels with decreasing time limits
- 🎯 **Scaling Difficulty**: Progressive enemy stat increases
- 🎵 **Audio Experience**: Looping background music + dynamic sound effects
- ✈️ **Custom Game Engine**: 30 FPS rendering with smooth animations
- 🎨 **Modern UI**: Material Design with adaptive icon

## Screenshots

### App Icon
The app features a vibrant fighter jet launch icon with:
- Deep space gradient background with radiant light effects
- Detailed metallic fighter jet with afterburner flames
- Dynamic speed lines and sparkle effects
- Adaptive icon support for all Android versions

## Architecture

- **Game Engine**: Custom `SurfaceView` (`GameCoreView`) with a dedicated rendering thread at 30 FPS, drawing directly to `Canvas`.
- **Audio**: Background music via `MediaPlayer` (looping), sound effects via `SoundPool` (fire, hit, game over).
- **Level System**: Time-based progression with per-level enemy stat scaling.
- **Collision Detection**: Per-frame checks for player vs enemies, enemy bullets vs player, and player bullets vs enemies.

## Project Structure

```
app/src/main/java/com/young/aircraft/
├── common/
│   └── AircraftApplication.kt       # Application class
├── data/
│   ├── Aircraft.kt                  # Player data model (health, lethality)
│   └── EnemyState.kt                # Enemy state (position, health, bullets)
├── gui/
│   ├── LaunchActivity.kt            # Splash / launch screen
│   ├── MainActivity.kt              # Hosts GameCoreView, binds MusicService
│   ├── SettingsActivity.kt          # Game settings
│   └── PrivacyPolicyActivity.kt     # Privacy policy screen
├── service/
│   └── MusicService.kt              # Bound service: MediaPlayer (BGM) + SoundPool (SFX)
├── ui/
│   ├── DrawBaseObject.kt            # Abstract base class for all drawable objects
│   ├── GameCoreView.kt              # SurfaceView game loop, collision, level logic
│   ├── Aircraft.kt                  # Player jet rendering and bullet firing
│   ├── Enemies.kt                   # Enemy spawning, movement, and bullet system
│   ├── DrawBackground.kt            # Scrolling background
│   └── DrawHeader.kt                # HUD overlay (level, HP, timer, kills)
├── utils/
│   ├── ScreenUtils.kt               # Screen dimensions, dp/sp/px conversion
│   └── BitmapUtils.kt               # Bitmap loading, resizing, rotation
└── viewmodel/
    └── MainViewModel.kt             # LiveData for service readiness
```

## Requirements

- **Android Studio**: Meerkat | 2024.3.1 Patch 1 or later
- **Android SDK**: Compile SDK 35
- **Minimum SDK**: 30
- **Target SDK**: 35
- **Java Version**: 17

## Build

```bash
./gradlew assembleDebug          # Build debug APK
./gradlew assembleRelease        # Build release APK
./gradlew test                   # Run unit tests
./gradlew clean                  # Clean build
```

## Setup

1. Clone the repository:
   ```bash
   git clone <repository-url>
   cd Aircraft
   ```
2. Open in Android Studio.
3. Sync Gradle and run on a device or emulator (API 30+).

## License

This project is open source and available for educational purposes.
