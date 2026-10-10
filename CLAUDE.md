# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Language

- **Respond in Chinese (中文回复)**: All responses and explanations should be in Chinese.

## Project and Documentation

Aircraft is a Kotlin Android vertical-scrolling shooter. Four Gradle modules: `:app` holds the game and the user-facing screens (settings/about/privacy/history/puzzle); `:developtools` holds every developer/utility screen (Develop hub, assistant tools, API debugger, scanner, contacts, PDF, rich-text, QR, image details, flashlight, device info, plus the DeveloperMode/DebugTools gate, DevLog/LogSettings, and the ApiHistoryStore contract); `:richtexteditor` (one class, `RichTextEditorView.kt`) and `:supperbanner` (six classes) are reusable plain-Android-View libraries consumed by the app and `:developtools`, and distributable as AARs. `:developtools` never depends on `:app` — the host bridge is `DevTools` (four hooks wired in `AircraftApplication`). See [docs/develop-settings-module-plan.md](docs/develop-settings-module-plan.md) for the extraction record.

- [README.md](README.md): project overview, features, downloads, and a full annotated source tree.
- [DOCUMENT.md](DOCUMENT.md): detailed gameplay formulas and development documentation.
- [docs/rich-text-editor-aar-usage.md](docs/rich-text-editor-aar-usage.md): editor integration and AAR usage.
- [docs/supper-banner-aar-usage.md](docs/supper-banner-aar-usage.md): banner carousel integration and AAR publishing.
- [docs/puzzle-game-redesign-plan.md](docs/puzzle-game-redesign-plan.md): working plan explaining *why* the puzzle scoring code looks the way it does. Read it before changing that area.
- `docs/` also holds generated architecture artifacts: `aircraft-architecture.*`, `boss-combat-workflow.*`, and `pdf-reader-dataflow.*` at the top level, plus `aircraft-code-map.*` and `supperbanner-module.*` under `docs/diagrams/`. Each is a `.json` source plus a rendered `.html` and `.visual-check.*` screenshots. All 44 files are committed but machine-generated; edit the `.json`, not the `.html`.
- **Archify (and any diagram-generation skill) output must be written into `docs/`** — set `meta.output` to a `docs/…` path (or pass it on the command line) and keep the `.json` source, rendered `.html`, and `.visual-check.*` sidecars together there. Never leave generated diagrams at the repository root.
- [.github/copilot-instructions.md](.github/copilot-instructions.md): additional repository guidance, **several claims of which are wrong** — see the stale-documentation list below. No Cursor rules exist.
- `AGENTS.md` 是面向 OpenCode 的精简版（本文件是完整手册）；改精简规则只改 `AGENTS.md`，改完整事实同步两份。

### Documentation that contradicts the code

Verify against code before propagating any of these:

- Copilot says the repo has two modules; there are **three** (`:supperbanner` was extracted).
- README/Copilot describe puzzle gates interleaved between combat levels; `MainActivity.onLevelComplete` saves the next level then calls `coreView.advanceToNextLevel()` directly (`MainActivity.kt:201`). `PuzzleActivity` is a separate Settings entry with its own ten-level progression.
- Copilot says Room uses `fallbackToDestructiveMigration(true)`; `DatabaseProvider` registers four explicit migrations and **no** fallback, so a missing migration crashes rather than wiping saves.
- Copilot says many screens use ViewBinding/XML and lists only `values/` + `values-zh/` locales. Both are false — see Conventions below.
- Copilot frames `GameStateManager` as *the* cross-screen bus carrying pause/game-over/win; in practice only low-memory arrives through that flow. Pause, game-over, level-complete, and win use direct `GameCoreView` callbacks.
- `gui/GameHudScreen.kt` holds the **live** Compose HUD: `MainActivity.setContent` stacks `GameHudOverlay(state = hudState, …)` in a `Box` over `AndroidView(factory = { coreView })` (`MainActivity.kt:153`). It owns pause/resume/quit — do not delete or bypass it. The Canvas HUD is **not** replaced: `GameCoreView.onDraw` still calls `drawHeader(canvas)` (`ui/DrawHeader.kt` via `ui/GameHudFormatter.kt`), so the two coexist deliberately.
- `SupperBannerEffect` has **11** values (NONE, FADE, ZOOM_OUT, DEPTH, CUBE, ROTATION_GATE, COVERFLOW, STACK, PARALLAX, ACCORDION, SHADER — `supperbanner/.../SupperBannerTransition.kt`), each backed by one `develop_settings_supper_banner_effect_*` string (11 per locale × 4 locales). Adding an effect means touching the enum, `SupperBannerTransformers`, the DevelopSettings `when` mapping, and all four locales together.

## Build, Lint, and Tests

Run from the repository root:

```bash
./gradlew assembleDebug                         # Debug APK
./gradlew assembleRelease                       # Release APK; requires root keystore.properties
./gradlew :richtexteditor:assembleRelease        # Library AAR in richtexteditor/build/outputs/aar/
./gradlew :supperbanner:assembleRelease          # Library AAR in supperbanner/build/outputs/aar/
./gradlew :supperbanner:publishReleasePublicationToBuildRepoRepository   # com.young:supperbanner into supperbanner/build/repo
./gradlew testDebugUnitTest                      # Debug unit tests across modules
./gradlew test                                   # All unit-test variants
./gradlew :app:testDebugUnitTest --tests "com.young.aircraft.ui.GameCoreViewFormulaTest"
./gradlew :app:testDebugUnitTest --tests "com.young.aircraft.gui.SettingsActivityTest"
./gradlew :app:testDebugUnitTest --tests "com.young.aircraft.StringResourceTest"
./gradlew :app:checkAppLogUsage                   # AppLog-only rule, standalone; also a lint* dependency
./gradlew connectedAndroidTest                   # Requires device/emulator
./gradlew lintDebug                              # Debug lint, as in CI
./gradlew lint                                   # All lint variants
./gradlew clean
./gradlew assembleDebug lintDebug testDebugUnitTest  # CI-equivalent verification
```

Scope `--tests` to a **module** task (`:app:testDebugUnitTest`), not the root `testDebugUnitTest`: the root task does not contain the app test classes, so scoping there matches nothing. Unit tests live in `app/src/test` (game/user screens) and `developtools/src/test` (all tool screens, run with `:developtools:testDebugUnitTest`), plus 1 in `:richtexteditor` and 3 in `:supperbanner`. `connectedAndroidTest` is effectively dead — `app/src/androidTest` holds only the generated `ExampleInstrumentedTest` scaffold, though the `espresso-core` dependency is still declared. Unit tests use JUnit, Robolectric, Mockito, and Compose UI testing; `testOptions.unitTests.isIncludeAndroidResources = true` is set per module.

`.github/workflows/android.yml` runs debug assembly, lint, and unit tests on pushes/PRs to `main`, with Temurin JDK 17. Do not infer Gradle success from the exit code of a downstream command in a shell pipeline — grep for `BUILD SUCCESSFUL` or check `${PIPESTATUS[0]}`.

### Build Configuration and Prerequisites

- JDK 17; use the checked-in Gradle wrapper, not a system Gradle. Configure the Android SDK via `local.properties` or the environment.
- Kotlin DSL; dependency and plugin versions are centralized in `gradle/libs.versions.toml`. AGP uses built-in Kotlin: do **not** add `org.jetbrains.kotlin.android`; the app still requires `org.jetbrains.kotlin.plugin.compose`. Root `build.gradle.kts` *also* pins `kotlin-gradle-plugin` on the buildscript classpath so AGP's compose-mapping tasks can resolve a matching `compose-group-mapping` artifact — check both locations when upgrading Kotlin. **They already disagree**: the catalog has `kotlin-compose = "2.4.20"` while the root pin is `2.4.10`, and the comment justifying that pin still cites the older Compose version. Do not trust the comment.
- All four modules: compileSdk 37, minSdk 31. The app targets 37 and pins `buildToolsVersion = "37.0.0"`; the libraries rely on the AGP default.
- App ID/namespace `com.young.aircraft`; library namespace `com.young.richtext`.
- The app enables Compose and BuildConfig. **View Binding is disabled** (removed in `a6c3b6b`; no binding classes exist) and Data Binding is not used.
- Release enables R8 minification and resource shrinking via `app/proguard-rules.pro`. Signing loads root `keystore.properties` when present; it is untracked. `settings.gradle.kts` sets `FAIL_ON_PROJECT_REPOS`, so a new repository must go in the settings file, not a module.
- Firebase Analytics, Remote Config, and Crashlytics are configured in `app/build.gradle.kts`; `app/google-services.json` supplies the Firebase configuration. See "Remote Config and Forced Updates" below before touching Crashlytics — its collection is off by default.
- **Debug-only code is a source-set split, not a runtime flag.** `utils/DebugTools.kt` exists twice inside `:developtools`: `developtools/src/debug/` (`isEnabled = true`, `log`/`enableWebViewDebugging` live) and `developtools/src/release/` (`isEnabled = false`, all methods no-op). `SettingsViewModel` maps `DebugTools.isEnabled` into `SettingsUiState.showDevelopSettings`, the *only* gate for the Settings rows; each gated Activity then re-checks `DebugTools.isEnabled` in `onCreate` and calls `finish()`. Adding a debug screen means touching both `DebugTools` variants plus both gates — otherwise a release build crashes or silently shows dev UI. Keep the two variants' signatures in sync: `isEnabled` is a `var` in debug and a `val` in release, so a `main` source that assigns to it compiles in debug and fails in release.

## Architecture

### Two State Models That Must Not Be Blended

**Game engine — render-thread confined, not MVVM.** `MainActivity` hosts `ui/GameCoreView`, a `SurfaceView` implementing `SurfaceHolder.Callback` and `Runnable`. `surfaceCreated` builds the object graph once (`initializeGameDrawer()`) and starts `Thread(this)`; `run()` is a 30 FPS (`FPS = 30`) loop of `lockCanvas` → `synchronized(holder) { update + draw }` → `Thread.sleep` on the remainder. It owns composition, collision detection, timers, and boss/level progression. Drawable objects derive from `DrawBaseObject`; `GameCoreView` itself does not. Mutable state models live in `data/`. Do not refactor this hierarchy as if it were a Compose/MVVM screen.

