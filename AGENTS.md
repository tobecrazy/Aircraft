# Repository Guidelines

## Project Structure & Module Organization
`Aircraft` is a single-module Android app. Main code lives in `app/src/main/java/com/young/aircraft`, split by responsibility: `gui/` for activities (including the privacy gate, onboarding carousel, history/settings/device/debug screens) plus fragments/adapters, `ui/` for the `SurfaceView` game engine and drawable game objects (aircraft, enemies, power-ups, coronas, effects), `data/` for Room entities/DAO, state models, and migration-aware helper enums, `service/` for audio playback, `common/` for global singletons such as `AircraftApplication` and `GameStateManager`, `providers/` for `DatabaseProvider`/`SettingsRepository`, and `utils/` for Canvas/bitmap helpers and shared name logic. Every drawable, layout, and string is under `app/src/main/res` branches (`drawable/`, `layout/`, `raw/`, `values/`, `values-zh/`). Unit tests live in `app/src/test`; device or emulator tests live in `app/src/androidTest`.

## Build, Test, and Development Commands
Run commands from the repository root:

- `./gradlew assembleDebug`: build the debug APK for local development.
- `./gradlew assembleRelease`: build the release APK without shrinking.
- `./gradlew test`: run JVM unit tests, including Robolectric-based UI tests.
- `./gradlew connectedAndroidTest`: run instrumented tests on a connected device or emulator.
- `./gradlew lint`: run Android lint checks before opening a PR.
- `./gradlew clean`: remove build outputs when Gradle or generated sources get stale.

## Coding Style & Naming Conventions
The project uses Kotlin with `kotlin.code.style=official`; follow standard Kotlin formatting with 4-space indentation. Use `UpperCamelCase` for classes (`MainActivityViewModel`), `lowerCamelCase` for methods and properties, and descriptive package names that match the current layout. Keep Android resources lowercase with underscores, for example `activity_main.xml` or `jet_plane_2.png`. No dedicated formatter such as ktlint or detekt is configured, so rely on Android Studio formatting and lint.

## Testing Guidelines
Write JUnit 4 tests in `app/src/test` and use Robolectric when Android framework behavior is needed. Keep instrumented tests in `app/src/androidTest` with `AndroidJUnit4`. Name test files after the subject under test, such as `StarFieldViewTest` or `GameStateManagerTest`. Prefer backtick test names that describe behavior, for example ``fun `emit does not throw for PLAYING state`()``. Add tests for gameplay formulas, state transitions, and UI flows when modifying those areas.

After any code change, first update or add the relevant unit tests, then run the full unit test suite with `./gradlew test` to ensure there are no regression issues. If the tests cannot be run, explicitly report the blocker and the affected coverage.

## Commit & Pull Request Guidelines
Recent history uses short, imperative subjects such as `Implement timer`, `Fix reject Button Issue`, and `fix background reset issue`. Keep commit titles brief, action-oriented, and without trailing punctuation. For pull requests, include a concise summary, linked issue or ticket, the Gradle commands you ran, and screenshots or a short recording for gameplay or UI changes.
