# AGENTS.md

- **中文回复**：所有解释用中文；新增或修改的代码注释、KDoc 和 Markdown 一律用英文。
- `CLAUDE.md` is the long-form handbook; this file is its OpenCode digest — update both when a fact changes. `README.md` has the annotated source tree, `DOCUMENT.md` the gameplay formulas and long-form guide, `ChangeLogs.md` the release history. When prose conflicts with code (`.github/copilot-instructions.md` included), trust the code.
- Read `docs/puzzle-game-redesign-plan.md` before touching puzzle scoring; `docs/*-aar-usage.md` cover library integration. Architecture diagrams live in `docs/` (and `docs/diagrams/`) as a `.json` source plus generated `.html` / `.visual-check.*` sidecars.

## Project Structure

- `app/` is the game plus user-facing screens (settings, about, privacy, history, puzzle) in `com.young.aircraft`; `developtools/` holds the developer and utility screens (develop settings, assistant tools, API debugger, scanner, contacts, PDF, rich text, QR, image details, flashlight, device info) in `com.young.developtools` — **not** nested under `aircraft`. Both keep Kotlin in `src/main/java` and tests in `src/test`; `app/src/androidTest` holds only the generated instrumented-test scaffold, and `developtools/src/debug`/`src/release` hold the variant-only `DebugTools` copies.
- `richtexteditor/` provides only `RichTextEditorView`; `supperbanner/` provides `SupperBannerView` plus the config/colors/effect/transition types. Both are game-independent View libraries; `supperbanner` has no strings, `richtexteditor` ships its own 4-locale editor strings.
- `developtools` ships its own strings (4 locales) and copies of the theme/base classes, depends on `supperbanner` and `richtexteditor`, and must never depend on `app`; cross-module wiring goes through the four `DevTools` hooks (`openActivityMonitor`, `onInvincibleChanged`, `historyStoreProvider`, `onLogEnabledChanged`). Extraction record: `docs/develop-settings-module-plan.md`.
- `build-logic/` holds the three `aircraft.android.*` convention plugins; `gradle/` manages the wrapper and version catalog; `docs/` holds architecture notes, module plans, and AAR integration docs.

## Build Configuration

- JDK 17 plus the checked-in Gradle wrapper (never a system Gradle); `compileSdk 37 / minSdk 33`; versions are centralized in `gradle/libs.versions.toml`. Do **not** add `org.jetbrains.kotlin.android` (AGP ships built-in Kotlin), but `:app` still needs `org.jetbrains.kotlin.plugin.compose`. Shared compile setup lives in the `build-logic` included build (see `docs/build-logic-extraction-plan.md`); signing, R8, publishing, and `generateRepoUpdateConfig` stay in the individual modules.
- `settings.gradle.kts` sets `FAIL_ON_PROJECT_REPOS`: a new repository must go in the settings file, never in a module. There are no XML layouts; View Binding and Data Binding are unused, and there is no DI framework.

## Commands (repository root)

```bash
./gradlew assembleDebug lintDebug testDebugUnitTest   # CI-equivalent verification
./gradlew :app:testDebugUnitTest --tests "com.young.aircraft.ui.GameCoreViewFormulaTest"  # scope --tests to a module task; the root task matches nothing
./gradlew :developtools:testDebugUnitTest             # tool-screen tests, separate from :app
./gradlew :app:checkAppLogUsage                       # AppLog-only check, also a dependency of every lint* task
./gradlew assembleRelease                             # needs root keystore.properties; rewrites app-update.json first
./gradlew :richtexteditor:assembleRelease             # library AAR under build/outputs/aar/
./gradlew :supperbanner:assembleRelease               # library AAR under build/outputs/aar/
./gradlew :supperbanner:publishReleasePublicationToBuildRepoRepository  # publishes com.young:supperbanner to build/repo
./gradlew test lint                                   # all test variants / all lint variants
```

