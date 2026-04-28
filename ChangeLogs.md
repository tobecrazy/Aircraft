# ChangeLogs

All notable changes to this project will be documented in this file.

## [Unreleased]

### Added
- `AboutMeActivityTest` to verify localized About Me labels, GitHub repo rendering, and back navigation under Robolectric
- `MainActivityTest` coverage for the tactical overlay shell, mission-briefing chips, quit flow, and low-memory pause behavior
- Class Diagram section in README with package overview table and key relationships summary
- QR tool hero/status copy for English and Chinese plus dedicated neon drawables for preview, scan, and action states
- Robolectric assertions covering QR tool ready/generated preview messaging
- Long-press save-to-device for generated QR codes via system file picker (`CreateDocument`), with editable filename and user-chosen save location
- Pick-from-gallery for QR scanning via Android photo picker (`PickVisualMedia`), with ZXing `RGBLuminanceSource` decode and inverted-luminance fallback for white-on-dark QR codes
- Bottom sheet scan result dialog (`BottomSheetDialog`) with selectable result text, Copy Result button, and tactical theme styling
- `bottom_sheet_scan_result.xml` layout and `qr_tool_sheet_background.xml` drawable for the QR scan result bottom sheet
- `ThemeOverlay.Aircraft.QrToolBottomSheet` style for the QR tool bottom sheet dialog
- New localized strings for QR save, gallery pick, invalid QR, and scan result copy in both English and Chinese
- Unit tests for QR save flow (file picker intent launch), gallery pick button, long-click behavior, bottom sheet result display, copy-to-clipboard, and inverted QR bitmap decode
- Coil image loading library (`io.coil-kt:coil:2.7.0` + `io.coil-kt:coil-compose:2.7.0`) for efficient network image loading with crossfade animations
- `ic_placeholder.xml` shape drawable for Coil View-based placeholder/error states

### Changed
- `MainActivity` now hosts gameplay inside a green tactical shell with a pause overlay, mission-briefing card, and launch-context chips for sector, difficulty, and airframe
- Gameplay HUD/dialog surfaces were refreshed to keep `MainActivity`, game dialogs, and the Hall of Heroes bottom sheet on a consistent green theme
- `OnboardingActivity` migrated from ViewPager2 + Fragments + XML layouts to Jetpack Compose with `HorizontalPager`, `AnimatedVisibility`, and `AnimatedContent`
- `StarFieldView` retained as a Canvas-based custom View wrapped via `AndroidView` composable
- Onboarding tests rewritten with `createAndroidComposeRule` and Compose test tags
- `AboutMeActivity` now reads developer and project copy from Android string resources for English and Chinese i18n
- The About Me project card now includes the canonical GitHub repository URL: `https://github.com/tobecrazy/Aircraft`
- `AboutMeActivity` image loading replaced with Coil `AsyncImage` using `ImageRequest.Builder` with `crossfade(true)` and `ColorPainter` placeholder/error states
- `AboutAircraftActivity` image loading replaced with Coil `ImageView.load()` with `crossfade(true)` and listener callbacks for progress/error visibility
- `HistoryActivity` layout background changed from solid dark color (`#0F1118`) to `launch_background.jpeg` (Chinese ink painting)
- `README.md`, `project_diagram.svg`, and `class_diagram.svg` updated for the Compose onboarding migration
- `README.md` project structure now lists `GameDifficulty.kt` and corrects `AppDatabase` version to v2030
- Architecture diagram summary added with packages, threading model, and first-launch gate chain
- `QRCodeToolActivity` now uses a hero-card layout, framed output panel, full-height camera scan state, and persistent bottom scan action instead of the previous flat stacked layout
- QR tool state handling now renders idle/scanning/generated preview text explicitly and reuses a single `SurfaceHolder.Callback`
- QR scan result display changed from `MaterialAlertDialogBuilder` to a styled `BottomSheetDialog` with Copy Result button
- QR tool camera scan view now includes a "Pick from Gallery" button for decoding QR codes from saved images
- QR tool preview hint now shows "Long press QR code to save" after generation
- Bumped compileSdk to 37, AGP to 9.1.1, Compose BOM to 2026.04.01
- `CLAUDE.md` updated with corrected build versions, accurate Compose/ViewBinding activity mapping, and new gotchas for inverted QR colors and bottom sheet dialog pattern

### Removed
- `OnboardingFragments.kt` (Controls + Power-ups fragments replaced by Compose pages)
- `activity_onboarding.xml`, `fragment_onboarding_controls.xml`, `fragment_onboarding_powerups.xml` (XML layouts replaced by composables)
- `indicator_dot.xml` (page indicator drawable replaced by Compose dot composables)

## [1.2.3] - 2026-04-02

### Added
- Top-record medal/star badge styling for the first entry in the leaderboard
- `HistoryAdapterTest` to verify first-place badge visibility and score color

