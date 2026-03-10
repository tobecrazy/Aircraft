# Aircraft - Developer Document

## 1. Technology Stack

### Platform & Language

| Category          | Technology                  |
|-------------------|-----------------------------|
| Platform          | Android                     |
| Language          | Kotlin 2.1.20 (bundled with AGP) |
| Min SDK           | 30 (Android 11)             |
| Target SDK        | 35 (Android 15)             |
| Compile SDK       | 36                          |
| Java Compatibility| 17                          |

### Build System

| Component              | Version        |
|------------------------|----------------|
| Gradle                 | 9.3.1          |
| Android Gradle Plugin  | 9.1.0          |
| KSP (Annotation Proc.) | 2.1.20-1.0.32 |
| Build Tools            | 34.0.0         |

### Core Libraries

| Library                        | Version  | Purpose                            |
|--------------------------------|----------|------------------------------------|
| AndroidX Core KTX              | 1.17.0   | Kotlin extensions for Android APIs |
| AndroidX AppCompat             | 1.7.1    | Backward-compatible UI components  |
| Material Components            | 1.13.0   | Material Design UI widgets         |
| ConstraintLayout               | 2.2.1    | Flexible layout system             |
| Room (runtime + ktx + compiler)| 2.7.1    | Local SQLite database via ORM      |
| Lifecycle ViewModel KTX        | 2.10.0   | Lifecycle-aware ViewModel          |
| Preference KTX                 | 1.2.1    | Settings/preferences framework     |
| AndroidX Media                 | 1.7.1    | Media playback support             |

### Testing

| Library             | Version | Purpose              |
|---------------------|---------|----------------------|
| JUnit               | 4.x     | Unit testing         |
| AndroidX Test JUnit | 1.3.0   | Android test runner  |
| Espresso            | 3.7.0   | UI instrumented tests|

### Build Features

- **View Binding** — Type-safe view references
- **Data Binding** — Declarative layout binding
- **BuildConfig** — Generated build constants

### Game Engine

The game does **not** use any third-party game framework. It is built entirely on Android's native `SurfaceView` + `Canvas` API with a custom 30 FPS render loop running on a dedicated thread.

---

## 2. Quick Start

### Prerequisites

- Android Studio Meerkat (2024.3.1) or later
- Android device or emulator running API 30+

### Setup

```bash
git clone <repository-url>
cd Aircraft
```

Open in Android Studio, sync Gradle, and run on a device/emulator.

### Build Commands

```bash
./gradlew assembleDebug          # Build debug APK
./gradlew assembleRelease        # Build release APK
./gradlew test                   # Run all unit tests
./gradlew testDebugUnitTest --tests "com.young.aircraft.ExampleUnitTest"  # Single test
./gradlew connectedAndroidTest   # Instrumented tests (requires device/emulator)
./gradlew clean                  # Clean build
./gradlew lint                   # Lint check
```

---

## 3. Key Gotchas

**AGP 9.1 bundles Kotlin.** Do NOT add `org.jetbrains.kotlin.android` as a plugin — it will cause `Cannot add extension with name 'kotlin'` at build time. Only KSP is declared as a separate plugin.

**Two files named `Aircraft.kt` exist.** `data/Aircraft.kt` is the player stats data class; `ui/Aircraft.kt` is the rendering class. Code disambiguates with:
```kotlin
import com.young.aircraft.data.Aircraft as AircraftData
```

**KSP + built-in Kotlin requires a compat flag.** `gradle.properties` must include `android.disallowKotlinSourceSets=false` or KSP source sets will fail to register.

**Room database uses destructive migration.** `AppDatabase` uses `fallbackToDestructiveMigration()`, so schema changes wipe existing data. Version is currently `2026` (chosen as the project year — simply increment when changing the schema).

**Bullet density must be set.** Both player and enemy bullet bitmaps must have `bitmap.density = screenDensity` or they render at wrong sizes. Both use 25dp bitmaps.

---

## 4. Project Structure

