# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Aircraft is a 2D vertical-scrolling shooter game for Android, written in Kotlin. The player controls a jet plane, fires bullets upward, and destroys enemies while avoiding collisions. The game has 10 time-based levels with decreasing time limits and scaling difficulty.

For detailed project documentation (tech stack, project structure, game loop walkthrough, collision detection, level system formulas, database schema, threading rules, common tasks, and how to play), see **[DOCUMENT.md](DOCUMENT.md)**.

## Build Commands

```bash
./gradlew assembleDebug          # Build debug APK
./gradlew assembleRelease        # Build release APK
./gradlew test                   # Run unit tests
./gradlew testDebugUnitTest --tests "com.young.aircraft.ExampleUnitTest"  # Single test class
./gradlew connectedAndroidTest   # Instrumented tests (requires device/emulator)
./gradlew clean                  # Clean build
./gradlew lint                   # Lint check
```

## Build Configuration

- **Gradle:** 9.3.1, AGP 9.1.0 (bundles Kotlin — do NOT add `org.jetbrains.kotlin.android` plugin separately), KSP 2.1.20-1.0.32
- **SDK:** compileSdk 36, minSdk 30, targetSdk 35
- **Java:** 17
- **App ID:** `com.young.aircraft`
- View Binding and Data Binding are both enabled
- `android.disallowKotlinSourceSets=false` in gradle.properties (required for KSP compatibility with AGP's built-in Kotlin)

## Architecture

### Game Engine (SurfaceView-based)

The game runs on a custom `SurfaceView` (`GameCoreView`) with a dedicated rendering thread at 30 FPS. This is **not** a Compose or standard View-based UI — it draws directly to a `Canvas`.

**Rendering hierarchy:**
- `GameCoreView` (SurfaceView + Runnable) — owns the game loop, coordinates all drawing, collision detection, and level progression
- `DrawBaseObject` — abstract base class (`onDraw`, `updateGame`, `getEnemyBounds`) for all drawable game objects
  - `Aircraft` (ui/) — player jet with auto-firing bullets (every 2 frames), touch-based movement
  - `DrawBackground` — seamless double-buffer scrolling background
  - `DrawHeader` — HUD overlay showing level, HP, timer countdown, kill count
  - `Enemies` — timed row spawning with per-enemy Y tracking, 10 sprite types, red-tinted bullets
  - `ExplosionEffect` — particle-based death animation (flash, fireball, debris, smoke phases)

### Level System (Time-Based)

Defined in `GameCoreView` companion object. Level duration **decreases** with progression; required kills **increase**:
- **Duration:** 300s - 20s*(level-1) (300s at level 1, down to 120s at level 10)
- **Required kills:** 90 + level*10 (100 at level 1, up to 190 at level 10)

Enemy stats scale with level:
- **Enemies per row:** 5 + level (in `Enemies.getEnemiesPerRow()`)
- **Spawn interval:** 90 - 5*(level-1) frames (in `Enemies.getSpawnIntervalFrames()`)
- **Bullet spacing:** 350 - 15*(level-1) dp, min 250dp (in `Enemies.getBulletSpacingDp()`)
- **Move speed:** 3 + 1.5*(level-1) (in `Enemies.getEnemyMoveSpeed()`)

Enemies have 1 HP and are destroyed in a single hit. Player has 100 HP and loses 20 per hit (`Aircraft.BULLET_DAMAGE`).

### Scoring & Persistence (Room Database)

- **Score:** 100 points per kill, cumulative across all levels in a session. Reset when switching users.
- **Player ID:** Device's `Settings.Secure.ANDROID_ID`
- **Database:** Room (`AppDatabase`, version 2026) with `fallbackToDestructiveMigration()`. Table: `player_game_data` (playerId, level, score, timestamp).
- **DAO:** `PlayerGameDataDao` — insert, query by player, query all sorted by score DESC, get total score, delete by player.
- Game data is saved on both game over and game won via `lifecycleScope` in `MainActivity`.

### Enemy System (Per-Enemy Y Tracking)

Enemies use individual Y positions (`EnemyState.y`) — multiple rows coexist on screen simultaneously. Each `EnemyState` tracks its own position, health, destruction time, and bullet list (`MutableList<EnemyBullet>`). `EnemyBullet` stores both current Y and origin Y for 60% screen-height range limiting.

### Collision Detection

Three checks run every frame in `GameCoreView.checkCollision()`:
1. Player aircraft vs enemy sprites (RectF intersection, with cooldown)
2. Enemy bullets vs player (`getEnemyBullets()` returns `Triple<x, y, EnemyBullet>` for removal by reference)
3. Player bullets vs enemies (iterates `activeEnemies`, increments kill counters — both `enemiesDestroyedThisLevel` and `totalKills` — on destroy)

### Activity Flow

```
LaunchActivity (entry point, Theme.AppCompat.NoActionBar)
  ├─→ MainActivity (TransparentTheme) → GameCoreView (full-screen immersive game)
  ├─→ HistoryActivity (Theme.Aircraft) → HistoryFragment → RecyclerView
  └─→ SettingsActivity → SettingsFragment
       └─→ PrivacyPolicyActivity (WebView)
```

### Audio

`MusicService` is a bound Service using `MediaPlayer` for looping background music and `SoundPool` (max 5 streams) for low-latency sound effects. Sound IDs are hex constants (0x002-0x005: fire, be_hit, enemy_be_hit, game_over). `MainActivity` binds to it and observes readiness via `MainActivityViewModel` LiveData. Sound toggles (background_sound, combat_sound) are controlled via SharedPreferences.

### Threading Model

- **Main thread:** Activity lifecycle, UI, service binding, database access via `lifecycleScope`
- **Game thread:** Dedicated thread in `GameCoreView` for the 30 FPS render loop (synchronized on SurfaceHolder)
- **Service:** `MusicService` bound service with @Synchronized playback methods

### Key Naming Collision

There are two files named `Aircraft.kt`:
- `data/Aircraft.kt` — data class with `name`, `health_points`, `lethality`, `icon`, and `isAlive()` check
- `ui/Aircraft.kt` — rendering class extending `DrawBaseObject`, manages player sprite and bullet firing

Code uses `import com.young.aircraft.data.Aircraft as AircraftData` to disambiguate.

### Themes

- `TransparentTheme` (app default) — translucent window background, used by game activities
- `Theme.Aircraft` — Material DayNight.DarkActionBar, solid background, used by HistoryActivity
- Activities that need a solid background **must** explicitly set `android:theme="@style/Theme.Aircraft"` in the manifest

### Bitmap Density

Player bullets set `bitmap.density = screenDensity` for canvas density scaling. Enemy bullets must do the same to render at matching visual size. Both use 25dp bitmaps; enemy bullets use `bullet_up.png` rotated 180 degrees with a red `ColorMatrixColorFilter`.

### Game Assets

- 10 enemy sprites: `enemy_1.png` through `enemy_10.png` (all loaded in `Enemies.init{}`)
- 2 player sprites: `jet_plane.png`, `jet_plane_1.png`
- 6 audio files in `res/raw/`: background music (x2), fire, be_hit, enemy_be_hit, game_over
- Localization: English (default) and Chinese (`values-zh/strings.xml`)
