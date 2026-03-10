# Aircraft - Project Document

## 1. Technology Stack

### Platform & Language

| Category          | Technology                  |
|-------------------|-----------------------------|
| Platform          | Android                     |
| Language          | Kotlin (bundled with AGP)   |
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
| Espresso             | 3.7.0   | UI instrumented tests|

### Build Features

- **View Binding** - Type-safe view references
- **Data Binding** - Declarative layout binding
- **BuildConfig** - Generated build constants

### Game Engine

The game does **not** use any third-party game framework. It is built entirely on Android's native `SurfaceView` + `Canvas` API with a custom 30 FPS render loop running on a dedicated thread.

---

## 2. Project Structure

```
Aircraft/
├── build.gradle                          # Root: AGP classpath + KSP plugin
├── settings.gradle                       # Module includes + repository config
├── gradle.properties                     # JVM args, AndroidX, KSP compat flags
│
└── app/
    ├── build.gradle                      # App: plugins, SDK, dependencies
    │
    └── src/main/
        ├── AndroidManifest.xml
        │
        ├── java/com/young/aircraft/
        │   │
        │   ├── common/
        │   │   └── AircraftApplication.kt          # Application entry point
        │   │
        │   ├── data/                                # ── Data Layer ──
        │   │   ├── AppDatabase.kt                   # Room database singleton (v2026)
        │   │   ├── PlayerGameData.kt                # Entity: player_game_data table
        │   │   ├── PlayerGameDataDao.kt             # DAO: CRUD for game records
        │   │   ├── Aircraft.kt                      # Data model: player HP & stats
        │   │   └── EnemyState.kt                    # Data model: enemy state & bullets
        │   │
        │   ├── gui/                                 # ── Presentation Layer ──
        │   │   ├── LaunchActivity.kt                # Home screen (Start / History / Settings)
        │   │   ├── MainActivity.kt                  # Game host, binds MusicService
        │   │   ├── HistoryActivity.kt               # History screen container
        │   │   ├── HistoryFragment.kt               # Game history list (RecyclerView)
        │   │   ├── HistoryAdapter.kt                # RecyclerView adapter for records
        │   │   ├── SettingsActivity.kt              # Sound & privacy preferences
        │   │   └── PrivacyPolicyActivity.kt         # Privacy policy (WebView)
        │   │
        │   ├── ui/                                  # ── Game Engine Layer ──
        │   │   ├── GameCoreView.kt                  # SurfaceView: game loop & orchestration
        │   │   ├── DrawBaseObject.kt                # Abstract base for drawable objects
        │   │   ├── Aircraft.kt                      # Player jet: rendering & bullet firing
        │   │   ├── Enemies.kt                       # Enemy spawning, movement & bullets
        │   │   ├── DrawBackground.kt                # Scrolling parallax background
        │   │   ├── DrawHeader.kt                    # HUD: level, HP bar, timer, kills
        │   │   └── ExplosionEffect.kt               # Particle-based death explosion
        │   │
        │   ├── service/                             # ── Service Layer ──
        │   │   └── MusicService.kt                  # Bound service: BGM + SFX playback
        │   │
        │   ├── viewmodel/                           # ── ViewModel Layer ──
        │   │   └── MainActivityViewModel.kt         # LiveData for service readiness
        │   │
        │   └── utils/                               # ── Utilities ──
        │       ├── ScreenUtils.kt                   # Screen dimensions, dp/sp/px conversion
        │       └── BitmapUtils.kt                   # Bitmap loading, resizing, rotation
        │
        ├── res/
        │   ├── layout/                              # XML layouts (activities, fragments, items)
        │   ├── drawable/                            # Sprites, backgrounds, vector icons
        │   ├── raw/                                 # Audio: background music + sound effects
        │   ├── values/                              # Strings, colors, themes (English)
        │   ├── values-zh/                           # Strings (Chinese)
        │   └── xml/                                 # Preference definitions
        │
        └── assets/
            └── privacy policy.html                  # Privacy policy content
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
│  │ + bullets│ │ move,  │ └──────────┘       │
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

### Threading Model

```
Main Thread                  Game Thread                  Service
───────────                  ───────────                  ───────
Activity lifecycle           SurfaceView render loop      MusicService
UI dialogs                   Canvas drawing (30 FPS)      @Synchronized
Service binding              Collision detection            playback
lifecycleScope (DB)          Level timer checks
```

---

## 3. How to Play

### Getting Started

1. Launch the app — the **home screen** shows three buttons:
   - **Start Game** — begin a new game session
   - **History** — view past game records sorted by score
   - **Game Settings** — toggle background music and combat sound effects

2. Tap **Start Game** to enter the battlefield.

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
- **Victory**: Clear all 10 levels → you can enter your name for the record

### Game History

- Tap **History** on the home screen to view saved records
- Records show **Player ID**, **Level reached**, and **Score**
- Sorted from **highest score to lowest**
- Only the **most recent record** per player is kept
- Swipe or tap the **delete button** on any record to remove it
