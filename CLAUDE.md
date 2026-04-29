# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Aircraft is a 2D vertical-scrolling shooter game for Android, written in Kotlin. The player controls a jet plane, fires bullets upward, and destroys enemies while avoiding collisions. The game has 10 time-based levels with scaling difficulty and a boss fight at the end of each level.

For detailed documentation (formulas, database schema, common tasks like adding enemies/sounds/languages, and how to play), see **[DOCUMENT.md](DOCUMENT.md)**.

## Build Commands

```bash
./gradlew assembleDebug          # Build debug APK
./gradlew assembleRelease        # Build release APK
./gradlew test                   # Run unit tests (Robolectric + JUnit)
./gradlew testDebugUnitTest --tests "com.young.aircraft.ExampleUnitTest"  # Single test class
./gradlew connectedAndroidTest   # Instrumented tests (requires device/emulator)
./gradlew clean                  # Clean build
./gradlew lint                   # Lint check
./gradlew lintDebug              # Lint debug variant only (matches CI)
```

## Build Configuration

- **Gradle:** 9.4.1, AGP 9.1.1 (bundles Kotlin — do NOT add `org.jetbrains.kotlin.android` plugin separately), KSP 2.1.20-1.0.32
- **Build files:** Groovy DSL (`build.gradle`, not `.gradle.kts`)
- **SDK:** compileSdk 37, minSdk 30, targetSdk 36, buildToolsVersion 36.0.0
- **Java:** 17
- **Room:** 2.8.4
- **Compose BOM:** 2026.04.01 (material3, foundation, activity-compose 1.13.0)
- **Firebase:** BOM 34.12.0 (Analytics + Crashlytics)
- **ZXing:** 3.5.4 (QR code generation/decoding)
- **Networking:** Retrofit 3.0.0, OkHttp 5.3.2
- **Test stack:** JUnit 4.13.2, Robolectric 4.16.1, Mockito 5.23.0/Kotlin 6.3.0, Compose UI test
- **App ID:** `com.young.aircraft`
- View Binding and Data Binding are both enabled
- `android.disallowKotlinSourceSets=false` in gradle.properties (required for KSP compatibility with AGP's built-in Kotlin)
- Release signing reads from `keystore.properties` in project root (not checked in)

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

Most GUI activities use ViewBinding with XML layouts. Only `AboutMeActivity` and `OnboardingActivity` use Jetpack Compose (`setContent`). Activities like `QRCodeToolActivity`, `HistoryActivity`, `LaunchActivity`, `SettingsActivity`, and `MainActivity` all use ViewBinding.

```
PrivacyPolicyAcceptActivity (entry point, MAIN LAUNCHER, Theme.Aircraft.Common)
  ├─→ [Already accepted?] → skips to OnboardingActivity
  └─→ [Not accepted] → cinematic privacy policy (StarFieldView + WebView)
       ├─→ [Accept] → saves pref → OnboardingActivity
       └─→ [Reject] → finishAffinity() (exits app)

OnboardingActivity (Theme.Aircraft.Common)
  ├─→ [Already completed?] → skips to LaunchActivity
  └─→ [Not completed] → Compose HorizontalPager carousel (ControlsPage + PowerupsPage)
       ├─→ [Skip / Launch] → saves pref → LaunchActivity
       └─→ LaunchActivity

LaunchActivity (Theme.AppCompat.NoActionBar)
  ├─→ [Start Game] → checks DB for saved progress
  │     ├─→ saved data exists (level > 1) → dialog: Continue / New Game
  │     └─→ no saved data → starts at level 1
  ├─→ MainActivity (TransparentMaterialTheme) → GameCoreView (full-screen immersive game)
  │     Intent extras: "start_level" (Int), "jet_plane_res" (Int)
  ├─→ HistoryActivity (Theme.Aircraft.Common) → HistoryFragment → RecyclerView
  ├─→ QRCodeToolActivity (Theme.Aircraft.Common) → QR scan/generate utility
  └─→ SettingsActivity (Theme.Aircraft.Common)
       ├─→ DeviceInfoActivity (device hardware/software info)
       ├─→ AboutAircraftActivity
       ├─→ AboutMeActivity (Compose-based developer profile)
       ├─→ PrivacyPolicyActivity (WebView)
       └─→ DevelopSettingsActivity (debug builds only — crash testing, invincible-mode toggle)
```

### Game State Broadcasting

`GameStateManager` (common/) broadcasts `GameState` enum values (defined in data/GameState.kt: `PLAYING`, `PAUSED`, `GAME_OVER`, `LEVEL_COMPLETE`, `GAME_WON`, `LOW_MEMORY`) via a Kotlin `SharedFlow`. Emit with `GameStateManager.emit(state)`, observe with `GameStateManager.gameState`.

### Difficulty System

User-selectable via SharedPreferences (`"difficulty"` key): Easy (`"1.2"`), Normal (`"1.0"`), Hard (`"0.8"`). The multiplier controls the player's fire rate accumulator in `Aircraft` (ui/). The `GameDifficulty` enum (data/) maps these strings to `fireRateMultiplier` values.

### Audio

`MusicService` is a bound Service using `MediaPlayer` for looping BGM and `SoundPool` (max 5 streams) for SFX. Sound IDs are hex constants (0x002-0x005). Sound toggles (background_sound, combat_sound) via SharedPreferences.

### Threading Model

- **Main thread:** Activity lifecycle, UI, service binding, database access via `lifecycleScope`
- **Game thread:** Dedicated thread in `GameCoreView` for the 30 FPS render loop (synchronized on SurfaceHolder)
- **Service:** `MusicService` bound service with @Synchronized playback methods
- **Never touch game objects from the main thread.** The main thread communicates via callbacks using `post {}`.

### Frozen State (Time Freeze Mechanic)

`TimeFreezes` sets a `frozen: Boolean` property on `Aircraft` (ui/), `Enemies`, and `BossEnemy` each frame. When `frozen = true`, the object skips movement and bullet updates. This is a cross-cutting state — `GameCoreView` propagates it from `TimeFreezes` to all affected objects during the render loop.

### Compose UI Layer

Only `AboutMeActivity` and `OnboardingActivity` use Jetpack Compose (`setContent`) with Material3. There is no shared Compose theme — each uses hardcoded color constants matching the XML tactical theme (BackgroundDark `#0F1118`, AccentGreen `#00FF88`, HeaderBg `#161A26`). `StarFieldView` (a custom Canvas animation view) is wrapped via `AndroidView` composable in activities that need it (PrivacyPolicyAcceptActivity, OnboardingActivity). Tests use `createAndroidComposeRule` with `@GraphicsMode(GraphicsMode.Mode.NATIVE)` for Robolectric Compose testing.

## Key Gotchas

### Naming Collision
Two files named `Aircraft.kt`: `data/PlayerAircraft.kt` (data class, renamed from Aircraft) and `ui/Aircraft.kt` (rendering class). Code disambiguates with `import com.young.aircraft.data.PlayerAircraft as AircraftData`.

### Bitmap Density
All game object bitmaps must have `bitmap.density = screenDensity` set for correct canvas density scaling. Forgetting this causes incorrect rendering sizes.

### Themes
- `TransparentTheme` (app default) — translucent window, AppCompat-based, used as the application-level theme
- `TransparentMaterialTheme` — translucent window with MaterialComponents, used by MainActivity (game host)
- `Theme.Aircraft.Common` — MaterialComponents DayNight NoActionBar with dark background (#1B1F2B), used by most non-game activities
- `Theme.Aircraft` — MaterialComponents DayNight DarkActionBar, not currently assigned to any activity in the manifest
- Activities needing a solid background **must** set `android:theme="@style/Theme.Aircraft.Common"` in the manifest

### Localization
English (default) and Chinese (`values-zh/strings.xml`). A `StringResourceTest` verifies locale parity and usage coverage — when adding/removing strings, ensure both locales stay in sync to avoid test failures. Unused strings in `strings.xml` will also cause test failures; clean up orphans after refactors.

### CI
GitHub Actions (`.github/workflows/android.yml`) runs `./gradlew assembleDebug lintDebug` on push/PR to `main`, using JDK 17 (temurin). Note: CI does **not** run unit tests — only compile and lint.

### Settings & Debug
`SettingsRepository` (providers/) wraps SharedPreferences for difficulty, sound toggles, privacy acceptance, hit-shake effect, and a debug invincible-mode flag. `GameStateManager.isInvincible` exposes this flag to the game loop. Debug builds expose `DevelopSettingsActivity` (crash testing, invincible-mode toggle) from `SettingsActivity`.

### Firebase
Firebase Analytics and Crashlytics are integrated via the Firebase BOM. The `google-services.json` config file is required in `app/` for Firebase to initialize. Crashlytics plugin is applied in `app/build.gradle`.

### QR Code Inverted Colors
Generated QR codes use **white modules on dark background** (`#0F1118`) for the tactical theme. This is the inverse of standard QR codes (black on white). When decoding these images from file, ZXing's binarizer must try `source.invert()` as a fallback, otherwise decode always fails. See `decodeQrFromBitmap()` in `QRCodeToolActivity`.

### Bottom Sheet Dialogs
`BottomSheetDialog` with transparent background is used in `MainActivity` (hall of heroes) and `QRCodeToolActivity` (scan results). Each has a matching `ThemeOverlay` style in `themes.xml` and a custom layout in `res/layout/bottom_sheet_*.xml`. The pattern: create dialog with theme → inflate layout → `setContentView` → set `design_bottom_sheet` background to transparent in `setOnShowListener`.

### Standard Activity Header Pattern
Non-game activities share a consistent header: **52dp RelativeLayout** (`#161A26` background) with a 48dp back ImageButton (start-aligned) and a centered title TextView (`#00FF88`, 16sp, bold, monospace, letterSpacing 0.25). A 1dp green divider (`#4400FF88`) separates it from content. The root layout uses `android:fitsSystemWindows="true"` for status bar handling — do NOT use manual `WindowCompat.setDecorFitsSystemWindows(window, false)` + inset listeners in these activities.

### FileProvider
A `FileProvider` is registered in the manifest with authority `${applicationId}.fileprovider`. Path configuration in `res/xml/file_paths.xml` exposes `external-files-path` (Pictures/) and `cache-path`. The `FilePickerHelper` utility (utils/) provides `getUriForFile()`, `createQrImageFile()`, `copyUriToCache()`, and `queryFileInfo()`. Used by `QRCodeToolActivity` for sharing QR codes via `Intent.ACTION_SEND` with `FLAG_GRANT_READ_URI_PERMISSION`.