```
app/src/main/java/com/young/aircraft/
├── common/
│   └── AircraftApplication.kt          # Application entry point
│
├── data/                                # ── Data Layer ──
│   ├── AppDatabase.kt                   # Room database singleton (v2026)
│   ├── PlayerGameData.kt               # Entity: player_game_data table
│   ├── PlayerGameDataDao.kt            # DAO: CRUD for game records
│   ├── Aircraft.kt                      # Data model: player HP & stats
│   └── EnemyState.kt                   # Data model: enemy state & bullets
│
├── gui/                                 # ── Presentation Layer ──
│   ├── LaunchActivity.kt               # Home screen (Start / History / Settings)
│   ├── MainActivity.kt                 # Game host, binds MusicService
│   ├── HistoryActivity.kt              # History screen container
│   ├── HistoryFragment.kt              # Game history list (RecyclerView)
│   ├── HistoryAdapter.kt               # RecyclerView adapter for records
│   ├── SettingsActivity.kt             # Sound & privacy preferences
│   └── PrivacyPolicyActivity.kt        # Privacy policy (WebView)
│
├── ui/                                  # ── Game Engine Layer ──
│   ├── GameCoreView.kt                 # SurfaceView: game loop & orchestration
│   ├── DrawBaseObject.kt               # Abstract base for drawable objects
│   ├── Aircraft.kt                      # Player jet: rendering & bullet firing
│   ├── Enemies.kt                       # Enemy spawning, movement & bullets
│   ├── DrawBackground.kt               # Seamless scrolling background
│   ├── DrawHeader.kt                   # HUD: level, HP bar, timer, kills
│   └── ExplosionEffect.kt              # Particle-based death explosion
│
├── service/                             # ── Service Layer ──
│   └── MusicService.kt                 # Bound service: BGM + SFX playback
│
├── viewmodel/                           # ── ViewModel Layer ──
│   └── MainViewModel.kt                # LiveData for service readiness
│
└── utils/                               # ── Utilities ──
    ├── ScreenUtils.kt                   # Screen dimensions, dp/sp/px conversion
    └── BitmapUtils.kt                   # Bitmap loading, resizing, rotation
```

### Architecture Diagram

```
┌─────────────────────────────────────────────────────────┐
│                     LaunchActivity                       │
│              [Start Game] [History] [Settings]            │
└─────┬──────────────┬──────────────────┬─────────────────┘
      │              │                  │
      ▼              ▼                  ▼
┌───────────┐  ┌────────────┐   ┌──────────────┐
│ MainActivity│  │HistoryAct. │   │SettingsAct.  │
│            │  │            │   │              │
│ ┌────────┐ │  │ ┌────────┐ │   │ Preferences  │
│ │GameCore│ │  │ │History │ │   │    Fragment   │
│ │  View  │ │  │ │Fragment│ │   └──────┬───────┘
│ └───┬────┘ │  │ └───┬────┘ │          │
└─────┼──────┘  └─────┼──────┘   ┌──────▼───────┐
      │               │          │PrivacyPolicy │
      │               │          │   Activity    │
      │               │          └──────────────┘
      │               │
      ▼               ▼
┌─────────────────────────────────┐
│         Data Layer              │
│  ┌───────────┐ ┌─────────────┐  │
│  │ AppDatabase│ │SharedPrefs  │  │
│  │  (Room)   │ │  (Sound)    │  │
│  └─────┬─────┘ └─────────────┘  │
│        │                        │
│  ┌─────▼──────────────┐         │
│  │ PlayerGameDataDao  │         │
│  │ (insert/delete/    │         │
│  │  query by score)   │         │
│  └────────────────────┘         │
└─────────────────────────────────┘

┌─────────────────────────────────────────────┐
│            Game Engine (ui/)                 │
│                                             │
│  GameCoreView (SurfaceView, 30 FPS)         │
│  ┌─────────┐ ┌────────┐ ┌──────────┐       │
│  │Aircraft │ │Enemies │ │DrawHeader│       │
│  │(player) │ │(spawn, │ │  (HUD)   │       │
│  │+ bullets│ │ move,  │ └──────────┘       │
│  └─────────┘ │ shoot) │ ┌──────────────┐   │
│              └────────┘ │DrawBackground│   │
│  ┌───────────────────┐  │ (scrolling)  │   │
│  │ ExplosionEffect   │  └──────────────┘   │
│  │ (death particles) │                     │
│  └───────────────────┘                     │
└──────────────────┬──────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────┐
│      MusicService (Bound)       │
│  MediaPlayer (BGM loop)         │
│  SoundPool (fire, hit, explode) │
└─────────────────────────────────┘
```

---

## 5. Game Loop Walkthrough

The entire game runs inside `GameCoreView.run()` on a dedicated thread at 30 FPS. The loop uses `surfaceHolder.lockCanvas()` / `unlockCanvasAndPost()` and sleeps for the remaining frame time (`Thread.sleep(targetTime - elapsed)`) to maintain the target frame rate. Each frame calls `onUpdateGameDraw(canvas)`, which executes this pipeline:

