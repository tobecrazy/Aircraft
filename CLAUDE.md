# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Aircraft is a 2D vertical-scrolling shooter game for Android, written in Kotlin. The player controls a jet plane, fires bullets upward, and destroys enemies while avoiding collisions. The game has 10 time-based levels with decreasing time limits, scaling difficulty, and a boss fight at the end of each level.

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
  - `Aircraft` (ui/) — player jet with auto-firing bullets (fire rate affected by difficulty), touch-based movement
  - `DrawBackground` — seamless double-buffer scrolling background (3 backgrounds, randomized on level advance)
  - `DrawHeader` — HUD overlay showing level, HP, timer countdown, kill count
  - `Enemies` — timed row spawning with per-enemy Y tracking, 15 sprite types, red-tinted bullets
  - `RedEnvelopes` — collectible power-up: spawns drifting gift boxes, launches rockets on detonation
  - `BossEnemy` — end-of-level boss with AI movement, bomb attacks, and dramatic multi-explosion death
  - `MedicalKits` — collectible health pickups: spawns heart items, restores player HP to max on collection
  - `Shields` — collectible shield power-up: grants 10s invincibility with blink effect
  - `ExplosionEffect` — particle-based death animation (flash, fireball, debris, smoke phases)

### Level System (Time-Based + Boss)

Defined in `GameCoreView` companion object. Level duration **decreases** with progression; required kills **increase**:
- **Duration:** 300s - 20s*(level-1) (300s at level 1, down to 120s at level 10)
- **Required kills:** 90 + level*10 (100 at level 1, up to 190 at level 10)
- **Boss:** After the kill target is met, a Boss spawns. The level only completes when the Boss is defeated. The timer is paused during the boss fight.

Enemy stats scale with level:
- **Enemies per row:** 5 + level (in `Enemies.getEnemiesPerRow()`)
- **Spawn interval:** 90 - 5*(level-1) frames (in `Enemies.getSpawnIntervalFrames()`)
- **Bullet spacing:** 350 - 15*(level-1) dp, min 250dp (in `Enemies.getBulletSpacingDp()`)
- **Move speed:** 3 + 1.5*(level-1) (in `Enemies.getEnemyMoveSpeed()`)

Enemies have 1 HP and are destroyed in a single hit. Player has 100 HP and loses 20 per hit.

### Boss System

`BossEnemy` (ui/) manages the end-of-level boss. State is tracked in `BossState` (data/).

- **Spawning:** When the kill target is reached, `checkKillTarget()` calls `bossEnemy.spawnBoss(level)` and pauses enemy spawning (`enemies.spawnPaused = true`).
- **HP:** 1000 + 100*(level-1). Each player bullet hit deals 10 damage. Level 1 = 100 hits, level 10 = 190 hits.
- **Movement AI:** Targets the upper 8–30% of screen height. Advances from off-screen into the target zone, retreats if pushed too low, drifts laterally with periodic direction changes. Speed = 1.5× enemy speed for the current level.
- **Bombs:** Fires `BossBomb` projectiles straight down. Fire interval scales by level: `50 / (1 + 0.2*(level-1))` frames (faster at higher levels). Bombs detonate when their center comes within 5dp of the player aircraft, creating a 20% screen-size explosion and dealing 20 HP damage.
- **Collision:** Boss body contact with the player = instant death (HP set to 0, death explosion).
- **Death:** When HP reaches 0, 5 staggered `ExplosionEffect` instances play (scale up to 3×). The boss is considered expired 3.5s after destruction, after which the level completes.
- **Sprites:** 5 boss sprites (`boss_1`–`boss_5`, 350dp), 3 missile sprites (`missile_1`–`missile_3`, 60dp), randomly selected on spawn.

### Difficulty System

User-selectable difficulty via SharedPreferences (`"difficulty"` key), configured in Settings:
- **Easy:** `"1.2"` — faster fire rate (1.2× accumulator per frame)
- **Normal:** `"1.0"` — default fire rate
- **Hard:** `"0.8"` — slower fire rate (0.8× accumulator per frame)

The difficulty multiplier is read in `initializeGameDrawer()` and passed to `Aircraft(context, speed, jetPlaneResId, fireRateMultiplier)`. The fire system uses an accumulator: `fireAccumulator += fireRateMultiplier`, firing when it reaches `FIRE_INTERVAL (4)`. Difficulty is persisted in `PlayerGameData.difficulty`.

### Red Envelope Power-Up System

`RedEnvelopes` (ui/) manages collectible power-ups. State in `RedEnvelopeState` and `RocketState` (data/).

- **Spawning:** 1 envelope every 300 frames (~10s), max 2 on screen. Random X, drifts downward at 2 dp/frame.
- **Hit mechanic:** 3 HP. Player bullets decrement HP and are consumed. Sprites cycle: `red_box_1` (3 HP) → `red_box_3` (2–1 HP) → `red_box_2` (0 HP flash). White hit flash for 100ms.
- **Rocket:** On detonation (0 HP), a rocket launches from the player's aircraft center, flying upward at 20 dp/frame.
- **AoE explosion:** When a rocket hits an enemy (or the boss), it deactivates and creates a blast area of 20% of `min(screenW, screenH)` as a square. All enemies with bounds intersecting the blast are destroyed. Kill counters increment for each.
- **Level reset:** `redEnvelopes.clearAll()` on `advanceToNextLevel()`.

