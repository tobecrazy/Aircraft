# GEMINI.md

This file provides context and instructions for the **Aircraft** project, a 2D vertical-scrolling shooter game for Android.

## Project Overview

- **Purpose:** A high-performance 2D shooter game featuring 10 levels, boss fights, power-ups, and a custom particle-based explosion system.
- **Tech Stack:** 
  - **Language:** Kotlin 2.x
  - **Platform:** Android (Min SDK 30, Target/Compile SDK 36)
  - **Build System:** Gradle 9.3.1 with AGP 9.1.0
  - **Persistence:** Room Database (v2.8.4)
  - **Networking:** OkHttp 5.x
  - **Observability:** Firebase Analytics & Crashlytics
- **Architecture:** 
  - **Game Engine:** Custom `SurfaceView`-based engine (`GameCoreView`) running at 30 FPS.
  - **State Management:** `GameStateManager` using Kotlin `SharedFlow` to broadcast game states.
  - **Audio:** `MusicService` (Bound Service) for BGM (`MediaPlayer`) and SFX (`SoundPool`).
  - **Patterns:** MVVM for UI components, Singleton for database and state managers.

## Building and Running

| Task | Command |
|------|---------|
| **Build Debug APK** | `./gradlew assembleDebug` |
| **Build Release APK** | `./gradlew assembleRelease` |
| **Run Unit Tests** | `./gradlew test` |
| **Run Instrument Tests** | `./gradlew connectedAndroidTest` |
| **Lint Check** | `./gradlew lint` |
| **Clean Project** | `./gradlew clean` |

## Development Conventions

### 1. Game Object Lifecycle
All drawable game objects must extend `DrawBaseObject` and implement:
- `onDraw(canvas: Canvas)`: For rendering logic.
- `updateGame()`: For state/position updates.
- `getEnemyBounds()`: For collision detection.

### 2. Naming & Imports
- **Collision Warning:** There are two `Aircraft.kt` files.
  - `com.young.aircraft.data.PlayerAircraft` (Data model)
  - `com.young.aircraft.ui.Aircraft` (Game object)
  - **Usage:** Always disambiguate with `import com.young.aircraft.data.PlayerAircraft as AircraftData`.

### 3. Localization
- Support for **English** (default) and **Chinese** (`values-zh/`).
- All user-facing strings must reside in `strings.xml`.

### 4. Database Safety
- `AppDatabase` version is currently **2028**.
- **Critical:** When saving game data, the `finish()` call must occur *inside* the coroutine after the database write completes to avoid cancelling the `lifecycleScope`.

### 5. UI & Themes
- The game activities use `TransparentTheme` for immersive play.
- Support activities (History, Settings) use `Theme.Aircraft.History`.

## Key Files

- `GameCoreView.kt`: The heart of the game loop and collision logic.
- `GameStateManager.kt`: Global state broadcaster.
- `AppDatabase.kt`: Room database definition.
- `MusicService.kt`: Handles all audio playback.
- `DOCUMENT.md`: Contains detailed formulas, schemas, and asset guides.

## Testing Guidelines

- **Unit Tests:** Located in `app/src/test`. Uses JUnit 4, Mockito, and Robolectric.
- **Instrumented Tests:** Located in `app/src/androidTest`.
- **Convention:** Use backtick names for test methods, e.g., ``fun `test player health depletion`()``.
