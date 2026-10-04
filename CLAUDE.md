# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Language

- **Respond in Chinese (中文回复)**: All responses and explanations should be in Chinese.

## Project and Documentation

Aircraft is a Kotlin Android vertical-scrolling shooter. Three Gradle modules: `:app` contains the game and utility screens; `:richtexteditor` and `:supperbanner` are reusable Android View libraries consumed by the app and distributable as AARs.

- [README.md](README.md): project overview, features, downloads, and architecture diagrams.
- [DOCUMENT.md](DOCUMENT.md): detailed gameplay formulas and development documentation.
- [docs/rich-text-editor-aar-usage.md](docs/rich-text-editor-aar-usage.md): editor integration and AAR usage.
- [docs/supper-banner-aar-usage.md](docs/supper-banner-aar-usage.md): banner carousel integration and AAR publishing.
- [docs/puzzle-game-redesign-plan.md](docs/puzzle-game-redesign-plan.md): working plan explaining *why* the puzzle scoring code looks the way it does. Read it before changing that area.
- `docs/` also holds generated architecture artifacts — `aircraft-architecture.*` and `boss-combat-workflow.*` at the top level, `pdf-reader-dataflow.*` likewise, while `aircraft-code-map.*` and `supperbanner-module.*` live under `docs/diagrams/`. Each is a `.json` source plus rendered `.html` and `.visual-check.*` screenshots. All 44 files are committed but machine-generated; edit the `.json`, not the `.html`.
- [.github/copilot-instructions.md](.github/copilot-instructions.md): additional repository guidance. No Cursor rules were found during initialization.
- **[AGENTS.md](AGENTS.md) is a symlink to this file**; edit CLAUDE.md rather than replacing the symlink.

Some documentation is stale; verify behavior against code before propagating documentation claims:

- README/Copilot describe interleaved puzzle gates, but current `MainActivity` advances directly to the next combat level. `PuzzleActivity` is a separate Settings entry with its own ten-level progression.
- README says min SDK 30; `app/build.gradle.kts` actually sets minSdk 32.
- Copilot claims Room uses `fallbackToDestructiveMigration(true)`; `DatabaseProvider` registers explicit migrations only, with no fallback (see Persistence below).
- Copilot says many screens still use ViewBinding/XML and that combat routes through `PuzzleActivity` between levels; both are false — View Binding is off and `MainActivity` advances straight to the next combat level.
- `gui/GameHudScreen.kt` holds the **live** Compose HUD: `MainActivity.setContent` stacks `GameHudOverlay(state = hudState, …)` in a `Box` on top of `AndroidView(factory = { coreView })` (`MainActivity.kt:153`), driven by a `GameHudState` field and wired to `showPauseOverlay` / `hidePauseOverlay` / `quitFromPauseOverlay`. It owns pause/resume/quit — do not delete or bypass it. Note the Canvas HUD is **not** replaced: `GameCoreView.onDraw` still calls `drawHeader(canvas)` (`ui/DrawHeader.kt` via `ui/GameHudFormatter.kt`) for the in-surface stats readout, so the two coexist.
- `SupperBannerEffect` has **11** values (NONE, FADE, ZOOM_OUT, DEPTH, CUBE, ROTATION_GATE, COVERFLOW, STACK, PARALLAX, ACCORDION, SHADER), each with one `develop_settings_supper_banner_effect_*` string (11 per locale × 4 locales). Adding an effect means touching the enum, `SupperBannerTransformers`, the DevelopSettings `when` mapping, and all four locales together.

## Build, Lint, and Tests

Run from the repository root:

```bash
./gradlew assembleDebug                         # Debug APK
./gradlew assembleRelease                       # Release APK; requires signing configuration
./gradlew :richtexteditor:assembleRelease        # Library AAR in richtexteditor/build/outputs/aar/
./gradlew :supperbanner:assembleRelease          # Library AAR in supperbanner/build/outputs/aar/
./gradlew :supperbanner:publishReleasePublicationToBuildRepoRepository   # com.young:supperbanner into supperbanner/build/repo
./gradlew testDebugUnitTest                      # Debug unit tests across modules
./gradlew test                                   # All unit-test variants
./gradlew :app:testDebugUnitTest --tests "com.young.aircraft.ui.GameCoreViewFormulaTest"
./gradlew :app:testDebugUnitTest --tests "com.young.aircraft.gui.SettingsActivityTest"
./gradlew :app:testDebugUnitTest --tests "com.young.aircraft.StringResourceTest"
./gradlew connectedAndroidTest                  # Requires device/emulator
./gradlew lintDebug                             # Debug lint, as in CI
./gradlew lint                                  # All lint variants
./gradlew clean
./gradlew assembleDebug lintDebug testDebugUnitTest  # CI-equivalent verification
```