**Activity/UI layer — Compose + Material3.** Every `gui/` Activity calls `setContent`; there are no ViewBinding hosts. `viewmodel/` exposes StateFlow/LiveData plus SharedFlow one-shot events where needed. `data/SettingsRepository` wraps SharedPreferences; `providers/DatabaseProvider` supplies the Room database. `GameViewModel` handles persistence and scoring, never the render loop.

**Nearly every screen extends `gui/BaseAircraftActivity`** — every `:app` screen except `MainActivity` and `MandatoryUpdateActivity` (a `ComponentActivity`, see below); `:developtools` carries its own same-shaped copy for its 13 tool screens. It is `final override fun onCreate`: call `initializeViewModel` first, then `initializeUI` only `if (!isFinishing)`. That guard exists because some `initializeViewModel` implementations finish the Activity (the `DebugTools` self-gates), and building UI afterwards would operate on a dead window. Follow the two-method shape for new screens rather than inventing a third base class.

`PuzzleActivity` is the fullest example of the newer split: the image-feed request, disk cache, and load state live in `viewmodel/PuzzleImageViewModel`, while the Activity only reads launch args, saves progress, and owns the Compose board. Board/piece/undo state uses `rememberSaveable` with a Saver so rotation restores the in-progress board.

`MainActivity` is the deliberate mixed case: Compose owns the screen chrome (HUD overlay, dialogs) while `GameCoreView` stays a plain `SurfaceView` hosted through `AndroidView`. New game-*surface* work belongs in the View; new *chrome* belongs in Compose. Game dialogs and the hall-of-heroes sheet use Compose content via `gui/dialogs/` even though the game host uses Views.

### Thread and Event Boundaries

Engine state is confined to the render thread; other threads never mutate game objects directly, they submit work:

- `gameCommands: ConcurrentLinkedQueue<() -> Unit>` — `pauseGame()`, `resumeGame()`, `advanceToNextLevel()`, and `onKeyDown` enqueue a lambda; the loop drains the queue at the top of each frame, inside the `SurfaceHolder` lock. `advanceToNextLevel()` is a public enqueue wrapper; the body is private `advanceToNextLevelOnGameThread()`. **Any new Activity/UI entry point that touches game state must enqueue, not assign.**
- `pendingPlayerTouch: AtomicReference<PlayerTouch?>` — `onTouchEvent` records the latest ACTION_MOVE coordinates; the loop applies them to `drawAircraft.jetX/jetY` before drawing. Only the newest touch survives, so drag latency is one frame.
- `isRunning` and `musicService` are `@Volatile` for cross-thread visibility.
- `GameCoreView.post { … }` dispatches game-over / level-complete / win callbacks **to the UI thread**. It is not a queue onto the game thread — do not treat it as a substitute for `gameCommands`.
- `common/GameStateManager` is a singleton with a `MutableSharedFlow<GameState>` (`extraBufferCapacity = 1`) plus a plain `var isInvincible`. Only `onLowMemory` routes through it today (`AircraftApplication.onLowMemory` → `emit(LOW_MEMORY)` → `MainActivity` pauses and shows the pause overlay). It is a reasonable place to add cross-screen events, but do not assume existing completion flows use it.
- `surfaceCreated` also owns the background-resume compensation: it shifts `levelStartTimeMs` forward by the paused duration so time spent backgrounded does not count against the level timer.
- The game propagates time-freeze state into the player, enemies, and boss before updates. Changes to movement or projectiles must preserve freeze behavior across all three.
- **Non-game features have no thread-confinement rules.** They are ordinary `Dispatchers.IO` + `viewModelScope` and share nothing with the render thread.

### Services

`service/` holds two unrelated components, both of which must outlive the screen that starts them:

- `MusicService` is a **bound** service (`MediaPlayer` for BGM, `SoundPool` for SFX) with synchronized playback methods. It is not a foreground service.
- `FlashlightService` owns the camera torch as a foreground service with `FOREGROUND_SERVICE_TYPE_CAMERA`, and holds a `PARTIAL_WAKE_LOCK` (`Aircraft::FlashlightSos`) during SOS so blink pacing stays accurate with the screen off. It promotes to foreground *before* touching the torch — required within 5s of `startForegroundService`, and it needs the `FOREGROUND_SERVICE_CAMERA` + `WAKE_LOCK` + `POST_NOTIFICATIONS` permissions, declared in both the app and the `:developtools` manifests (merger dedupes them).

### Theme and Transient UI

`SettingsRepository` persists five theme identifiers (green/blue/purple/yellow/red). `ui/theme/AircraftTheme.kt` maps them through `themeAccent` / `aircraftColorScheme` and listens to preference changes for live Compose updates. `AccentGreen` and `DividerGreen` are composable getters despite their legacy names: read them in composable scope, never inside a Canvas draw callback or an ordinary Activity method. Native UI resolves the shared scheme from the repository.