- CI is `.github/workflows/android.yml` (Temurin JDK 17, triggered by pushes/PRs to `main` only): `assembleDebug lintDebug` in one step, `testDebugUnitTest` in another. Do not infer success from the exit code of a downstream command in a shell pipeline.
- Release requires the untracked root `keystore.properties`. `assembleRelease`/`bundleRelease` automatically rewrite the root `app-update.json` (the Remote Config fallback source: `latest=versionName`, `minimum` defaults to `1.5.0` and is raised with `-PminimumVersion=`); merge the regenerated file into `main` after releasing. `connectedAndroidTest` is dead (scaffold only) — do not rely on it.
- **Version numbers are decimal with segment rollover** (`versionName`): a normal release bumps patch (1.4.3 → 1.4.4); past 9 the patch rolls into minor (1.4.9 → 1.5.0); past 9 there it rolls into major (1.9.9 → 2.0.0), and so on. `versionCode` increments by exactly 1 per release.

## Global Structure

- Startup chain: `PrivacyPolicyAcceptActivity` → `OnboardingActivity` → `LaunchActivity` → `MainActivity`; stored privacy/onboarding flags skip completed gates.
- `MainActivity` is a deliberate hybrid: the Compose `GameHudOverlay` (pause/resume/quit) is stacked over `AndroidView(factory = { coreView })`, while `GameCoreView.onDraw` still paints the Canvas `drawHeader`. Both HUDs coexist on purpose — never delete either. New game-*surface* work goes in the View; new *chrome* goes in Compose. It declares its own `configChanges`, so rotation never recreates it.
- Combat is ten timed levels (`MAX_LEVEL = 10`) with rising kill targets and a boss after each target; the timer pauses during boss fights. "Continue" is a level checkpoint, not a scene snapshot: it restores the saved level, cumulative kills, and jet only — never health, remaining time, or on-screen objects. The puzzle is a separate ten-level mode opened from Settings; a stored `GameMode` does not make the launch hub resume it.
- Services: `MusicService` is a **bound** service (MediaPlayer BGM + SoundPool SFX, not a foreground service); `FlashlightService` lives in `:developtools` as a foreground service and holds a `PARTIAL_WAKE_LOCK` during SOS.
- Themes: `SettingsRepository` persists six theme ids (green/blue/purple/yellow/red plus `THEME_DYNAMIC`); Compose reads `AircraftTheme`, native UI resolves the shared scheme from the repository.
- Update chain: `AircraftRemoteConfig` (Firebase, also carrying `AppIcon` and the welcome gating) → `RepoUpdateConfig` reads the root `app-update.json` through jsDelivr `@main` with a raw.githubusercontent fallback → `AircraftApplication`'s `ActivityLifecycleCallbacks` calls `enforceMinimumVersion` from `onActivityResumed` → `MandatoryUpdateActivity`. A malformed version string fails open (treated as "no update needed").
- Room: `AppDatabase` is at version 2033 and `DatabaseProvider` registers six explicit migrations (2027 → 2033).
- A new Settings entry needs three edits: the `SettingsDestination` enum, the `navigateTo` `when`, and the row in `SettingsScreen`; `DEVELOP_SETTINGS`/`ASSISTANT_TOOLS` render only when `showDevelopSettings` is true.

## Architecture Red Lines