Scope `--tests` to a module task (`:app:testDebugUnitTest`), not the root task: the library does not contain app test classes. Unit tests use JUnit, Robolectric, Mockito, and Compose UI testing; module builds enable Android resources for local tests. `connectedAndroidTest` requires a device and is effectively unused — `app/src/androidTest` holds only the generated `ExampleInstrumentedTest` scaffold, so real coverage is all in `app/src/test` (plus 1 file in `:richtexteditor` and 3 in `:supperbanner`).

`.github/workflows/android.yml` runs debug assembly, lint, and unit tests on pushes/PRs to `main`, with Temurin JDK 17. Do not infer Gradle success from the exit code of a downstream command in a shell pipeline.

### Build Configuration and Prerequisites

- JDK 17; use the checked-in Gradle wrapper (`gradle/wrapper/gradle-wrapper.properties`) rather than a system Gradle installation.
- Kotlin DSL builds; dependency/plugin versions are centralized in `gradle/libs.versions.toml`. AGP uses built-in Kotlin: do **not** add `org.jetbrains.kotlin.android`; the app still requires `org.jetbrains.kotlin.plugin.compose`. Root `build.gradle.kts` also explicitly pins `kotlin-gradle-plugin` for Compose mapping artifact resolution; check both locations when upgrading Kotlin rather than assuming their versions match. **They already disagree**: the catalog has `kotlin-compose = "2.4.20"` while root `build.gradle.kts` pins `2.4.10` (its comment justifying the pin still cites the older Compose version — do not trust it).
- Both modules use compileSdk 37 and minSdk 32; the app targets SDK 37 and sets build tools 37.0.0 (only `:app` declares `buildToolsVersion`; the libraries rely on the AGP default). Configure the Android SDK via `local.properties` or the environment.
- App ID/namespace: `com.young.aircraft`; library namespace: `com.young.richtext`.
- The app enables Compose and BuildConfig. **View Binding is disabled** (removed in `a6c3b6b`; no binding classes exist) and Data Binding is not used.
- Release enables R8 minification/resource shrinking via `app/proguard-rules.pro`. Signing loads root `keystore.properties` when present; it is not tracked.
- Firebase Analytics and Crashlytics are configured in `app/build.gradle.kts`; `app/google-services.json` supplies the Firebase configuration.
- **Debug-only code is a source-set split, not a runtime flag.** `utils/DebugTools.kt` exists twice: `app/src/debug/` (`isEnabled = true`, `log`/`enableWebViewDebugging` live) and `app/src/release/` (`isEnabled = false`, all methods no-op). `SettingsViewModel` maps `DebugTools.isEnabled` into `SettingsUiState.showDevelopSettings`, which is the *only* gate for the Settings rows; each gated Activity then re-checks `DebugTools.isEnabled` in `onCreate` and calls `finish()` — six call sites today (`ContactsActivity`, `RichTextEditorActivity`, `DevelopSettingsActivity`, `CameraScanActivity`, `AndroidDevAssistantToolsActivity`, plus the ViewModel mapping). Adding a debug screen means touching both `DebugTools` variants plus both gates — otherwise a release build crashes or silently shows dev UI. Note the two variants must agree on **`val` vs `var`**: `isEnabled` is currently a `var` in debug and a `val` in release, so a `main` source that assigns to it compiles in debug and fails in release.

## Architecture

### Two Different State Models

**Game engine:** `MainActivity` hosts `ui/GameCoreView`, a `SurfaceView` implementing `SurfaceHolder.Callback` and `Runnable`. It owns a dedicated 30 FPS Canvas loop, game-object composition, collision detection, timers, and boss/level progression. Drawable objects derive from `DrawBaseObject`; `GameCoreView` itself does not. Mutable state models live in `data/`. Do not refactor this rendering hierarchy as if it were a Compose/MVVM screen.