### Changed
- README project details now match the current app version, asset counts, and module inventory

### Fixed
- Removed stale unused string resources that were failing `StringResourceTest`
- Updated `PlayerGameDataTest` equality coverage to use a fixed shared timestamp

## [1.1.3] - 2026-03-28

### Added
- Debug-only `DevelopSettingsActivity` with a controlled crash action for validating crash collection
- Hidden invincible-mode toggle via repeated taps on the developer screen version badge
- Firebase Analytics and Crashlytics integration
- `jet_plane_index` persistence and Room migration `2028 -> 2029`
- Robolectric coverage for background tiling and string-resource parity

### Changed
- Settings now act as a navigation hub for difficulty, audio, device info, about, privacy, and debug-only developer tools
- `DrawBackground` now uses mirrored seamless tiling and reload-safe background randomization
- Saved-game resume prefers the stored jet index and falls back to legacy resource IDs when needed
- README and SVG architecture diagrams were refreshed to match the current app structure

### Fixed
- Loading a saved game no longer risks starting without a plane sprite
- Background scrolling no longer shows seams or reset artifacts during redraw/reload scenarios

## [1.1.2] - 2026-03-22

### Added
- Time Freeze power-up system: player pickup freezes enemies for 5s, enemy pickup freezes player for 5s
- `TimeFreezeState` data class and `TimeFreezes` rendering class
- Frozen-state mechanic on `Aircraft`, `Enemies`, and `BossEnemy` so movement and bullets pause during freezes
- Shield power-up with 10s invincibility and blink effect
- `ShieldState` data class and `Shields` rendering class
- Unit tests for `TimeFreezeState`, `ShieldState`, and `GameCoreView` level formulas
- HUD freeze indicator in `DrawHeader`

### Changed
- Renamed `data/Aircraft.kt` to `PlayerAircraft.kt` to resolve the naming collision with `ui/Aircraft.kt`
- Collision detection expanded from 9 to 12 checks, including shield pickup, time-freeze pickup, and enemy bullets vs. player
- README and supporting documentation were updated for the new power-up systems

### Fixed
- Background reset issue when transitioning between levels
- Reject button behavior in the privacy-policy acceptance screen

## Architecture Diagrams (v1.2.3)

The class and project architecture diagrams ([class_diagram.svg](class_diagram.svg), [project_diagram.svg](project_diagram.svg)) document the following structure as of version 1.2.3:

### Packages & Layers

| Layer | Package | Key Classes |
|-------|---------|-------------|
| Common | `common/` | `AircraftApplication`, `GameStateManager` |
| Data | `data/` | `PlayerAircraft`, `EnemyState` + `EnemyBullet`, `BossState` + `BossBomb`, `RedEnvelopeState`, `RocketState`, `MedicalKitState`, `ShieldState`, `TimeFreezeState`, `PlayerGameData` (`@Entity`), `PlayerGameDataDao` (`@Dao`), `AppDatabase` (Room v2030), `GameState` (enum), `GameDifficulty` (enum) |
| Game Engine | `ui/` | `DrawBaseObject` (abstract), `Aircraft`, `DrawBackground`, `DrawHeader`, `Enemies`, `BossEnemy`, `RedEnvelopes`, `MedicalKits`, `Shields`, `TimeFreezes`, `ExplosionEffect`, `GameCoreView` (SurfaceView + Runnable) |
| Presentation | `gui/` | `PrivacyPolicyAcceptActivity` (LAUNCHER), `OnboardingActivity` (Compose + HorizontalPager), `LaunchActivity`, `MainActivity`, `HistoryActivity` + `HistoryFragment` + `HistoryAdapter`, `SettingsActivity`, `DeviceInfoActivity`, `AboutAircraftActivity`, `AboutMeActivity`, `PrivacyPolicyActivity`, `DevelopSettingsActivity`, `StarFieldView` |
| Service | `service/` | `MusicService` + `MusicBinder` |
| Providers | `providers/` | `DatabaseProvider`, `SettingsRepository` |
| Utilities | `utils/` | `ScreenUtils`, `BitmapUtils` |

### Threading Model

| Thread | Responsibility |
|--------|---------------|
| Main | Activity lifecycle, UI dialogs, service binding, Room DB via `lifecycleScope` |
| Game | Dedicated `SurfaceView` render loop at 30 FPS — Canvas drawing, collision, level timer |
| Service | `MusicService` with `@Synchronized` playback methods |

### First-Launch Gate Chain

```
PrivacyPolicyAcceptActivity (LAUNCHER)
  ├─→ [accept] → OnboardingActivity
  └─→ [reject] → finishAffinity()

OnboardingActivity
  ├─→ [skip/launch] → LaunchActivity
  └─ Compose HorizontalPager: ControlsPage + PowerupsPage
```
