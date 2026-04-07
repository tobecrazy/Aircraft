# Changelog

All notable changes to this project will be documented in this file.

## [Unreleased]

### Added
- `AboutMeActivityTest` to verify localized About Me labels, GitHub repo rendering, and back navigation under Robolectric

### Changed
- `AboutMeActivity` now reads developer and project copy from Android string resources for English and Chinese i18n
- The About Me project card now includes the canonical GitHub repository URL: `https://github.com/tobecrazy/Aircraft`
- `README.md`, `project_diagram.svg`, and `class_diagram.svg` now document `AboutMeActivity` and its Settings navigation path

## [1.2.3] - 2026-04-02

### Added
- Top-record medal/star badge styling for the first entry in the leaderboard
- `HistoryAdapterTest` to verify first-place badge visibility and score color

### Changed
- README project details now match the current app version, asset counts, and module inventory
- `ChangeLogs.md` now points directly to the latest documented release entry

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