- The game engine is render-thread confined, not MVVM: `ui/GameCoreView` (SurfaceView + 30 FPS loop) owns composition, collision, timing, and boss/level progression. The UI thread **must** submit through `gameCommands` (a `ConcurrentLinkedQueue`; `pauseGame`/`resumeGame`/`advanceToNextLevel` all `add` to it); `post {}` is only a callback back to the UI thread, not a queue onto the game thread. `pendingPlayerTouch` keeps the newest frame only.
- Non-game screens are plain Compose + Material3: every `gui/` Activity calls `setContent`; new screens extend `gui/BaseAircraftActivity` (`initializeViewModel` first, then `initializeUI` only `if (!isFinishing)`), with `MainActivity` and `MandatoryUpdateActivity` as the exceptions; tool screens use the same-shaped copy inside `:developtools`.
- All progress writes go through `PlayerGameDataDao.replaceForPlayer()` (a cross-mode merging `@Transaction`) — do not revert to read-delete-insert; `finish()` must come **after** the suspend save (see `MainActivity`/`PuzzleActivity`), otherwise the Room write is cancelled. Room has no destructive fallback, so a missing migration crashes.
- `GameStateManager` carries LOW_MEMORY only today; every other completion flow uses direct `GameCoreView` callbacks. Networking is plain OkHttp (Retrofit is declared but unused); the puzzle Bing feed is parsed by regex, and `:developtools` persists history through the `ApiHistoryStore` contract rather than importing Room.
- Two aircraft types: `ui/Aircraft.kt` is the rendered sprite; `data/PlayerAircraft.kt` is the health/damage model, usually imported as `AircraftData`. Do not merge or cross-assign them.
- `DebugTools.kt` (inside `:developtools`) is a debug/release source-set split, not a runtime flag: adding a debug screen means editing both copies plus the `showDevelopSettings` gate plus each Activity's own `finish()` check. `isEnabled` is a `var` in debug and a `val` in release, so assigning it from `main` compiles in debug and fails in release. Release builds still expose dev screens after 8 taps on the About version badge (`DeveloperMode`), so keep both gates.

## Conventions and Traps

- Log through `AppLog` only (`android.util.Log` is allowed solely inside `utils/AppLog.kt`; `:developtools` mirrors this with `DevLog` in `utils/DevLog.kt`). Keep lambda messages in trailing position — never `AppLog.d(tag, { … })`.
- The log checks scan `src/main` only (not the debug/release source sets); `:developtools` uses its own `checkDevLogUsage` task.
- Four locales are mandatory: `values/values-zh/values-zh-rTW/values-zh-rHK`. Both `:app` and `:developtools` have a `StringResourceTest` that enforces parity and fails on unreferenced default strings (the `:app` test exempts an 8-entry Firebase whitelist), so deleting the last usage breaks the build.
- Robolectric screen tests use `createAndroidComposeRule` + `@GraphicsMode(NATIVE)`; long lists need a tall viewport (`w420dp-h2000dp` — off-window clicks silently do nothing); **never call `waitForIdle()`** on a screen with an indeterminate spinner (it hangs for 60s); tests touching `AppLog`/`LogSettings`/`DevLog` must pin `@Config(sdk=[34], application=Application::class)`.
- No `!!`; keep `bitmap.density = screenDensity` for game sprites; keep ZXing's inverted-source fallback in QR decoding (generated codes are light-on-dark); share files through `${applicationId}.fileprovider` with `FLAG_GRANT_READ_URI_PERMISSION`.
- `AircraftApplication` applies two app-wide policies through `ActivityLifecycleCallbacks`, so a new Activity inherits them: portrait lock when `smallestScreenWidthDp < 600` (using `SCREEN_ORIENTATION_UNSPECIFIED` on large screens so the user's rotation lock wins), and `FLAG_SECURE` in **release builds only** (debug builds can be screenshotted).
- Wide-screen content columns use `Modifier.maxContentWidth()` (`ui/WideScreen.kt`, 640dp) — do not invent another width cap.
- Use the themed UI helpers rather than platform defaults: `ThemedMessage.makeText(...).show()` (themed Snackbar, not `Toast`), `MaterialAlertDialogBuilder.showThemed()` for native confirms, and `Dialog.setDialogComposeContent(host)` for Compose-content dialogs.
- `SupperBannerEffect` has 11 values; adding one means updating the enum, `SupperBannerTransformers`, the DevelopSettings mapping, and all four locales together. Files under `docs/` ending in `.html`/`visual-check` are generated — edit the `.json` only. Archify and similar diagram skills must write their artifacts (`.json`/`.html`/`.visual-check.*`) into `docs/`, never the repository root.
