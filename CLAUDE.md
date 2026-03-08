# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Aircraft is a 2D vertical-scrolling shooter game for Android, written in Kotlin. The player controls a jet plane, fires bullets upward, and destroys enemies while avoiding collisions.

## Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Run unit tests
./gradlew test

# Run a single unit test class
./gradlew testDebugUnitTest --tests "com.young.aircraft.ExampleUnitTest"

# Run instrumented tests (requires device/emulator)
./gradlew connectedAndroidTest

# Clean build
./gradlew clean

# Lint check
./gradlew lint
```

## Build Configuration

- **Gradle:** 9.3.1, AGP 8.13.2, Kotlin 2.3.0
- **SDK:** compileSdk 35, minSdk 30, targetSdk 35, buildTools 34.0.0
- **Java:** 17
- **App ID:** `com.young.aircraft`
- View Binding and Data Binding are both enabled

## Architecture

### Game Engine (SurfaceView-based)

The game runs on a custom `SurfaceView` (`GameCoreView`) with a dedicated rendering thread at 30 FPS. This is **not** a Compose or standard View-based UI — it draws directly to a `Canvas`.

**Rendering hierarchy:**
- `GameCoreView` (SurfaceView + Runnable) — owns the game loop, coordinates all drawing, and handles collision detection
- `DrawBaseObject` — abstract base class for all drawable game objects
  - `Aircraft` (ui/) — player jet with bullet management and touch-based movement
  - `DrawBackground` — seamless double-buffer scrolling background
  - `DrawHeader` — HUD overlay showing level info
  - `Enemies` — spawns and manages enemy sprites with random positioning

### Activity Flow

`LaunchActivity` → `MainActivity` (hosts `GameCoreView`) → optional `SettingsActivity` / `PrivacyPolicyActivity`

### Audio

`MusicService` is a bound Service using `SoundPool` for low-latency game audio (fire, hit, game over sounds). `MainActivity` binds to it and observes readiness via `MainActivityViewModel` LiveData.

### Utilities

- `ScreenUtils` — thread-safe screen dimensions and unit conversion (dp/sp/px)
- `BitmapUtils` — bitmap loading, resizing, rotation from resources

### Threading Model

- **Main thread:** Activity lifecycle, UI
- **Game thread:** Dedicated thread in `GameCoreView` for the render loop
- **Service:** `MusicService` bound service lifecycle

### Key Data Model

`Aircraft` (data/) — data class with `name`, `health_points`, `lethality`, `icon`. The companion object holds mutable game state and `isAlive()` check. Note: there are two files named `Aircraft.kt` — one in `data/` (data model) and one in `ui/` (rendering).