**Activity/UI layer:** Most non-game screens use Compose + Material3, with `viewmodel/` exposing StateFlow/LiveData state and, where needed, SharedFlow one-shot events. `data/SettingsRepository` wraps SharedPreferences; `providers/DatabaseProvider` supplies Room DAOs. Follow existing ViewModel/UiState/Factory patterns for new utilities. `GameViewModel` handles game persistence/scoring, not the render loop.

`PuzzleActivity` follows this pattern too: the image feed request, disk cache, and load state now live in `viewmodel/PuzzleImageViewModel`; the Activity only reads launch args, saves progress, and owns the Compose board. Board/piece/undo state uses `rememberSaveable` with a Saver so rotation restores the in-progress board.

All `gui/` activities are Compose (`setContent`); there are no ViewBinding hosts. `HistoryActivity` is Compose, not a HistoryFragment/RecyclerView flow. Game dialogs and the hall-of-heroes sheet use Compose content through `gui/dialogs/` even though the game host uses Views. `MainActivity` is the mixed case: Compose owns the screen chrome (HUD overlay, dialogs) while `GameCoreView` stays a plain `SurfaceView` hosted through `AndroidView` — new game-surface work belongs in the View, new chrome in Compose.

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

**"Continue" is a level checkpoint, not a scene snapshot**: it restores the saved combat level, cumulative kills, and jet, but *not* current health, remaining time, or on-screen objects. The game restarts that level from scratch. Don't add snapshot persistence unless the product asks for in-place resume.

`SettingsActivity` opens the independent Compose drag-and-drop `PuzzleActivity`. It saves puzzle level/score using `GameViewModel` with `GameMode.PUZZLE`; do not assume a stored mode implies the launch hub can resume it. Puzzle scoring is efficiency-based (par = `gridSize²` moves; scans and retries subtract; 1–3 stars) and lives in the pure internal top-level `calculatePuzzleRoundResult()` / `createPuzzlePieces()` in `PuzzleActivity.kt` — see `docs/puzzle-game-redesign-plan.md`. There is no constant reference image on the board; the player spends limited "intel scans" to peek at it.

`SettingsActivity` navigates through a `SettingsDestination` enum (`SettingsScreen.kt`) mapped to Activity classes in `navigateTo`. Two destinations — `DEVELOP_SETTINGS` and `ASSISTANT_TOOLS` — are debug-only rows; add new Settings entries in all three places (enum, `navigateTo` `when`, the `SettingsScreen` row).

### Debug-Only Screens

`DevelopSettingsActivity` is the debug hub (crash tooling, invincible toggle, banner effect lab, and launchers for `RichTextEditorActivity` / `CameraScanActivity` / `PdfReaderActivity` / `ShowImageDetailsActivity`). `AndroidDevAssistantToolsActivity` is a sibling hub under the same `showDevelopSettings` gate, reading device facts through **native APIs only** — `Os.uname()`, `WebView.getCurrentWebViewPackage()` (API 26+), `/proc/version` — deliberately *without* androidx.webkit or any DI. Its per-module facts are pure internal top-level functions (`readKernelInfo()`, `readBrowserEngineInfo(context)`) returning `internal data class`es, so they are unit-testable without an Activity. It hosts **four** modules in `ASSISTANT_MODULES` (`MODULE_SYSTEM_INFO`, `MODULE_QUICK_SETTINGS`, `MODULE_APP_BROWSER`, `MODULE_CONTACTS`) plus the app-logs toggle; adding one means touching the constant, the `ASSISTANT_MODULES` entry, and `openModule`'s `when`. Module toggles persist in their own `SharedPreferences` (`ASSISTANT_PREFS`), not `SettingsRepository`, and a disabled module hides the row without preventing a direct Intent. It uses its own `ACTION_BAR`-free Compose shell with literal debug-only colors — it is a dev tool, so it is exempt from the tactical-UI palette rule below.

### Contacts (debug-only)

