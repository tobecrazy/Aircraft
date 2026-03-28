# Aircraft

Aircraft is a Kotlin Android vertical-scrolling shooter built on a custom `SurfaceView` + Canvas game loop. The current app combines a first-launch privacy gate, a two-screen onboarding flow, 10 time-based combat stages, boss fights, collectible power-ups, local save/resume support, and debug-only developer tools.

## Project Architecture

![Project Architecture](project_diagram.svg)

> For the full UML class diagram, see [class_diagram.svg](class_diagram.svg). For detailed developer documentation, see [DOCUMENT.md](DOCUMENT.md). For release history, see [CHANGELOG.md](CHANGELOG.md) or the compatibility alias [ChangeLogs.md](ChangeLogs.md).

## Highlights

- Custom 30 FPS `SurfaceView` engine with no third-party game framework
- First-launch privacy acceptance flow with cinematic `StarFieldView`
- Two-page onboarding for controls and power-ups
- 10 levels with boss fights, scaling kill targets, and randomized scrolling backgrounds
- Four power-up systems: red envelopes/rockets, medical kits, shields, and time freezes
- Difficulty presets that adjust fire rate: Easy (`1.2x`), Normal (`1.0x`), Hard (`0.8x`)
- Room persistence for leaderboard data and saved progress, including jet selection and difficulty
- Utility screens for history, device info, about, privacy policy, and debug-only developer settings
- Firebase Analytics and Crashlytics integration
- English and Chinese localization

## Gameplay

- **Progression**: 10 levels with timers decreasing from 300s to 120s
- **Boss fights**: every level ends with a boss that scales from 1,000 HP to 1,900 HP
- **Controls**: drag the plane to move; bullets auto-fire during play
- **Power-ups**:
  - Red envelopes take 3 hits, then launch rockets with AoE damage
  - Medical kits restore the player to full HP
  - Shields grant temporary invincibility with a blink indicator
  - Time freezes can freeze enemies or the player for 5 seconds depending on who collects them
- **Progress persistence**: if a run ends after level 1, the player can save and later continue from the stored level
- **Debug flow**: debug builds expose Developer Settings, test-crash tooling, and a hidden invincible-mode toggle

## Features

- 12-way per-frame collision system covering enemies, bullets, bosses, rockets, and pickups
- Particle-based explosion effects with flash, fireball, debris, and smoke phases
- Screen shake, red damage flash, and low-health vignette effects
- Background music via `MediaPlayer` and combat SFX via `SoundPool`
- Jet selection with 4 playable plane sprites and saved `jet_plane_index`
- Device information screen with CPU, memory, disk, battery, and network telemetry
- Robolectric coverage for onboarding, privacy gate, background tiling, string parity, and gameplay formulas

## Project Structure

```text
app/src/main/java/com/young/aircraft/
├── common/
│   ├── AircraftApplication.kt          # Application entry point; emits LOW_MEMORY events
│   └── GameStateManager.kt             # SharedFlow game-state broadcaster + debug invincible flag
├── data/
│   ├── AppDatabase.kt                  # Room database (v2029) + migrations
│   ├── PlayerGameData.kt               # Saved run entity
│   ├── PlayerGameDataDao.kt            # Leaderboard/save DAO
│   ├── PlayerAircraft.kt               # Player HP and damage model
│   ├── EnemyState.kt                   # Enemy position and bullet state
│   ├── BossState.kt                    # Boss HP, bombs, and sprite state
│   ├── RedEnvelopeState.kt             # Red envelope pickup state
│   ├── RocketState.kt                  # Rocket projectile state
│   ├── MedicalKitState.kt              # Medical kit pickup state
│   ├── ShieldState.kt                  # Shield pickup state
│   ├── TimeFreezeState.kt              # Time-freeze pickup state
│   └── GameState.kt                    # PLAYING / PAUSED / GAME_OVER / GAME_WON / LOW_MEMORY
├── gui/
│   ├── PrivacyPolicyAcceptActivity.kt  # Launcher privacy gate
│   ├── OnboardingActivity.kt           # First-run tutorial carousel
│   ├── OnboardingFragments.kt          # Controls + power-up fragments
│   ├── LaunchActivity.kt               # Main menu, jet selection, continue-game dialog
│   ├── MainActivity.kt                 # Game host, dialogs, DB save flow
│   ├── HistoryActivity.kt              # History screen container
│   ├── HistoryFragment.kt              # Leaderboard fragment
│   ├── HistoryAdapter.kt               # RecyclerView adapter for saved runs
│   ├── SettingsActivity.kt             # Difficulty, sound, and navigation hub
│   ├── DevelopSettingsActivity.kt      # Debug-only crash/invincibility tools
│   ├── DeviceInfoActivity.kt           # Live system monitor
│   ├── AboutAircraftActivity.kt        # Project overview and GitHub link
│   ├── PrivacyPolicyActivity.kt        # Standalone privacy policy viewer
│   └── StarFieldView.kt                # Animated cinematic background
├── providers/
│   └── DatabaseProvider.kt             # Singleton Room provider
├── service/
│   └── MusicService.kt                 # Bound BGM + SFX playback service
├── ui/
│   ├── GameCoreView.kt                 # Main game loop and collision orchestration
│   ├── DrawBaseObject.kt               # Base drawable/update contract
│   ├── DrawBackground.kt               # Mirrored seamless background renderer
│   ├── DrawHeader.kt                   # HUD for level, HP, timer, kills, freeze state
│   ├── Aircraft.kt                     # Player sprite and bullet system
│   ├── Enemies.kt                      # Enemy spawning, movement, and bullets
│   ├── BossEnemy.kt                    # Boss AI, bombs, and scaling HP
│   ├── RedEnvelopes.kt                 # Rocket power-up and explosion handling
│   ├── MedicalKits.kt                  # HP pickup spawning and lifetime rules
│   ├── Shields.kt                      # Shield pickup spawning and lifetime rules
│   ├── TimeFreezes.kt                  # Freeze pickup spawning and 5s freeze logic
│   └── ExplosionEffect.kt              # Particle explosion effect
├── utils/
│   ├── BitmapUtils.kt                  # Bitmap loading, scaling, mirroring, rotation
│   └── ScreenUtils.kt                  # Screen metrics and dp/sp conversions
└── viewmodel/
    └── MainViewModel.kt                # MainActivityViewModel sound-service readiness
```