### Medical Kit Power-Up System

`MedicalKits` (ui/) manages health pickups. State in `MedicalKitState` (data/).

- **Spawning:** Interval = 450 + 150*(level-1) frames. Max kits per level = max(0, 2 - (level-1)/4). Max 1 uncollected kit on screen at a time. Random position in the middle 15–70% of screen height.
- **Lifetime:** 450 frames (15s at 30 FPS). Blinks in the last 5 seconds (toggling alpha every 6 frames).
- **Sprites:** 2 heart sprites (`red_heart_1.png`, `red_heart_2.png`), 120dp, randomly selected on spawn.
- **Player pickup:** When the player is not at full HP and overlaps the kit, HP is restored to max (100).
- **Boss pickup:** If the player doesn't collect it, the boss can also pick it up to restore its HP to max.
- **Level reset:** `medicalKits.clearAll()` on `advanceToNextLevel()`.

### Shield Power-Up System

`Shields` (ui/) manages invincibility pickups. State in `ShieldState` (data/).

- **Spawning:** One spawn attempt per level, after a 300-frame delay (10s). Spawn probability decreases with level: 90% at level 1, 50% at level 5, 5% at level 10. Formula: `max(0.05, 1.0 - (level-1) * 0.1)`. Random position in middle 15–65% of screen height.
- **Lifetime:** 450 frames (15s at 30 FPS). Blinks in the last 5 seconds (toggling alpha every 6 frames).
- **Sprites:** 3 shield sprites (`shield_1.png`, `shield_2.png`, `shield_3.png`), 100dp, randomly selected on spawn.
- **Collection:** Player bullets hitting the shield collect it (bullet consumed). Activates 10-second invincibility (`SHIELD_DURATION_MS = 10_000L` in `Aircraft` ui/). While shielded, the player jet blinks every 4 frames.
- **Protection:** Shield absorbs enemy bullet hits, boss body collisions, and boss bomb hits — all checked via `drawAircraft.isShielded()` in `GameCoreView.checkCollision()`.
- **Level reset:** `shields.clearAll()` and `drawAircraft.shieldEndTimeMs = 0L` on `advanceToNextLevel()`.

### Collision Detection

Ten checks run every frame in `GameCoreView.checkCollision()`:
1. Player aircraft vs enemy sprites (RectF intersection, with cooldown)
2. Enemy bullets vs player (`getEnemyBullets()` returns `Triple<x, y, EnemyBullet>` for removal by reference; shield absorbs hit)
3. Player bullets vs enemies (iterates `activeEnemies`, increments kill counters — both `enemiesDestroyedThisLevel` and `totalKills` — on destroy)
4. Player bullets vs red envelopes (remaining bullets checked, consumed on hit, rocket launched on detonation)
5. Rockets vs enemies (AoE blast on first enemy impact)
6. Player aircraft vs boss body (instant player death; shield absorbs)
7. Boss bombs vs player (proximity detonation within 5dp, 20% screen blast, 20 HP damage; shield absorbs)
8. Player bullets vs boss (10 damage per hit)
9. Rockets vs boss (10 damage per hit via `bossEnemy.hitBoss()`)
10. Medical kit pickup (player collects if not at full HP → HP restored to max; boss collects if active and not at max HP → boss HP restored to max)
11. Shield pickup (player bullets hitting shield → bullet consumed, shield collected, 10s invincibility activated)

### Scoring & Persistence (Room Database)

- **Score:** 100 points per kill, cumulative across all levels in a session. Reset when switching users.
- **Player ID:** Device's `Settings.Secure.ANDROID_ID`
- **Database:** Room (`AppDatabase`, version 2028) with `fallbackToDestructiveMigration(true)` and `MIGRATION_2027_2028` (adds `difficulty` column). Table: `player_game_data` (playerId, level, score, jetPlaneRes, difficulty, timestamp). Database access is via `DatabaseProvider` singleton (providers/).
- **DAO:** `PlayerGameDataDao` — insert, query by player, query all sorted by score DESC, get total score, delete by player.
- Game data is saved via a `suspend` function `saveGameData()` called from `lifecycleScope` in `MainActivity`. **Important:** `finish()` must be called inside the coroutine *after* the DB write completes, never alongside — otherwise `lifecycleScope` cancels the write.
- On game over, an `AlertDialog` asks the user to save or discard progress. On save, the level and jet plane selection are persisted so the player can continue later.

### Enemy System (Per-Enemy Y Tracking)

Enemies use individual Y positions (`EnemyState.y`) — multiple rows coexist on screen simultaneously. Each `EnemyState` tracks its own position, health, destruction time, and bullet list (`MutableList<EnemyBullet>`). `EnemyBullet` stores both current Y and origin Y for 60% screen-height range limiting. Enemy spawning can be paused via `Enemies.spawnPaused` (set `true` during boss fights).

