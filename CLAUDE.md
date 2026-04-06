# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Aircraft is a 2D vertical-scrolling shooter game for Android, written in Kotlin. The player controls a jet plane, fires bullets upward, and destroys enemies while avoiding collisions. The game has 10 time-based levels with scaling difficulty and a boss fight at the end of each level.

For detailed documentation (formulas, database schema, common tasks like adding enemies/sounds/languages, and how to play), see **[DOCUMENT.md](DOCUMENT.md)**.

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

- **Gradle:** 9.4.1, AGP 9.1.0 (bundles Kotlin — do NOT add `org.jetbrains.kotlin.android` plugin separately), KSP 2.1.20-1.0.32
- **SDK:** compileSdk 36, minSdk 30, targetSdk 36, buildToolsVersion 36.0.0
- **Java:** 17
- **Room:** 2.8.4
- **App ID:** `com.young.aircraft`
- View Binding and Data Binding are both enabled
- `android.disallowKotlinSourceSets=false` in gradle.properties (required for KSP compatibility with AGP's built-in Kotlin)

## Architecture

### Game Engine (SurfaceView-based)

The game runs on a custom `SurfaceView` (`GameCoreView`) with a dedicated rendering thread at 30 FPS. This is **not** a Compose or standard View-based UI — it draws directly to a `Canvas`.

**Rendering hierarchy — all extend `DrawBaseObject` (abstract base with `onDraw`, `updateGame`, `getEnemyBounds`):**
- `GameCoreView` (SurfaceView + Runnable) — owns the game loop, coordinates all drawing, collision detection, and level progression
- `Aircraft` (ui/) — player jet with auto-firing bullets, touch-based movement
- `DrawBackground` — seamless double-buffer scrolling background
- `DrawHeader` — HUD overlay (level, HP, timer, kill count)
- `Enemies` — timed row spawning with per-enemy Y tracking, 15 sprite types
- `BossEnemy` — end-of-level boss with AI movement, bomb attacks, multi-explosion death
- `RedEnvelopes` — collectible power-up: gift boxes that launch AoE rockets on detonation
- `MedicalKits` — collectible health pickups: heart items that restore HP to max
- `Shields` — collectible shield power-up: grants 10s invincibility with blink effect (spawns once per level, probability 90%→5% by level)
- `TimeFreezes` — collectible time freeze: player pickup freezes enemies for 5s, enemy pickup freezes player for 5s (max 3 per level, probability 80%→20% by level)
- `ExplosionEffect` — particle-based death animation (flash, fireball, debris, smoke phases)

### Level System (Time-Based + Boss)

Level duration **decreases** with progression (300s→120s); required kills **increase** (100→190). After the kill target is met, a Boss spawns — the level only completes when the Boss is defeated. Timer pauses during boss fights. All formulas are in `GameCoreView` and `Enemies` companion objects.

### Collision Detection

Twelve checks run every frame in `GameCoreView.checkCollision()`:
1. Player vs enemy sprites (RectF intersection, with cooldown)
2. Enemy bullets vs player (shield absorbs)
3. Player bullets vs enemies (increments both `enemiesDestroyedThisLevel` and `totalKills`)
4. Player bullets vs red envelopes (consumed on hit, rocket on detonation)
5. Rockets vs enemies (AoE blast on impact)
6. Player vs boss body (instant death; shield absorbs)
7. Boss bombs vs player (proximity detonation, 20 HP damage; shield absorbs)
8. Player bullets vs boss (10 damage per hit)
9. Rockets vs boss (10 damage via `bossEnemy.hitBoss()`)
10. Medical kit pickup (player or boss can collect → HP restored to max)
11. Shield pickup (bullet consumed, 10s invincibility activated)
12. Time freeze pickup (player pickup → enemies frozen 5s; enemy pickup → player frozen 5s)

### Scoring & Persistence (Room Database)

- **Score:** 100 points per kill, cumulative across all levels in a session
- **Database:** Room (`AppDatabase`, version 2030) with `fallbackToDestructiveMigration(true)`. Migrations exist for 2027→2028→2029→2030. Table: `player_game_data` (playerId, level, score, jetPlaneRes, difficulty, timestamp). Access via `DatabaseProvider` singleton (providers/).
- **Critical:** `finish()` must be called inside the coroutine *after* the DB write completes in `saveGameData()`, never alongside — otherwise `lifecycleScope` cancels the write.

### Activity Flow

```
PrivacyPolicyAcceptActivity (entry point, MAIN LAUNCHER, Theme.Aircraft.History)
  ├─→ [Already accepted?] → skips to OnboardingActivity
  └─→ [Not accepted] → cinematic privacy policy (StarFieldView + WebView)
       ├─→ [Accept] → saves pref → OnboardingActivity
       └─→ [Reject] → finishAffinity() (exits app)

OnboardingActivity (Theme.Aircraft.History)
  ├─→ [Already completed?] → skips to LaunchActivity
  └─→ [Not completed] → 2-screen carousel (controls + power-ups)
       ├─→ [Skip / Launch] → saves pref → LaunchActivity
       └─→ LaunchActivity

LaunchActivity (Theme.AppCompat.NoActionBar)
  ├─→ [Start Game] → checks DB for saved progress
  │     ├─→ saved data exists (level > 1) → dialog: Continue / New Game
  │     └─→ no saved data → starts at level 1
  ├─→ MainActivity (TransparentTheme) → GameCoreView (full-screen immersive game)
  │     Intent extras: "start_level" (Int), "jet_plane_res" (Int)
  ├─→ HistoryActivity (Theme.Aircraft) → HistoryFragment → RecyclerView
  └─→ SettingsActivity (custom layout, no fragment)
       ├─→ DeviceInfoActivity (device hardware/software info)
       ├─→ AboutAircraftActivity
       └─→ PrivacyPolicyActivity (WebView)
```

### Game State Broadcasting

`GameStateManager` (common/) broadcasts `GameState` enum values (defined in data/GameState.kt: `PLAYING`, `PAUSED`, `GAME_OVER`, `LEVEL_COMPLETE`, `GAME_WON`, `LOW_MEMORY`) via a Kotlin `SharedFlow`. Emit with `GameStateManager.emit(state)`, observe with `GameStateManager.gameState`.

### Difficulty System

User-selectable via SharedPreferences (`"difficulty"` key): Easy (`"1.2"`), Normal (`"1.0"`), Hard (`"0.8"`). The multiplier controls the player's fire rate accumulator in `Aircraft` (ui/).

### Audio

`MusicService` is a bound Service using `MediaPlayer` for looping BGM and `SoundPool` (max 5 streams) for SFX. Sound IDs are hex constants (0x002-0x005). Sound toggles (background_sound, combat_sound) via SharedPreferences.

### Threading Model

- **Main thread:** Activity lifecycle, UI, service binding, database access via `lifecycleScope`
- **Game thread:** Dedicated thread in `GameCoreView` for the 30 FPS render loop (synchronized on SurfaceHolder)
- **Service:** `MusicService` bound service with @Synchronized playback methods
- **Never touch game objects from the main thread.** The main thread communicates via callbacks using `post {}`.

### Frozen State (Time Freeze Mechanic)

`TimeFreezes` sets a `frozen: Boolean` property on `Aircraft` (ui/), `Enemies`, and `BossEnemy` each frame. When `frozen = true`, the object skips movement and bullet updates. This is a cross-cutting state — `GameCoreView` propagates it from `TimeFreezes` to all affected objects during the render loop.

## Key Gotchas

### Naming Collision
Two files named `Aircraft.kt`: `data/PlayerAircraft.kt` (data class, renamed from Aircraft) and `ui/Aircraft.kt` (rendering class). Code disambiguates with `import com.young.aircraft.data.PlayerAircraft as AircraftData`.

### Bitmap Density
All game object bitmaps must have `bitmap.density = screenDensity` set for correct canvas density scaling. Forgetting this causes incorrect rendering sizes.

### Themes
- `TransparentTheme` (app default) — translucent window, used by game activities
- `Theme.Aircraft` — Material DayNight.DarkActionBar, solid background, used by HistoryActivity
- Activities needing a solid background **must** set `android:theme="@style/Theme.Aircraft"` in the manifest

### Localization
English (default) and Chinese (`values-zh/strings.xml`).

### CI
GitHub Actions (`.github/workflows/android.yml`) runs `./gradlew build` (compile + unit tests + lint) on push/PR to `main`, using JDK 17 (temurin).

### Settings & Debug
`SettingsRepository` (providers/) wraps SharedPreferences for difficulty, sound toggles, privacy acceptance, hit-shake effect, and a debug invincible-mode flag. `GameStateManager.isInvincible` exposes this flag to the game loop. Debug builds expose `DevelopSettingsActivity` (crash testing, invincible-mode toggle) from `SettingsActivity`.