`StarFieldView` has its own preference listener and renders theme-specific glyph particles on the launch/onboarding/privacy screens. It is separate from `DrawBackground` and the combat loop; a UI theme change must not replace combat backgrounds.

- Compose-content dialogs and bottom sheets go through `Dialog.setDialogComposeContent(host)` (`gui/dialogs/DialogCompose.kt`). It supplies lifecycle owners, `AircraftTheme`, and `LocalDialogDismiss`, and installs content **after** showing the dialog because AppCompat otherwise replaces it. `GameDialogContent` dismisses on a negative action even without a caller callback.
- Native confirmations use `MaterialAlertDialogBuilder.showThemed()` (`gui/dialogs/ThemedAlertDialog.kt`) to share surface/outline/text colors and stateful enabled/disabled button colors. Preference listeners are removed on dismissal.
- Foreground feedback uses `ThemedMessage.makeText(…).show()` — a themed Material Snackbar, **not** a system Toast. It requires an attached, started Activity (wrapped Activity contexts included) and cleans up its listeners when the Activity stops. System permission prompts, pickers, and notifications stay system-styled.

### Navigation and Progression

First launch: `PrivacyPolicyAcceptActivity` → `OnboardingActivity` → `LaunchActivity` → `MainActivity`. Stored privacy/onboarding preferences skip completed gates. The launch hub also opens history, settings, and the QR utility; developer tools are reachable from Settings **only in debug builds**. `MainActivity` handles its own `configChanges` (orientation, screenSize, screenLayout, smallestScreenSize, keyboardHidden) and is never recreated on rotation.

Combat is ten timed levels with rising kill targets and a boss after each target. The timer pauses during boss fights; defeating the boss completes the level. `GameCoreView` and `Enemies` companion objects hold the difficulty and level formulas.

`LaunchViewModel` offers continuation only when `(level > 1 || score > 0)` **and** `gameMode == AIR_BATTLE`. Launch and Main transfer the starting level, jet resource/index, and total kills through `AircraftConstants.IntentExtras`; preserve kills on resume so cumulative score survives.

**"Continue" is a level checkpoint, not a scene snapshot.** It restores the saved combat level, cumulative kills, and jet — not current health, remaining time, or on-screen objects. The level restarts from scratch. Don't add snapshot persistence unless the product asks for in-place resume.

`SettingsActivity` navigates through an 11-value `SettingsDestination` enum in `SettingsScreen.kt` (GAME_SETTINGS, DEVICE_INFO, QR_CODE_TOOL, FLASHLIGHT, PUZZLE, LANGUAGE, ABOUT_AIRCRAFT, ABOUT_ME, PRIVACY_POLICY, DEVELOP_SETTINGS, ASSISTANT_TOOLS) mapped to Activity classes in `navigateTo`. The last two are debug-only rows; add a new Settings entry in all three places (enum, `navigateTo` `when`, the `SettingsScreen` row).

`HistoryActivity` is the leaderboard — a Compose screen (not a fragment/RecyclerView flow) reading `dao.getAllByScoreDesc()`. Player names resolve through `utils/HallOfHeroesNameUtils.kt`: a blank name falls back to the caller's `anonymousLabel`, and failing that to `truncatePlayerId(playerId)` (first 6 chars plus an ellipsis). Both that util and the DAO ordering have unit tests, so the anonymous-player path is worth routing through the util rather than inlining a fallback at a new call site.

`DeviceInfoActivity` is Compose but its layout is foldable-aware via Jetpack WindowManager: inside `repeatOnLifecycle(STARTED)` it collects `WindowInfoTracker.getOrCreate(this).windowLayoutInfo(this)`, takes the first `FoldingFeature` from `displayFeatures`, and sets `systemInfoWide` when `foldFeature.state == FoldingFeature.State.FLAT`. That flag widens the Current Time and System Info rows side by side; every other posture stacks them. Adding a card here means deciding which layout it takes in both postures.

`SettingsActivity` also opens the independent Compose drag-and-drop `PuzzleActivity`, which saves through `GameViewModel` with `GameMode.PUZZLE`. Do not assume a stored mode implies the launch hub can resume it. Puzzle scoring is efficiency-based (par = `gridSize²` moves; scans and retries subtract; 1–3 stars) and lives in the pure internal top-level `calculatePuzzleRoundResult()` / `createPuzzlePieces()` in `PuzzleActivity.kt` — see `docs/puzzle-game-redesign-plan.md`. There is no constant reference image on the board; the player spends limited "intel scans" to peek at it.

### Debug-Only Screens