```
1. applyScreenShake(canvas)         ← if player was recently hit, offset the canvas
2. drawBackground(canvas)           ← seamless scrolling background
3. drawHeader(canvas)               ← HUD: level, HP bar, timer, kill count
4. drawAircraft(canvas)             ← player jet + auto-fired bullets (skipped if dying)
5. if (!isPaused && !isPlayerDying):
   a. checkLevelTimer()             ← game over if time expired
   b. drawEnemies(canvas)           ← spawn rows, move enemies, fire enemy bullets
   c. checkCollision()              ← 3-way collision detection (see below)
6. drawDeathExplosion(canvas)       ← particle explosion if player is dying
7. drawDamageFlash(canvas)          ← red flash overlay on hit (fades over 300ms)
8. drawLowHealthVignette(canvas)    ← pulsing red border when HP ≤ 20
9. canvas.restore()                 ← undo shake offset
```

**Key state flags that control the loop:**
- `isPaused` — set `true` on level complete or player death; skips enemy updates and collision
- `isPlayerDying` — set `true` during death explosion; skips aircraft drawing and game logic
- `gameWon` — set `true` when level 10 is cleared

**Callbacks to the Activity (run on main thread via `post {}`):**
- `onGameOver` — time expired or HP reached 0
- `onLevelComplete(level)` — kill target met, level < 10
- `onGameWon` — kill target met on level 10

---

## 6. Collision Detection

Three checks run every frame inside `checkCollision()`:

### 1. Player vs Enemies (body collision)

```
for each activeEnemy (not destroyed):
    if RectF.intersects(aircraftBounds, enemyBounds) AND !collisionCooldown:
        → playerData.hit() (−20 HP)
        → triggerHitEffects() (shake + flash)
        → collisionCooldown = true (resets when enemy no longer overlaps aircraft)
```

### 2. Enemy Bullets vs Player

```
for each enemy bullet (via getEnemyBullets() → Triple<x, y, EnemyBullet>):
    if RectF.intersects(aircraftBounds, bulletBounds):
        → playerData.hit() (−20 HP)
        → remove bullet by reference from enemy's bullet list
        → break (one bullet per frame)
```

### 3. Player Bullets vs Enemies

```
for each player bullet:
    for each activeEnemy (not destroyed):
        if RectF.intersects(bulletBounds, enemyBounds):
            → enemies.hitEnemy(enemy) (set health to -1, record destroyedTime)
            → drawAircraft.removeBullet(bullet)
            → enemiesDestroyedThisLevel++, totalKills++
            → checkKillTarget() (may trigger level complete or game won)
            → break
```

All bounds are `RectF` rectangles. Enemy sprites are 48dp, bullets are 25dp (converted via `ScreenUtils.dpToPx()`).

---

## 7. Level System

All formulas are in `GameCoreView.companion` and `Enemies.companion`:

```kotlin
// GameCoreView
fun getLevelDurationMs(level: Int): Long = 300_000L - 20_000L * (level - 1)   // 300s → 120s
fun getRequiredKills(level: Int): Int = 90 + level * 10                        // 100 → 190

// Enemies
fun getEnemiesPerRow(): Int = BASE_ENEMIES_PER_ROW + level                     // 6 → 15
fun getSpawnIntervalFrames(): Int = 90 - (level - 1) * 5                       // 90 → 45 frames
fun getEnemyMoveSpeed(): Float = BASE_ENEMY_MOVE_SPEED + (level - 1) * 1.5f   // 3 → 16.5
fun getBulletSpacingDp(): Float = max(BASE_BULLET_SPACING_DP - (level - 1) * 15f, 250f)  // 350 → 250
```

Constants: `BASE_ENEMY_MOVE_SPEED = 3f`, `BASE_ENEMIES_PER_ROW = 5`, `BASE_BULLET_SPACING_DP = 350f`, `MIN_BULLET_SPACING_DP = 250f`, `SPACING_DECREASE_PER_LEVEL = 15f`.

Enemies have 1 HP. Player has 100 HP, loses 20 per hit (`BULLET_DAMAGE = 20f`). Score = `totalKills * 100`.

---

## 8. Database

### Schema

Table `player_game_data` (Room entity `PlayerGameData`):

| Column      | Type   | Notes                            |
|-------------|--------|----------------------------------|
| id          | Long   | Auto-generated primary key       |
| player_id   | String | Device `ANDROID_ID`              |
| level       | Int    | Level reached when game ended    |
| score       | Long   | `totalKills * 100`               |
| timestamp   | Long   | `System.currentTimeMillis()`     |