### Activity Flow

```
LaunchActivity (entry point, Theme.AppCompat.NoActionBar)
  ├─→ [Start Game] → checks DB for saved progress
  │     ├─→ saved data exists (level > 1) → dialog: Continue (saved level + jet) / New Game
  │     └─→ no saved data → starts at level 1
  ├─→ MainActivity (TransparentTheme) → GameCoreView (full-screen immersive game)
  │     Intent extras: "start_level" (Int, default 1), "jet_plane_res" (Int, default jet_plane)
  ├─→ HistoryActivity (Theme.Aircraft) → HistoryFragment → RecyclerView
  └─→ SettingsActivity → SettingsFragment
       ├─→ DeviceInfoActivity (device hardware/software info display)
       └─→ PrivacyPolicyActivity (WebView)
```

### Jet Plane Selection

Players choose their jet on `LaunchActivity` by tapping the plane image (cycles through `jet_plane_2`, `jet_plane_3`, `jet_plane_4`, `jet_plane_1`). The selected resource ID flows:
- `LaunchActivity` → intent extra `"jet_plane_res"` → `MainActivity` → `GameCoreView.jetPlaneResId` → `Aircraft(context, speed, jetPlaneResId, fireRateMultiplier)`
- Saved to DB in `PlayerGameData.jetPlaneRes` so continuing a game restores the same jet. Default fallback is `jet_plane_2`.

### Audio

`MusicService` is a bound Service using `MediaPlayer` for looping background music and `SoundPool` (max 5 streams) for low-latency sound effects. Sound IDs are hex constants (0x002-0x005: fire, be_hit, enemy_be_hit, game_over). `MainActivity` binds to it and observes readiness via `MainActivityViewModel` LiveData. Sound toggles (background_sound, combat_sound) are controlled via SharedPreferences.

### Threading Model

- **Main thread:** Activity lifecycle, UI, service binding, database access via `lifecycleScope`
- **Game thread:** Dedicated thread in `GameCoreView` for the 30 FPS render loop (synchronized on SurfaceHolder)
- **Service:** `MusicService` bound service with @Synchronized playback methods

### Game State Broadcasting

`GameStateManager` (common/) is a singleton that broadcasts `GameState` enum values (`PLAYING`, `PAUSED`, `GAME_OVER`, `LEVEL_COMPLETE`, `GAME_WON`, `LOW_MEMORY`) via a Kotlin `SharedFlow`. Game components emit state changes with `GameStateManager.emit(state)`, and observers collect from `GameStateManager.gameState`.

### Key Naming Collision

There are two files named `Aircraft.kt`:
- `data/Aircraft.kt` — data class with `name`, `health_points`, `lethality`, `icon`, `isAlive()`, `hit()`, `restoreHealth()`, and `isFullHealth()`
- `ui/Aircraft.kt` — rendering class extending `DrawBaseObject`, manages player sprite and bullet firing

Code uses `import com.young.aircraft.data.Aircraft as AircraftData` to disambiguate.

### Themes

- `TransparentTheme` (app default) — translucent window background, used by game activities
- `Theme.Aircraft` — Material DayNight.DarkActionBar, solid background, used by HistoryActivity
- Activities that need a solid background **must** explicitly set `android:theme="@style/Theme.Aircraft"` in the manifest

### Bitmap Density

All game object bitmaps must have `bitmap.density = screenDensity` set for correct canvas density scaling. This applies to player bullets, enemy bullets, red envelope sprites, rocket sprites, boss sprites, missile sprites, medical kit sprites, and shield sprites. Forgetting this causes incorrect rendering sizes.

### Game Assets

- 15 enemy sprites: `enemy_1.png` through `enemy_15.png` (all loaded in `Enemies.init{}`)
- 4 player sprites: `jet_plane_1.png` through `jet_plane_4.png` (selectable on launch screen, persisted in DB; default `jet_plane_2`)
- 5 boss sprites: `boss_1.png` through `boss_5.png` (randomly selected on boss spawn)
- 3 missile sprites: `missile_1.png` through `missile_3.png` (boss bombs, randomly selected)
- 3 red envelope sprites: `red_box_1.png` (closed), `red_box_3.png` (ribbon/hit), `red_box_2.png` (open/detonated)
- 1 rocket sprite: `rocket.png` (launched from player on envelope detonation)
- 2 medical kit sprites: `red_heart_1.png`, `red_heart_2.png` (120dp, randomly selected on spawn)
- 3 shield sprites: `shield_1.png`, `shield_2.png`, `shield_3.png` (100dp, randomly selected on spawn)
- 3 background images: `background.jpg`, `background_1.jpg`, `background_2.jpg` (randomized on init and level advance)
- 6 audio files in `res/raw/`: background music (x2), fire, be_hit, enemy_be_hit, game_over
- Localization: English (default) and Chinese (`values-zh/strings.xml`)