`DevelopSettingsActivity` (in `:developtools`) is the debug hub (crash tooling, invincibility toggle, banner effect lab, and launchers for `RichTextEditorActivity` / `CameraScanActivity` / `PdfReaderActivity` / `ShowImageDetailsActivity`). `AndroidDevAssistantToolsActivity` is a sibling hub under the same `showDevelopSettings` gate, reading device facts through **native APIs only** — `Os.uname()`, `WebView.getCurrentWebViewPackage()` (API 26+), `/proc/version` — deliberately *without* androidx.webkit or any DI. Its per-module facts are pure internal top-level functions (`readKernelInfo()`, `readBrowserEngineInfo(context)`) returning `internal data class`es, so they are unit-testable without an Activity. It hosts **four** modules in `ASSISTANT_MODULES` (`MODULE_SYSTEM_INFO`, `MODULE_QUICK_SETTINGS`, `MODULE_APP_BROWSER`, `MODULE_CONTACTS`) plus the app-logs toggle; adding one means touching the constant, the `ASSISTANT_MODULES` entry, and `openModule`'s `when`. Module toggles live in their own `SharedPreferences` (`ASSISTANT_PREFS`), not `SettingsRepository`, and a disabled module hides the row without preventing a direct Intent. It uses an `ACTION_BAR`-free Compose shell with literal debug-only colors — a dev tool, so it is exempt from the tactical-UI palette rule below.

### Contacts (debug-only)

`ContactsActivity` is a Compose browser/editor over the **platform `ContactsContract` provider** — not Room, no local DB. Reached via Settings → `ASSISTANT_TOOLS` → `AndroidDevAssistantToolsActivity` → the `MODULE_CONTACTS` row, and self-gated in `onCreate` with `if (!DeveloperMode.isEnabled(this)) { finish(); return }`. It lives in `:developtools/src/main` (not a `debug` source set) because the gate is a runtime check, not a source-set split.

- `READ_CONTACTS` **and** `WRITE_CONTACTS` are both required before the list renders; a read-only grant is deliberately unusable. `onPermissionResult` ORs new results with the previous value so a partial grant never downgrades.
- `ContactsRepository(resolver: ContentResolver)` takes the **resolver, not a Context** — that is the test seam (`ContactsRepositoryTest` mocks it and feeds a hand-built `MatrixCursor`). `ContactsViewModel` is an `AndroidViewModel` only to reach `application.contentResolver`.
- `observeContacts()` merges the platform `ContentObserver` with a local `MutableStateFlow` refresh counter, then `conflate()` + `transformLatest { delay(300) }`, so a burst of writes collapses into one re-query. Writes bump the counter because self-issued provider notifications are not reliably timely.
- **Row shaping is not 1:1 with contacts**: one contact with N phones becomes N rows sharing name/email/address; a contact with no phone row produces *zero* rows. HOME-typed email and address win, work email is a fallback, and non-HOME postal rows are dropped entirely.
- `add` is a single `applyBatch`; name/phone are mandatory ops while email/address are appended only when non-blank. In `updateOptionalData`, clearing an existing optional field **deletes that Data row** rather than writing an empty string. `delete` targets `RawContacts.CONTENT_URI` by `CONTACT_ID`, cascading all rows at once.
- `add`/`update` re-validate in the ViewModel (`isValidChinaPhoneNumber`, `isValidOptionalEmail` in `data/`) and silently return on failure — a second line of defense behind the dialog's disabled Save button. A blank email is *valid* by design.
- The contacts permissions are declared in the **main** manifest, so they ship in release builds even though the screen is unreachable there.

### App Logging

`AppLog` is a **runtime kill-switch for `android.util.Log`**, not a logging framework: no file sink, no rotation, no Timber. Use `AppLog` for all application logging; direct `android.util.Log` access is allowed only inside `utils/AppLog.kt`. The `checkAppLogUsage` Gradle task enforces this and is wired as a dependency of every `lint*` task; it scans `src/main` only (not the debug/release source sets) and fails on any `import android.util.Log` or `Log.` reference.

Its `d(tag) { … }` lambda skips message construction and interpolation while logging is disabled, so the hot game loop pays one volatile read. `@Volatile` is load-bearing because the render thread reads it.

**`d` is deliberately overloaded, not given a trailing `tr`.** `d(tag, msg: String, tr: Throwable? = null)` sits *beside* `d(tag, msg: () -> String)`. Had the lambda instead been appended as a fourth parameter, a trailing-lambda call `AppLog.d(tag) { … }` would silently bind to `tr`. Both call forms compile, so an accidental `AppLog.d(tag, { … })` is easy to write and invisible in review — keep lambda messages in trailing position.

`:developtools` `LogSettings` persists the toggle in DataStore Preferences (`log_settings`, single `log_enabled` key) and exposes `enabledFlow` + `suspend setEnabled`, which writes DataStore *and* eagerly assigns `DevLog.enabled` (forwarded to the app-wide `AppLog.enabled` through the `DevTools.onLogEnabledChanged` hook) so the switch lands on the same frame. A stored preference wins over `defaultEnabled`; the latter is only the fallback for a first run or a failed read. `AircraftApplication.onCreate` seeds both `LogSettings.defaultEnabled` and `AppLog.enabled` from `BuildConfig.DEBUG` **before** collecting the flow, so early-startup logs are not lost, with `.catch` falling back to that seed if the read throws. `LogSettingsViewModel` uses a hand-rolled `Factory`, and its `StateFlow` is seeded from `DevLog.enabled` (not disk) so the toggle renders correctly on frame one. The toggle is a `LogSwitchRow` (testTag `assistant_switch_app_logs`) on the assistant-tools screen, so — like its host — it is debug-reachable only.

### PDF Reader

