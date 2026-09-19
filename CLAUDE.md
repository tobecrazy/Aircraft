# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Language

- **Respond in Chinese (中文回复)**: All responses and explanations should be in Chinese.

## Project and Documentation

Aircraft is a Kotlin Android vertical-scrolling shooter. Two Gradle modules: `:app` contains the game and utility screens; `:richtexteditor` is a reusable Android View library consumed by the app and distributable as an AAR.

- [README.md](README.md): project overview, features, downloads, and architecture diagrams.
- [DOCUMENT.md](DOCUMENT.md): detailed gameplay formulas and development documentation.
- [docs/rich-text-editor-aar-usage.md](docs/rich-text-editor-aar-usage.md): editor integration and AAR usage.
- [.github/copilot-instructions.md](.github/copilot-instructions.md): additional repository guidance. No Cursor rules were found during initialization.
- **[AGENTS.md](AGENTS.md) is a symlink to this file**; edit CLAUDE.md rather than replacing the symlink.

Some documentation is stale: README/Copilot describe interleaved puzzle gates, but current `MainActivity` advances directly to the next combat level. `PuzzleActivity` is a separate Settings entry with its own ten-level progression. Verify behavior against code before propagating documentation claims.

## Build, Lint, and Tests

Run from the repository root:

```bash
./gradlew assembleDebug                         # Debug APK
./gradlew assembleRelease                       # Release APK; requires signing configuration
./gradlew :richtexteditor:assembleRelease        # Library AAR in richtexteditor/build/outputs/aar/
./gradlew testDebugUnitTest                      # Debug unit tests across modules
./gradlew test                                   # All unit-test variants
./gradlew :app:testDebugUnitTest --tests "com.young.aircraft.ExampleUnitTest"
./gradlew :app:testDebugUnitTest --tests "com.young.aircraft.gui.SettingsActivityTest"
./gradlew :app:testDebugUnitTest --tests "com.young.aircraft.StringResourceTest"
./gradlew connectedAndroidTest                  # Requires device/emulator
./gradlew lintDebug                             # Debug lint, as in CI
./gradlew lint                                  # All lint variants
./gradlew clean
./gradlew assembleDebug lintDebug testDebugUnitTest  # CI-equivalent verification
```

Scope `--tests` to a module task (`:app:testDebugUnitTest`), not the root task: the library does not contain app test classes. Unit tests use JUnit, Robolectric, Mockito, and Compose UI testing; module builds enable Android resources for local tests.

`.github/workflows/android.yml` runs debug assembly, lint, and unit tests on pushes/PRs to `main`, with Temurin JDK 17. Do not infer Gradle success from the exit code of a downstream command in a shell pipeline.

### Build Configuration and Prerequisites

- JDK 17; use the checked-in Gradle wrapper (`gradle/wrapper/gradle-wrapper.properties`) rather than a system Gradle installation.
- Kotlin DSL builds; dependency/plugin versions are centralized in `gradle/libs.versions.toml`. AGP uses built-in Kotlin: do **not** add `org.jetbrains.kotlin.android`; the app still requires `org.jetbrains.kotlin.plugin.compose`. Root `build.gradle.kts` also explicitly pins `kotlin-gradle-plugin` for Compose mapping artifact resolution; check both locations when upgrading Kotlin rather than assuming their versions match.
- Both modules use compileSdk 37 and minSdk 32; the app targets SDK 37 and build tools 37.0.0. Configure the Android SDK via `local.properties` or the environment.
- App ID/namespace: `com.young.aircraft`; library namespace: `com.young.richtext`.
- The app enables Compose, View Binding, and BuildConfig, not Data Binding.
- Release enables R8 minification/resource shrinking via `app/proguard-rules.pro`. Signing loads root `keystore.properties` when present; it is not tracked.
- Firebase Analytics and Crashlytics are configured in `app/build.gradle.kts`; `app/google-services.json` supplies the Firebase configuration.

## Architecture

### Two Different State Models

**Game engine:** `MainActivity` hosts `ui/GameCoreView`, a `SurfaceView` implementing `SurfaceHolder.Callback` and `Runnable`. It owns a dedicated 30 FPS Canvas loop, game-object composition, collision detection, timers, and boss/level progression. Drawable objects derive from `DrawBaseObject`; `GameCoreView` itself does not. Mutable state models live in `data/`. Do not refactor this rendering hierarchy as if it were a Compose/MVVM screen.