### Behavior

- **One record per player:** `saveGameData()` in `MainActivity` calls `deleteByPlayerId()` before `insert()`, so new records overwrite old ones.
- **Player ID:** Retrieved via `Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)`.
- **Saved on:** Both game over (HP = 0 or time expired) and game won (all 10 levels cleared).

### DAO Methods

| Method                | Query                                          |
|-----------------------|------------------------------------------------|
| `insert(data)`        | Insert a new record                            |
| `delete(data)`        | Delete a specific record by entity              |
| `getByPlayerId(id)`   | All records for a player, newest first          |
| `getAllByScoreDesc()`  | All records sorted by score (leaderboard)       |
| `getTotalScore(id)`   | Sum of all scores for a player                  |
| `deleteByPlayerId(id)`| Delete all records for a player                 |

> **Note:** Because `saveGameData()` always calls `deleteByPlayerId()` before `insert()`, most of these methods only ever operate on a single row per player. The multi-row capabilities (`getByPlayerId`, `getTotalScore`) exist for potential future use but are effectively single-record today.

---

## 9. Threading

```
Main Thread                  Game Thread                  Service
───────────                  ───────────                  ───────
Activity lifecycle           SurfaceView render loop      MusicService
UI dialogs                   Canvas drawing (30 FPS)      @Synchronized
Service binding              Collision detection            playback
lifecycleScope (Room DB)     Level timer checks
```

### Rules

- **Never touch game objects from the main thread.** `GameCoreView` properties like `level`, `enemies`, `drawAircraft` are read/written on the game thread. The main thread communicates via callbacks (`onGameOver`, `onLevelComplete`, `onGameWon`) which use `post {}` to dispatch to the main thread.
- **`advanceToNextLevel()` is called from the main thread** (from the dialog button click), but it only sets simple flags and clears lists — this is the one intentional cross-thread write.
- **Room DAO methods are `suspend` functions.** Always call them from a coroutine scope (`lifecycleScope`), never from the game thread.
- **`MusicService` playback methods are `@Synchronized`.** Safe to call from the game thread (collision handlers) or the main thread.
- **`SurfaceHolder` is synchronized** in the game loop: `synchronized(surfaceHolder) { onUpdateGameDraw(canvas) }`.
- **`ScreenUtils` methods are `@Synchronized`.** Safe from any thread.

---

## 10. Common Tasks

### Add a New Enemy Type

1. Add the sprite PNG to `res/drawable/` (e.g., `enemy_11.png`). The sprite should face **upward** in the file — it will be rotated 180° at load time. Any size works; it is resized to 48dp x 48dp.
2. In `Enemies.kt` `init {}` block, add the resource ID to the `enemyResIds` array:
   ```kotlin
   val enemyResIds = intArrayOf(
       R.drawable.enemy_1, ..., R.drawable.enemy_10,
       R.drawable.enemy_11  // ← add here
   )
   ```
   The existing loop loads each ID into `bitmapList`, then pre-caches a resized+rotated copy in `cachedEnemyBitmaps`. Enemies randomly pick from the list when spawning rows, so the new sprite appears automatically.
3. To add special behavior (e.g., more HP), modify `EnemyState` (add an HP field) and the hit detection logic in `GameCoreView.checkPlayerBulletsHitEnemies()`.

### Add a New Sound Effect

1. Place the `.mp3` file in `res/raw/` (e.g., `power_up.mp3`)
2. In `MusicService.kt`, add a new hex constant and load it in `initSoundPool()`:
   ```kotlin
   private val powerUpId = 0x006
   soundMap[powerUpId] = soundPool.load(this, R.raw.power_up, 1)
   ```
3. Add a public play method:
   ```kotlin
   @Synchronized
   fun powerUpSoundPlay() {
       if (!isCombatSoundEnabled()) return
       soundMap[powerUpId]?.let { soundPool.play(it, 1f, 1f, 0, 0, 1f) }
   }
   ```
4. Call it from `GameCoreView` via `musicService?.powerUpSoundPlay()`.

### Add a New Language

1. Create `res/values-<code>/strings.xml` (e.g., `values-ja/strings.xml` for Japanese)
2. Copy all string entries from `res/values/strings.xml` and translate them
3. Android auto-selects the correct file based on device locale. No code changes needed.

Currently supported: English (default `values/`) and Chinese (`values-zh/`).

---

## 11. How to Play

### Controls

- **Move**: Touch and drag anywhere on the screen to move the player jet.
- **Fire**: Bullets fire **automatically** every 2 frames — no button needed.