`PdfReaderActivity` wraps the platform `android.graphics.pdf.PdfRenderer` — no PDF dependency. `PdfViewModel` owns the document and all rendering; the Activity only holds Compose state.

- One `PdfRenderer` guarded by a `Mutex`. `openPage` is not safe from concurrent coroutines, and every render/aspect-ratio call goes through `withLock { withContext(Dispatchers.IO) { … } }`.
- `%PDF-` magic is sniffed in the constructor (`hasPdfHeader`) because `PdfRenderer` only rejects encrypted or unreadable files at page-open time — failing early keeps the error out of the UI. Errors map to `pdf_reader_error_no_permission` (SecurityException) vs `pdf_reader_error_invalid`.
- **Zoom re-renders rather than scaling a bitmap.** A pinch updates `scale` continuously for transform only; `settledScale` (written on gesture end) drives a re-render at `renderWidthFor(baseWidthPx, settledScale)`, clamped to `MAX_RENDER_WIDTH_PX = 2048` (~16MB ARGB_8888, a page-safe ceiling). Rendering per frame during a pinch is the failure mode to avoid.
- The page cache is an `LruCache<String, Bitmap>` sized at `maxMemory() / 16`, keyed by `index@width`, with `sizeOf` returning `value.byteCount`. A page keeps its previous bitmap while the sharper one renders, so zoom does not flash white.
- `dominantPageIndex()` (internal top-level in the Activity file, covered by `DominantPageIndexTest`) derives the header page counter. It does not use the naive centre rule alone: a short trailing page such as a blank back page breaks it, so an unscrolled-past last page wins outright.
- The file arrives via `ActivityResultContracts.OpenDocument` with `takePersistableUriPermission`, so the grant survives process death.

### Networking

There is **no Retrofit usage** despite the declared dependency — network calls are direct OkHttp requests inside `BannerDetailsViewModel`, `ShowImageDetailsViewModel`, and `PuzzleActivity`. The Bing wallpaper (peapix) feed in `PuzzleActivity` is parsed by **regex** in `AircraftConstants`, not a JSON parser. There is no DI framework and no service layer; follow that pattern unless asked otherwise.

### Remote Config and Forced Updates