## Tests

`app/src/test` includes:

- data-model tests for gameplay and persistence state classes
- `GameCoreViewFormulaTest` for level duration and kill-target math
- `DrawBackgroundTest` for seamless mirrored tile coverage
- `OnboardingActivityTest` and `PrivacyPolicyAcceptActivityTest` for first-run flow behavior
- `StarFieldViewTest` for the animated onboarding/privacy background
- `StringResourceTest` for locale parity and resource usage coverage

Instrumented tests belong in `app/src/androidTest`.

## Game Assets

| Category | Count | Details |
|----------|-------|---------|
| Enemy sprites | 15 | `enemy_1.png` to `enemy_15.png` |
| Boss sprites | 5 | `boss_1.png` to `boss_5.png` |
| Missile sprites | 3 | `missile_1.png` to `missile_3.png` |
| Jet planes | 4 | `jet_plane_1.png` to `jet_plane_4.png` |
| Red envelopes | 3 | `red_box_1.png`, `red_box_2.png`, `red_box_3.png` |
| Medical kits | 2 | `red_heart_1.png`, `red_heart_2.png` |
| Shields | 3 | `shield_1.png`, `shield_2.png`, `shield_3.png` |
| Time freezes | 3 | `timer_1.png`, `timer_2.png`, `timer_3.png` |
| Rocket | 1 | `rocket.png` |
| Backgrounds | 3 | `background.jpg`, `background_1.jpg`, `background_2.jpg` |
| Audio | 6 | 2 BGM tracks + fire/hit/enemy-hit/game-over SFX |
| Localization | 2 | English (`values/`) + Chinese (`values-zh/`) |

## Level Progression

| Level | Time Limit | Required Kills | Enemies/Row | Boss HP |
|-------|-----------|----------------|-------------|---------|
| 1 | 300s | 100 | 6 | 1,000 |
| 2 | 280s | 110 | 7 | 1,100 |
| 3 | 260s | 120 | 8 | 1,200 |
| 4 | 240s | 130 | 9 | 1,300 |
| 5 | 220s | 140 | 10 | 1,400 |
| 6 | 200s | 150 | 11 | 1,500 |
| 7 | 180s | 160 | 12 | 1,600 |
| 8 | 160s | 170 | 13 | 1,700 |
| 9 | 140s | 180 | 14 | 1,800 |
| 10 | 120s | 190 | 15 | 1,900 |

## Requirements

- **Version**: `1.1.3`
- **Android Studio**: Meerkat (`2024.3.1`) or later
- **Compile SDK**: `36`
- **Min SDK**: `30`
- **Target SDK**: `36`
- **Java**: `17`
- **Gradle Wrapper**: `9.4.1`
- **Android Gradle Plugin**: `9.1.0`

## Build

```bash
./gradlew assembleDebug          # Build debug APK
./gradlew assembleRelease        # Build release APK
./gradlew test                   # Run unit and Robolectric tests
./gradlew connectedAndroidTest   # Run instrumented tests (device/emulator required)
./gradlew lint                   # Run Android lint
./gradlew clean                  # Clean build outputs
```

## Setup

1. Clone the repository:
   ```bash
   git clone <repository-url>
   cd Aircraft
   ```
2. Open the project in Android Studio.
3. Sync Gradle and run on a device or emulator with Android 11+.

## License

This project is open source and available for educational purposes.