**Activity/UI layer:** Most non-game screens use Compose + Material3, with `viewmodel/` exposing StateFlow/LiveData state and, where needed, SharedFlow one-shot events. `data/SettingsRepository` wraps SharedPreferences; `providers/DatabaseProvider` supplies Room DAOs. Follow existing ViewModel/UiState/Factory patterns for new utilities. This is not universal: `PuzzleActivity` still owns substantial puzzle/image-loading state itself. `GameViewModel` handles game persistence/scoring, not the render loop.

`MainActivity`, `QRCodeToolActivity`, and `RichTextEditorActivity` retain ViewBinding hosts. `HistoryActivity` is Compose, not a HistoryFragment/RecyclerView flow. Game dialogs and the hall-of-heroes sheet use Compose content through `gui/dialogs/` even though the game host uses Views.

### Theme and Transient UI

`SettingsRepository` persists five theme identifiers (green/blue/purple/yellow/red). `ui/theme/AircraftTheme.kt` maps them through `themeAccent` / `aircraftColorScheme` and listens to preference changes for live Compose updates. `AccentGreen` and `DividerGreen` are composable getters despite their legacy names: read them in composable scope, not inside Canvas draw callbacks or ordinary Activity methods. Native UI can resolve the shared color scheme from the repository.

`StarFieldView` has its own preference listener and renders theme-specific glyph particles on the launch/onboarding/privacy screens. It is separate from `DrawBackground` and the combat render loop; UI theme changes must not replace combat backgrounds.

- Classic dialogs/bottom sheets with Compose content use `Dialog.setDialogComposeContent(host)`. It supplies lifecycle owners, `AircraftTheme`, and `LocalDialogDismiss`, and installs content **after** showing the dialog because AppCompat otherwise replaces it. `GameDialogContent` dismisses on a negative action even without a caller callback.
- Native confirmations use `MaterialAlertDialogBuilder.showThemed()` to share surface/outline/text colors and stateful enabled/disabled button colors. Preference listeners are removed on dismissal.
- Foreground feedback uses `ThemedMessage.makeText(...).show()`, which presents a themed Material Snackbar, not a system Toast. It requires an attached, started Activity (including wrapped Activity contexts); it dismisses and cleans up listeners when the Activity stops. System permission prompts, pickers, and notifications remain system-styled.

### Navigation and Progression

First launch: `PrivacyPolicyAcceptActivity` → `OnboardingActivity` → `LaunchActivity` → `MainActivity`. Privacy/onboarding preferences skip completed gates. The launch hub also opens history, settings, and QR utilities; developer tools are exposed from Settings only in debug builds.

Combat has ten timed levels with increasing kill targets and a boss after each target. The timer pauses during boss fights; defeating the boss completes the level. `MainActivity.onLevelComplete` saves the next level, then calls `coreView.advanceToNextLevel()`; final victory opens the hall-of-heroes sheet. `GameCoreView` and `Enemies` companion objects hold the difficulty/level formulas.

`LaunchViewModel` offers continuation only when `(level > 1 || score > 0)` and `gameMode == AIR_BATTLE`. Launch/Main use `AircraftConstants.IntentExtras` to transfer starting level, jet resource/index, and total kills. Preserve kills when resuming so cumulative score survives.

`SettingsActivity` opens the independent Compose drag-and-drop `PuzzleActivity`. It saves puzzle level/score using `GameViewModel` with `GameMode.PUZZLE`; do not assume a stored mode implies the launch hub can resume it.

### Thread and Event Boundaries

- The game thread performs frame updates/drawing under the SurfaceHolder lock. Keep new mutable game-object work coordinated with that loop, rather than introducing unsynchronized Activity-side mutations.
- `GameCoreView.post { ... }` dispatches game-over/level-complete/win callbacks **to the main/UI thread**. It is not a queue onto the game thread. Existing Activity entry points include pause/resume and level advancement; inspect their synchronization when changing them.
- `common/GameStateManager` exposes a SharedFlow of `data/GameState` and the debug invincibility flag. `MainActivity` currently observes the flow for low-memory handling; normal completion dialogs use the direct callbacks above.
- `GameCoreView` propagates time-freeze state into the player, enemies, and boss before updates. Changes to movement or projectiles must preserve freeze behavior across these objects.
- `MusicService` is a bound MediaPlayer/SoundPool service with synchronized playback methods. `FlashlightService` separately owns the camera torch as a foreground service and holds a partial wake lock during SOS; this work must outlive the screen as designed.