`common/AircraftRemoteConfig.kt` is a plain `object` (the app's established no-DI pattern) wrapping `FirebaseRemoteConfig` with a typed accessor per parameter. `AircraftApplication.onCreate` calls `initialize()` — it is `@Synchronized` and one-shot, sets `minimumFetchIntervalInSeconds` to 0 in debug / 1h in release, then chains `setDefaultsAsync` → initial `fetchAndActivate`, and separately registers a real-time `addOnConfigUpdateListener`. Defaults are a hardcoded `defaults` map, so a fetch failure degrades to bundled values rather than throwing; a malformed version string makes `compareVersions` return null and the check **fails open** (treated as "no update needed").

Four parameters: `Aircraft2026` (a JSON blob parsed into `RemoteTokenConfig`), `minimum_version`, `latest_version`, `update_url`. The seven `remote_update_*` strings were added to all four locales; new ones must follow suit.

- **Repo-JSON fallback.** `app-update.json` at the repo root (`minimum_version`/`latest_version`/`update_url`, same key names) is the fallback whenever Firebase holds no *remote* value for a key — fetch failed, unreachable, or key absent. `common/RepoUpdateConfig.kt` fetches it at startup via jsDelivr (`cdn.jsdelivr.net/gh/…@main/…`, ICP-filed) with a `raw.githubusercontent.com` fallback; `RepoUpdateConfigStore` effective getters return the Firebase value when its source is `VALUE_SOURCE_REMOTE`, else the repo value, else the Firebase default. The enforce/optional-update/`openUpdatePage` call sites all read through it. The file is **generated, not hand-edited**: `:app:generateRepoUpdateConfig` writes it from `versionName` (plus `-PminimumVersion=` override, default `1.4.1`) and runs automatically before every `assembleRelease`/`bundleRelease`. After a release build, commit the regenerated file to `main` (CDN serves `@main`, not `develop`) and allow CDN cache lag before expecting new values on device.
- **Version numbering is decimal with segment rollover.** `versionCode` increments by exactly 1 per release. `versionName` bumps the patch segment normally (`1.4.3` → `1.4.4`); when the patch would exceed 9 it rolls into the minor segment instead (`1.4.9` → `1.5.0`); when the minor likewise rolls past 9 it advances the major segment (`1.9.9` → `2.0.0`), and so on. There is no "1.4.10" — after `x.y.9` comes `x.(y+1).0`.

- **Crashlytics collection is remote-gated.** The manifest sets `firebase_crashlytics_collection_enabled=false`, and `updateCrashlyticsCollection()` re-enables it only when `Aircraft2026.enable == true`. So a build without live Remote Config collects no crashes — do not "fix" a missing crash report by removing the meta-data.
- **Enforcement lives in `ActivityLifecycleCallbacks`, not in a Base class.** `onActivityResumed` calls `enforceMinimumVersion(activity)`, and `onConfigActivated` re-posts it to the main thread, so a real-time update takes effect on the next resume. `foregroundActivity` is a `WeakReference` set on resume and cleared on pause.
- `MandatoryUpdateActivity` is the only new-style screen that **cannot** extend `gui/BaseAircraftActivity` usefully: it must not be finishable by the same check that launches it, so it re-checks on its own resume (`enforceMinimumVersion` finishes it once the requirement is lifted) and swallows back with `BackHandler(enabled = true) {}`. `openUpdatePage(context)` is an internal top-level function in that file: it tries the configured URL, then the GitHub release tag page built by `githubReleaseTagUrl()` from `latest_version` (falling back to `minimum_version`), then `market://`, then the `play.google.com` web URL, and silently no-ops if neither handler exists. The GitHub page sits ahead of the Play fallbacks because mainland-China devices have no Play handler.
- The optional-update dialog is shown through `showThemed()` and de-duplicated per process by `optionalPromptedVersion` (last-prompted version string), so it appears at most once per version per launch.

### Persistence and Scoring

`DatabaseProvider` is a double-checked singleton building `aircraft_game.db` with `AppDatabase` (version 2031), registering `MIGRATION_2027_2028` → `2028_2029` → `2029_2030` → `2030_2031`. **There is no destructive-migration fallback**, so a schema change without a registered migration crashes on upgrade rather than silently wiping saves. `setDatabase()` exists for tests.

`PlayerGameData` / `PlayerGameDataDao` represent `player_game_data`: combat and puzzle levels/scores, mode, total kills, player name, jet resource/index, difficulty. `GameViewModel` identifies the player by an install ID from `SettingsRepository`. Combat score is `totalKills * 100`.

**All progress writes go through `PlayerGameDataDao.replaceForPlayer()`** — a `@Transaction` default method that reads the newest row, merges the fields belonging to the *other* game mode, and replaces the record atomically. Do not reintroduce read-then-delete-then-insert in `GameViewModel`; it loses data on failure and interleaves under concurrent saves. A PUZZLE save preserves `airBattleLevel`, an AIR_BATTLE save preserves `puzzleLevel`/`puzzleScore`, and a null `playerName` keeps the stored name.

**Save before finishing:** keep `finish()` inside the `lifecycleScope` coroutine *after* the suspend save completes, as in `MainActivity.saveCurrentProgress()` callers and `PuzzleActivity`. Finishing alongside the coroutine can cancel the Room write.

### Library Boundaries

Both libraries are plain Android Views with no strings, no game dependency, and no app theme imports — keep it that way. A host maps its own theme into the library's palette rather than the library importing `AircraftTheme`.

**`:richtexteditor`** supplies exactly one class, `com.young.richtext.RichTextEditorView` (~340 lines). The `:developtools` Activity owns mode switching, sample loading, WebView preview, and image-viewer navigation. Its optional `onMessage` callback lets `RichTextEditorActivity` and `QRCodeToolActivity` route editor feedback through `ThemedMessage`, while standalone AAR consumers keep a system Toast fallback. Large unbroken or base64 content must not be inserted unchanged into the native EditText — native text layout can exhaust memory. `RichTextEditorActivity` skips oversized default content (`MAX_EDITABLE_LENGTH`) and sanitizes inline data-image tags via `makeHtmlEditable()` for JSON examples. Preserve these guards; use WebView preview rather than native layout for large raw HTML.

**`:supperbanner`** supplies `SupperBannerView` plus the `SupperBannerItem` / `SupperBannerImage` / `SupperBannerConfig` / `SupperBannerColors` / `SupperBannerEffect` / `SupperBannerTransition` types. Every color it paints is a field on `SupperBannerColors` (indicators on `SupperBannerIndicatorColors`) applied through `setColors()` — the view holds no literal colors. A customizer installed via `setIndicatorCustomizer` runs *after* the palette and wins on the dots; use the palette for color and the customizer for what it cannot express (typeface, stroke width, shape).

Page transitions are `ViewPager2.PageTransformer` instances built by the internal `SupperBannerTransformers` from a `SupperBannerTransition`; `setTransition(NONE)` restores the default slide. Every transform resets stale view state first, because ViewPager2 recycles pages. The `SHADER` effect compiles AGSL at construction inside a `try/catch` that degrades to `FADE` — keep that fallback, since a bad shader string must not take the view down, and note that Robolectric never exercises real GPU shader compilation. `PARALLAX` offsets only the image layer; the info panel sits outside the pager and is re-bound in `onPageSelected`, so text deliberately does not parallax. The click contract and the details/download flow live in `:developtools` (`ShowImageDetailsActivity` + `ShowImageDetailsViewModel`; the legacy `BannerDetailsActivity` is unreferenced); `DevelopSettingsActivity` hosts the view through Compose `AndroidView` and supplies its own indicator styling. Unlike `:richtexteditor`, this module applies `maven-publish` and publishes `com.young:supperbanner` to a local file repo under `supperbanner/build/repo` — see [docs/supper-banner-aar-usage.md](docs/supper-banner-aar-usage.md).

## Repository-Specific Conventions

### UI and Localization

- Match the tactical UI: dark background `#0F1118`, header `#161A26`, the selected theme accent (green `#00FF88` by default), monospace typography, and a 52dp header. Use the shared `AircraftTheme` and its color scheme instead of hardcoding decorative green, and preserve each screen's existing layout.
- Solid-background utility Activities use `Theme.Aircraft.Common` in the manifest; only `MainActivity` uses `TransparentMaterialTheme`. The `<application>` tag carries `TransparentTheme`. Preserve each screen's inset handling — Compose screens use `safeDrawingPadding` / `statusBarsPadding` / `navigationBarsPadding`, or Scaffold `contentWindowInsets` (the Settings header uses `statusBarsPadding`). Do not double-apply insets.
- On tablets and foldables, Compose screens cap their content column with `Modifier.maxContentWidth()` (`ui/WideScreen.kt`, 640dp) — the counterpart of `@dimen/content_max_width` in `values-sw600dp/` (`-1px` in the default bucket). Use the modifier rather than inventing another width cap.
- There are **four** locales: `values/`, `values-zh/`, `values-zh-rTW/`, `values-zh-rHK/`. `StringResourceTest` scans *every* `values-*` dir that has a `strings.xml`, requires the full default key set in each, **and** fails on any string defined in `values/` but never referenced from `main` (`R.string.*`, XML `@string/`, or the manifest) minus an 8-entry Firebase whitelist. It currently passes with 0 unused, so it is a guard rail rather than a standing failure — but deleting the last usage of a string breaks the build, which is the intended behavior. Adding a string means adding it in all four. Use `stringResource` / `getString` / `@string/`, and remove orphans after refactoring.
- `values-night/`, `values-sw600dp/`, and `values-sw1240dp/` exist but hold **no `strings.xml`** — only `dimen`/`integer` resources, so `StringResourceTest` ignores them. The four locales above are the only ones needing string parity.
- In-app language switching goes through `AppCompatDelegate.setApplicationLocales` in `LanguageSettingsActivity`. There is **no `res/xml/locales_config.xml`**; persistence below API 33 comes from the manifest declaring `AppLocalesMetadataHolderService` with an `autoStoreLocales` meta-data value of `true`.

### Testing Conventions

- Robolectric Compose screen tests use `createAndroidComposeRule` (not `createComposeRule`) and `@GraphicsMode(NATIVE)`.
- For scrollable content, follow `SettingsActivityTest`'s tall viewport (`w420dp-h2000dp`). Off-window clicks may silently do nothing rather than fail — a new row near the bottom of `AndroidDevAssistantToolsActivity` once pushed a module tile out of the default viewport and turned `performClick()` into a no-op.
- **Never call `waitForIdle()` on a screen containing an indeterminate progress indicator.** Robolectric's default choreographer re-fires vsync inline, so Compose never reaches idle and the test hangs until timeout — `AppListDialog`'s `CircularProgressIndicator` did exactly this at 60s. Pause the choreographer and drive a bounded number of frames instead.
- Tests that exercise `AppLog` or `LogSettings` must pin `@Config(sdk = [34], application = Application::class)`. The real `AircraftApplication` seeds `AppLog` from DataStore on a background coroutine and keeps its own `LogSettings` instance and collector alive for the whole test, which double-reads the same file and can flip the switch mid-assertion. `LogSettingsTest` additionally deletes `filesDir/datastore` in `@Before`. `SettingsViewModel` must be stubbed in screen tests.
- Extract logic worth testing into pure internal top-level functions or `internal data class`es rather than testing through an Activity — the pattern already used by `readKernelInfo()`, `dominantPageIndex()`, `calculatePuzzleRoundResult()`, and `GameCoreView`'s companion formulas.

### Implementation Traps

- Never use Kotlin `!!`; use safe calls, explicit null guards, or `requireNotNull`/`checkNotNull` for programming errors.
- Distinguish `data/PlayerAircraft.kt` (usually imported as `AircraftData`) from the rendered `ui/Aircraft.kt`.
- Preserve `bitmap.density = screenDensity` for game sprites, or Canvas scaling will be wrong.
- Generated QR codes are light-on-dark. Keep ZXing's inverted-source fallback when decoding them (`decodeQrFromBitmap` in `QRCodeToolActivity`). Picked images decode via ZXing; live scanning is `CameraScanActivity` (CameraX `MlKitAnalyzer`), a debug-only entry from `DevelopSettingsActivity`.
- File sharing uses the existing `${applicationId}.fileprovider`, `res/xml/file_paths.xml`, and `FilePickerHelper`; share content URIs with `FLAG_GRANT_READ_URI_PERMISSION`.
- `common/AircraftApplication` applies two app-wide policies in `ActivityLifecycleCallbacks`, so a new Activity inherits them for free. **Portrait lock** when `smallestScreenWidthDp < 600`, using `SCREEN_ORIENTATION_UNSPECIFIED` (not `FULL_USER`) on large screens so the user's rotation lock wins. **`FLAG_SECURE` in release builds only** — it is inside an `if (!BuildConfig.DEBUG)` block, so debug builds *can* be screenshotted; the reverse of what you may assume when a screenshot test mysteriously works. It also turns `onLowMemory` into `GameStateManager.emit(GameState.LOW_MEMORY)`.