`ContactsActivity` is a Compose browser/editor over the **platform `ContactsContract` provider** — not Room, no local DB. It is reached Settings → `ASSISTANT_TOOLS` → `AndroidDevAssistantToolsActivity` → the `MODULE_CONTACTS` row, and self-gates in `onCreate` with `if (!DebugTools.isEnabled) { finish(); return }`. It lives in `main` (not `debug`) because the gate is runtime, not a source-set split.

- `READ_CONTACTS` **and** `WRITE_CONTACTS` are both required before the list renders; a read-only grant is deliberately treated as unusable. `onPermissionResult` ORs new results with the previous value so a partial grant never downgrades.
- `ContactsRepository(resolver: ContentResolver)` takes the **resolver, not a Context** — that is the test seam (`ContactsRepositoryTest` mocks it and feeds a hand-built `MatrixCursor`). `ContactsViewModel` is an `AndroidViewModel` only to reach `application.contentResolver`.
- `observeContacts()` merges the platform `ContentObserver` with a local `MutableStateFlow` refresh counter, then `conflate()` + `transformLatest { delay(300) }`, so a burst of writes collapses into one re-query. Writes bump the counter because self-issued provider notifications are not reliably timely.
- **Row shaping is not 1:1 with contacts**: one contact with N phones becomes N rows sharing name/email/address; a contact with no phone row produces *zero* rows. HOME-typed email and address win; work email is a fallback and non-HOME postal rows are dropped entirely.
- `add` is a single `applyBatch`; name/phone are mandatory ops while email/address are appended only when non-blank. In `updateOptionalData`, clearing an existing optional field **deletes that Data row** instead of writing an empty string. `delete` targets `RawContacts.CONTENT_URI` by `CONTACT_ID`, cascading all rows at once.
- `add`/`update` re-validate in the ViewModel (`isValidChinaPhoneNumber`, `isValidOptionalEmail` in `data/`) and silently return on failure — second line of defense behind the dialog's disabled Save button. Blank email is *valid* by design (optional field).
- Unlike the game engine, this feature has **no thread-confinement rules**: it is ordinary `Dispatchers.IO` + `viewModelScope` and shares nothing with the render thread.
- Note the permissions are declared in the **main** manifest, so they ship in release builds even though the screen is unreachable there.

### App Logging

`AppLog` is a **runtime kill-switch for `android.util.Log`**, not a logging framework: no file sink, no rotation, no Timber. Use `AppLog` for all application logging; direct `android.util.Log` access is allowed only inside `utils/AppLog.kt`. The `checkAppLogUsage` Gradle check enforces this rule as part of every `lint*` task — it scans `src/main` only (not the debug/release source sets) and fails on any `import android.util.Log` or `Log.` reference. Its `d(tag) { ... }` lambda skips message construction and interpolation while logging is disabled, so the hot game loop pays one volatile read. `@Volatile` is load-bearing because the render thread reads it.

**`d` is deliberately overloaded, not given a trailing `tr`**: `d(tag, msg: String, tr: Throwable? = null)` sits *beside* `d(tag, msg: () -> String)`. If the lambda version were `d(tag, msg: String, tr: Throwable? = null, msgLambda: () -> String)` instead, a trailing-lambda call `AppLog.d(tag) { ... }` would bind to `tr` — a silent overload-resolution trap. Write lambda messages as `AppLog.d(tag) { ... }`; both forms compile, so an accidental `{ msg }` in argument position is easy to introduce and invisible in review.