### Objective

Destroy enough enemies before time runs out on each level. There are **10 levels** to clear.

### Level Progression

| Level | Time Limit | Required Kills |
|-------|-----------|----------------|
| 1     | 300s      | 100            |
| 2     | 280s      | 110            |
| 3     | 260s      | 120            |
| 4     | 240s      | 130            |
| 5     | 220s      | 140            |
| 6     | 200s      | 150            |
| 7     | 180s      | 160            |
| 8     | 160s      | 170            |
| 9     | 140s      | 180            |
| 10    | 120s      | 190            |

### Difficulty Scaling

As levels increase, enemies become more dangerous:
- **More enemies** spawn per row
- **Faster spawn rate** — less time between waves
- **Faster movement** — enemies and bullets move quicker
- **Tighter bullet spacing** — harder to dodge

### HUD (Heads-Up Display)

The top of the screen shows:
- **Level** — current level number
- **HP Bar** — player health (green → yellow → red as health drops)
- **Timer** — countdown to level deadline (turns red at 10s remaining)
- **Kills** — current kills / required kills for the level

### Health & Damage

- Player starts with **100 HP**
- Each hit (enemy collision or bullet) deals **20 damage**
- At low HP (20 or below), a **red vignette** pulses on the screen as a warning
- When hit, the screen **shakes** and **flashes red** briefly

### Scoring

- Each enemy destroyed earns **100 points**
- Score is the **cumulative total** of all kills across all levels in one session
- Your score and level reached are **saved automatically** when the game ends

### Win & Lose Conditions

- **Level Failed**: Timer runs out before reaching the kill target → **Game Over**
- **Player Destroyed**: HP drops to 0 → death explosion → **Game Over**
- **Level Complete**: Reach the kill target before time runs out → proceed to next level
- **Victory**: Clear all 10 levels → a name prompt appears (name is logged but not yet saved to the database)

### Game History

- Tap **History** on the home screen to view saved records
- Records show **Player ID**, **Level reached**, and **Score**
- Sorted from **highest score to lowest**
- Only the **most recent record** per player is kept
- Tap the **delete button** on any record to remove it

---

## 12. Class Diagram

The full class diagram for the project is available as an SVG file:

**[View Class Diagram →](class_diagram.svg)**

The diagram covers all classes across the project's packages:

- **data/** — Data models (`Aircraft`, `EnemyState`, `EnemyBullet`, `PlayerGameData`), Room database (`AppDatabase`), and DAO interface (`PlayerGameDataDao`)
- **ui/** — Game engine layer with abstract `DrawBaseObject` base class and its four subclasses (`Aircraft`, `DrawBackground`, `DrawHeader`, `Enemies`), plus `ExplosionEffect` for particle animations, `Bullet` data class, and the central `GameCoreView` (SurfaceView) that orchestrates the 30 FPS game loop
- **gui/** — Presentation layer activities (`LaunchActivity`, `MainActivity`, `HistoryActivity`, `SettingsActivity`, `PrivacyPolicyActivity`), fragments (`HistoryFragment`), and adapter (`HistoryAdapter`)
- **service/** — `MusicService` bound service with inner `MusicBinder`
- **viewmodel/** — `MainActivityViewModel` bridging service readiness to the UI via LiveData
- **utils/** — Singleton utility objects (`ScreenUtils`, `BitmapUtils`)
- **common/** — `AircraftApplication` entry point

### Key Relationships

| Relationship | Description |
|---|---|
| `GameCoreView` → `DrawBaseObject` subclasses | Composition — owns one instance each of `Aircraft`, `DrawBackground`, `DrawHeader`, `Enemies` |
| `Aircraft` (ui) → `Bullet` | Composition — manages a mutable list of player bullets |
| `Enemies` → `EnemyState` | Aggregation — manages active enemies on screen |
| `EnemyState` → `EnemyBullet` | Composition — each enemy owns its bullet list |
| `Enemies` → `ExplosionEffect` | Composition — manages active explosion animations |
| `GameCoreView` → `MusicService` | Association — optional reference for audio playback |
| `MainActivity` → `GameCoreView` | Composition — sets as content view |
| `MainActivity` → `MusicService` | Bind — via `ServiceConnection` |
| `MainActivity` → `AppDatabase` | Association — lazy-initialized for Room persistence |
| `AppDatabase` → `PlayerGameDataDao` | Abstract factory — provides DAO instance |
| `DrawHeader` → `Aircraft` (data) | Association — reads player HP for HUD rendering |