### Persistence and Scoring

`DatabaseProvider` builds `aircraft_game.db` with `AppDatabase` (version 2031), registering migrations 2027→2028→2029→2030→2031. **There is no destructive-migration fallback in the current provider.** Schema changes need an explicit migration and registration.

`PlayerGameData` / `PlayerGameDataDao` represent `player_game_data`. Records include combat and puzzle levels/scores, mode, total kills, player name, jet resource/index, and difficulty. `GameViewModel` uses an install ID from SettingsRepository to identify the current player. Combat score is `totalKills * 100`; `saveAirBattleData()` preserves existing puzzle level/score while saving combat progress.

**Save before finishing:** keep `finish()` inside the `lifecycleScope` coroutine after the suspend save completes, as in `MainActivity.saveCurrentProgress()` callers and `PuzzleActivity`. Finishing alongside the coroutine can cancel the Room write.

### Rich-Text Library Boundary

`richtexteditor` supplies `com.young.richtext.RichTextEditorView`; the app Activity owns mode switching, sample loading, WebView preview, and image-viewer navigation. The library has no dependency on the game. Its optional `onMessage` callback lets `RichTextEditorActivity` and `QRCodeToolActivity` route editor feedback through `ThemedMessage`; standalone AAR consumers retain a system Toast fallback. Do not import app theme classes into the library.

Large unbroken/base64 content must not be inserted unchanged into the native EditText: native text layout can exhaust memory. `RichTextEditorActivity` skips oversized default content (`MAX_EDITABLE_LENGTH`) and sanitizes inline data-image tags via `makeHtmlEditable()` for JSON examples. Preserve these guards when changing content loading; use WebView preview rather than native text layout for large raw HTML.

## Repository-Specific Conventions

### UI and Localization

- Match the tactical UI: dark background `#0F1118`, header `#161A26`, selected theme accent (green `#00FF88` by default), monospace typography, and a 52dp header. Use the shared `AircraftTheme` and its color scheme instead of hardcoding decorative green; preserve each screen's existing layout.
- Solid-background utility activities should use `Theme.Aircraft.Common` in the manifest; the game uses `TransparentMaterialTheme`. Preserve each screen's inset handling: View roots use `fitsSystemWindows`, while Compose screens use their existing padding/Scaffold patterns (e.g. Settings header uses `statusBarsPadding`). Do not apply the XML RelativeLayout header pattern indiscriminately to Compose screens or double-apply insets.
- Default English and `values-zh/strings.xml` must stay synchronized. `StringResourceTest` checks locale parity and unused strings. Use resources (`stringResource`, `getString`, `@string/`) rather than hardcoded UI copy; remove orphan resources after refactoring.
- Robolectric Compose screen tests use `createAndroidComposeRule` and `@GraphicsMode(NATIVE)`. For scrollable content, follow `SettingsActivityTest`'s tall viewport (`w420dp-h2000dp`): off-window clicks may silently do nothing.

### Implementation Traps

- Never use Kotlin `!!`; use safe calls, explicit null guards, or `requireNotNull`/`checkNotNull` for programming errors.
- Distinguish `data/PlayerAircraft.kt` (often aliased as `AircraftData`) from the rendered `ui/Aircraft.kt`.
- Preserve `bitmap.density = screenDensity` for game sprites or Canvas scaling will be incorrect.
- Generated QR codes are light-on-dark. Keep ZXing's inverted-source fallback when decoding them (`decodeQrFromBitmap` in QRCodeToolActivity). There are two decode paths: picked images go through ZXing, while live camera scanning is `CameraScanActivity` (CameraX `MlKitAnalyzer`, a debug-only entry from `DevelopSettingsActivity`).
- File sharing uses the existing `${applicationId}.fileprovider`, `res/xml/file_paths.xml`, and `FilePickerHelper`; share content URIs with `FLAG_GRANT_READ_URI_PERMISSION`.