`LogSettings` persists the toggle in DataStore Preferences (`log_settings`, single `log_enabled` key) and exposes `enabledFlow` + `suspend setEnabled`, which writes DataStore *and* eagerly assigns `AppLog.enabled` so the switch lands on the same frame. `AircraftApplication.onCreate` seeds both `LogSettings.defaultEnabled` and `AppLog.enabled` from `BuildConfig.DEBUG` **before** collecting the flow (so early-startup logs aren't lost), with `.catch` falling back to the seed if the read throws. `LogSettingsViewModel` uses a hand-rolled `Factory`; its `StateFlow` is seeded from `AppLog.enabled` (not disk) so the toggle renders correctly on frame one. The toggle itself is a `LogSwitchRow` (testTag `assistant_switch_app_logs`) on the assistant-tools screen — so, like the screen hosting it, it is debug-reachable only.

### PDF Reader

`PdfReaderActivity` wraps the platform `android.graphics.pdf.PdfRenderer` — no PDF dependency. `PdfViewModel` owns the document and all rendering; the Activity only holds Compose state.

- One `PdfRenderer` guarded by a `Mutex`; `openPage` from concurrent coroutines is not safe, and every render/aspect-ratio call goes through `withLock { withContext(Dispatchers.IO) { … } }`.
- `%PDF-` magic is sniffed in the constructor (`hasPdfHeader`) because `PdfRenderer` only rejects encrypted/unreadable files at page-open time — failing early keeps the error out of the UI. Errors map to `pdf_reader_error_no_permission` (SecurityException) vs `pdf_reader_error_invalid`.
- **Zoom re-renders instead of scaling a bitmap.** A pinch updates `scale` continuously for transform only; `settledScale` (updated on gesture end) drives a re-render at `renderWidthFor(baseWidthPx, settledScale)`, clamped to `MAX_RENDER_WIDTH_PX = 2048` (~16MB ARGB_8888, a page-safe ceiling). Rendering per frame during a pinch is the failure mode to avoid.
- The page cache is an `LruCache<String, Bitmap>` sized at `maxMemory() / 16`, keyed by `index@width`; `sizeOf` returns `value.byteCount`. Pages keep their previous bitmap while the sharper one renders so zoom does not flash white.
- `dominantPageIndex()` (internal top-level, in the Activity file, with a `DominantPageIndexTest`) derives the page number for the header counter. It does not use the naive centre rule alone: a short trailing page (a blank back page) breaks it, so an unscrolled-past last page wins outright.
- The file comes from `ActivityResultContracts.OpenDocument` with `takePersistableUriPermission`, so the grant survives process death.

### Thread and Event Boundaries

Game-engine state is **render-thread confined** (`a6c3b6b`). Other threads never mutate game objects directly; they submit work:

- `gameCommands: ConcurrentLinkedQueue<() -> Unit>` — `pauseGame()`, `resumeGame()`, `advanceToNextLevel()`, and `onKeyDown` enqueue a lambda; the render loop drains the queue at the top of each frame, inside the `SurfaceHolder` lock. `advanceToNextLevel()` is a public enqueue wrapper; the body lives in private `advanceToNextLevelOnGameThread()`. **Any new Activity/UI entry point that touches game state must enqueue, not assign.**
- `pendingPlayerTouch: AtomicReference<PlayerTouch?>` — `onTouchEvent` records the latest ACTION_MOVE coordinates; the render loop applies them to `drawAircraft.jetX/jetY` before drawing. Only the newest touch is kept, so drag latency is one frame.
- `isRunning` and `musicService` are `@Volatile` for cross-thread visibility.
- The frame update/draw itself still happens under the SurfaceHolder lock. Keep new mutable game-object work coordinated with that loop.
- `GameCoreView.post { ... }` dispatches game-over/level-complete/win callbacks **to the main/UI thread**. It is not a queue onto the game thread.
- `common/GameStateManager` exposes a SharedFlow of `data/GameState` and the debug invincibility flag. `MainActivity` currently observes the flow for low-memory handling; normal completion dialogs use the direct callbacks above.
- `GameCoreView` propagates time-freeze state into the player, enemies, and boss before updates. Changes to movement or projectiles must preserve freeze behavior across these objects.
- `MusicService` is a bound MediaPlayer/SoundPool service with synchronized playback methods. `FlashlightService` separately owns the camera torch as a foreground service and holds a partial wake lock during SOS; this work must outlive the screen as designed.

### Networking

There is no Retrofit usage despite the declared dependency — network calls are direct OkHttp requests inside `BannerDetailsViewModel`, `ShowImageDetailsViewModel`, and `PuzzleActivity`. The Bing wallpaper (peapix) feed in `PuzzleActivity` is parsed by regex in `AircraftConstants`, not a JSON parser. Follow this pattern (no DI framework, no Retrofit service layer) unless asked otherwise.

### Persistence and Scoring

`DatabaseProvider` builds `aircraft_game.db` with `AppDatabase` (version 2031), registering migrations 2027→2028→2029→2030→2031. **There is no destructive-migration fallback in the current provider.** Schema changes need an explicit migration and registration.

`PlayerGameData` / `PlayerGameDataDao` represent `player_game_data`. Records include combat and puzzle levels/scores, mode, total kills, player name, jet resource/index, and difficulty. `GameViewModel` uses an install ID from SettingsRepository to identify the current player. Combat score is `totalKills * 100`.

**All progress writes go through `PlayerGameDataDao.replaceForPlayer()`** — a `@Transaction` default method that reads the newest row, merges the fields belonging to the *other* game mode, and replaces the record atomically. Do not reintroduce read-then-delete-then-insert in `GameViewModel`; it loses data on failure and interleaves under concurrent saves. A PUZZLE save preserves `airBattleLevel`; an AIR_BATTLE save preserves `puzzleLevel`/`puzzleScore`; a null `playerName` keeps the stored name.

**Save before finishing:** keep `finish()` inside the `lifecycleScope` coroutine after the suspend save completes, as in `MainActivity.saveCurrentProgress()` callers and `PuzzleActivity`. Finishing alongside the coroutine can cancel the Room write.

### Rich-Text Library Boundary

`richtexteditor` supplies `com.young.richtext.RichTextEditorView`; the app Activity owns mode switching, sample loading, WebView preview, and image-viewer navigation. The library has no dependency on the game. Its optional `onMessage` callback lets `RichTextEditorActivity` and `QRCodeToolActivity` route editor feedback through `ThemedMessage`; standalone AAR consumers retain a system Toast fallback. Do not import app theme classes into the library.

Large unbroken/base64 content must not be inserted unchanged into the native EditText: native text layout can exhaust memory. `RichTextEditorActivity` skips oversized default content (`MAX_EDITABLE_LENGTH`) and sanitizes inline data-image tags via `makeHtmlEditable()` for JSON examples. Preserve these guards when changing content loading; use WebView preview rather than native text layout for large raw HTML.

### Banner Library Boundary

`supperbanner` supplies `com.young.supperbanner.SupperBannerView` plus the `SupperBannerItem` / `SupperBannerImage` / `SupperBannerConfig` / `SupperBannerColors` / `SupperBannerEffect` / `SupperBannerTransition` types. It is a plain Android View with no strings and no dependency on the game, on `:richtexteditor`, or on any app theme class; keep it that way. Every color it paints is a field on `SupperBannerColors` (indicators on `SupperBannerIndicatorColors`) applied through `setColors()` — the view holds no literal colors, so a host maps its own theme into the palette instead of the library importing `AircraftTheme`. A customizer installed via `setIndicatorCustomizer` still runs after the palette and wins on the dots; use the palette for color and the customizer for what it cannot express (typeface, stroke width, shape).

Page transitions are `ViewPager2.PageTransformer` instances built by the internal `SupperBannerTransformers` from a `SupperBannerTransition`; `setTransition(NONE)` restores the default slide. The `SHADER` effect compiles AGSL at construction and is wrapped in a `try/catch` that degrades to `FADE` — keep that fallback, a bad shader string must not take the view down, and Robolectric does not exercise real GPU shader compilation. `PARALLAX` only offsets the image layer; the info panel sits outside the pager and is re-bound in `onPageSelected`, so text deliberately does not parallax. Every transform resets stale view state first because ViewPager2 recycles pages. `DevelopSettingsActivity` owns the effect dropdown; the 11 `develop_settings_supper_banner_effect_*` strings must stay in all four locales.

The click contract and the details/download flow stay in the app (`BannerDetailsActivity` + `BannerDetailsViewModel`), which only consumes the exported item types. `DevelopSettingsActivity` hosts the view through Compose `AndroidView` and supplies its own indicator styling via `setIndicatorCustomizer`.

Unlike `richtexteditor`, `supperbanner` applies `maven-publish` and publishes `com.young:supperbanner` to a local file repo under `supperbanner/build/repo`; see [docs/supper-banner-aar-usage.md](docs/supper-banner-aar-usage.md) for the publish commands and for adding a remote repository.

## Repository-Specific Conventions

### UI and Localization

- Match the tactical UI: dark background `#0F1118`, header `#161A26`, selected theme accent (green `#00FF88` by default), monospace typography, and a 52dp header. Use the shared `AircraftTheme` and its color scheme instead of hardcoding decorative green; preserve each screen's existing layout.
- Solid-background utility activities should use `Theme.Aircraft.Common` in the manifest; the game uses `TransparentMaterialTheme`. Preserve each screen's inset handling: Compose screens use `safeDrawingPadding`/`statusBarsPadding`/`navigationBarsPadding` or Scaffold `contentWindowInsets` patterns (e.g. Settings header uses `statusBarsPadding`). Do not double-apply insets.
- There are **four** locales, not two: `values/`, `values-zh/`, `values-zh-rTW/`, `values-zh-rHK/`. `StringResourceTest` scans *every* `values-*` dir that has a `strings.xml` and requires the full default key set in each, **and** fails on any string defined in `values/` but never referenced from `main` (code `R.string.*`, layouts/XML `@string/`, or the manifest), minus an 8-entry Firebase whitelist. It currently passes with 0 unused, so it is a guard rail rather than a standing failure — but removing the last usage of a string breaks the build, which is the intended behavior. Adding a string means adding it in all four. Use resources (`stringResource`, `getString`, `@string/`) rather than hardcoded UI copy; remove orphan resources after refactoring. In-app language switching goes through `AppCompatDelegate.setApplicationLocales` in `LanguageSettingsActivity`. There is **no `res/xml/locales_config.xml`**; persistence below API 33 comes from the manifest declaring `AppLocalesMetadataHolderService` with an `autoStoreLocales` meta-data value of `true`.
- On tablets/foldables, Compose screens cap their content column with `Modifier.maxContentWidth()` (`ui/WideScreen.kt`, 640dp) — the counterpart of `@dimen/content_max_width` in `values-sw600dp/`. Use the modifier in new Compose screens rather than inventing another width cap.
- `values-night/`, `values-sw600dp/`, and `values-sw1240dp/` also exist but hold **no `strings.xml`** — they carry `dimen`/`integer` resources only, so `StringResourceTest` ignores them. The four *locales* above are the only ones needing string parity.
- Robolectric Compose screen tests use `createAndroidComposeRule` and `@GraphicsMode(NATIVE)`. For scrollable content, follow `SettingsActivityTest`'s tall viewport (`w420dp-h2000dp`): off-window clicks may silently do nothing — a new row near the bottom of `AndroidDevAssistantToolsActivity` pushed a module tile out of the default viewport and made `performClick()` a no-op rather than a failure.
- **Never call `waitForIdle()` on a screen with an indeterminate progress indicator.** Robolectric's default choreographer re-fires vsync inline, so Compose never reaches idle and the test hangs until timeout (`AppListDialog`'s `CircularProgressIndicator` did exactly this, 60s). Pause the choreographer and drive a bounded number of frames instead.

