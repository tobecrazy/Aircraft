# Changelog

All notable changes to this project will be documented in this file.

## [1.1.2] - 2026-03-22

### Added
- Time Freeze power-up system: player pickup freezes enemies for 5s, enemy pickup freezes player for 5s
- `TimeFreezeState` data class and `TimeFreezes` rendering class
- Frozen state mechanic on Aircraft, Enemies, and BossEnemy (movement/bullets pause when frozen)
- Shield power-up with 10s invincibility and blink effect
- `ShieldState` data class and `Shields` rendering class
- Unit tests for `TimeFreezeState`, `ShieldState`, and `GameCoreView` level formulas
- HUD freeze indicator in `DrawHeader`

### Changed
- Renamed `data/Aircraft.kt` to `PlayerAircraft.kt` to resolve naming collision with `ui/Aircraft.kt`
- Collision detection expanded from 9 to 12 checks (added shield pickup, time freeze pickup, enemy bullets vs player)
- Updated README.md and CLAUDE.md to reflect new features

### Fixed
- Background reset issue when transitioning between levels
- Reject button not working correctly in privacy policy screen