### Implementation Traps

- Never use Kotlin `!!`; use safe calls, explicit null guards, or `requireNotNull`/`checkNotNull` for programming errors.
- Distinguish `data/PlayerAircraft.kt` (often aliased as `AircraftData`) from the rendered `ui/Aircraft.kt`.
- Preserve `bitmap.density = screenDensity` for game sprites or Canvas scaling will be incorrect.
- Generated QR codes are light-on-dark. Keep ZXing's inverted-source fallback when decoding them (`decodeQrFromBitmap` in QRCodeToolActivity). There are two decode paths: picked images go through ZXing, while live camera scanning is `CameraScanActivity` (CameraX `MlKitAnalyzer`, a debug-only entry from `DevelopSettingsActivity`).
- File sharing uses the existing `${applicationId}.fileprovider`, `res/xml/file_paths.xml`, and `FilePickerHelper`; share content URIs with `FLAG_GRANT_READ_URI_PERMISSION`.
- `common/AircraftApplication` applies two app-wide policies in `ActivityLifecycleCallbacks`, so a new Activity inherits them for free: portrait lock when `smallestScreenWidthDp < 600` (rotation left free on large screens), and `FLAG_SECURE` on every window (blocks screenshots/screen recording — relevant when debugging UI or writing screenshot tests). It also turns `onLowMemory` into `GameStateManager.emit(GameState.LOW_MEMORY)`.
