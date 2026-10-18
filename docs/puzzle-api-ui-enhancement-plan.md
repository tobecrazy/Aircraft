# Puzzle API and UI Enhancement Plan

## Goal

Make the independent puzzle mode reliable when the image feed is slow or unavailable, make progress and recovery clear during play, and keep one authoritative puzzle session state through completion and exit.

The existing puzzle rules remain unchanged: ten levels, difficulty-based grids, three scans, retry penalties, and the current score formula. No Room schema or combat progression changes are required.

## Current State

`PuzzleActivity` owns launch arguments, persistence, the screen state, round timing, dialogs, and board callbacks. `PuzzleImageViewModel` owns image-feed parsing, downloading, and a per-level cache. The board already restores pieces and undo history with `rememberSaveable`.

The following gaps affect user-visible behavior:

- Image state is expressed as separate maps and sets, so the UI reconstructs loading, success, and error states from several fields.
- Downloads write directly to the final cache file and accept an unbounded response body. A partial response can become the next cached image.
- A cleared non-final level can be saved while it still points to the cleared level. Replaying it can add the round score again.
- The three equally sized action buttons compete for width, especially in Chinese locales and compact windows.
- The scan overlay gives no remaining-duration feedback and has no explicit accessible dismissal semantics.
- Completion, timeout, loading failure, and recovery are represented by separate ad-hoc flags, making transitions hard to test.

## Scope

| Area | Files | Required change |
| --- | --- | --- |
| Image API | `viewmodel/PuzzleImageViewModel.kt`, `data/AircraftConstants.kt` if needed | Replace parallel image-state collections with a sealed per-level load state; validate and atomically cache downloads; expose retry and refresh through the ViewModel. |
| Session API | `gui/PuzzleActivity.kt`, `viewmodel/GameViewModel.kt` only if a focused method removes duplication | Represent a puzzle session and its one-shot effects explicitly; persist the next playable level after a successful non-final round. |
| Board API | `gui/PuzzleActivity.kt` | Replace callback fan-out with one board event stream or a compact event lambda; keep the current Saver contract and scoring functions. |
| UI | `gui/PuzzleActivity.kt` | Give primary game actions clear hierarchy, expose image and scan state, and use a single state-driven overlay for guide, scan, result, timeout, and loading failure. |
| Resources | All four `values*/strings.xml` files | Add only labels and accessibility descriptions introduced by the UI. |
| Tests | `app/src/test/java/com/young/aircraft/gui/`, `app/src/test/java/com/young/aircraft/viewmodel/` | Cover state transitions, cache safety, next-level persistence, and pure puzzle rules. |

## Design

### 1. Image API

Introduce a `PuzzleImageLoadState` sealed type for the active level:

- `Loading` contains the requested level.
- `Ready` contains the level, local `Uri`, and whether the image came from cache.
- `Failed` contains the level and a user-safe failure category.

Keep diagnostic details in `AppLog`; do not render raw network exceptions in the product UI. The ViewModel exposes `loadLevel(level)`, `retryLevel()`, and `refreshLevel()` with a single active request per level. A new request cancels or supersedes the previous request before it can update active state.

Download to a temporary file in `cacheDir`, enforce a small image-size ceiling before reading or writing, verify the response is an image, then atomically rename it to the level cache file. Delete the temporary file on failure. Continue to use the existing feed URL parsing and thumbnail-first candidate order.

When a refresh fails and a valid cached file exists, retain the cache as `Ready` and surface a non-blocking stale-image notice. If no image can be loaded, show the retry state before the puzzle timer starts.

### 2. Puzzle Session API

Create a compact `PuzzleSessionState` in `PuzzleActivity` (or a dedicated ViewModel only if the state must survive process death beyond `rememberSaveable`). It owns level, cumulative score, moves, elapsed seconds, scans, retries, placed count, board reset token, and round result.

Model UI-triggered changes as small events: piece dropped, board solved, undo requested, scan requested, retry requested, continue requested, and save requested. The reducer-like update path must apply a solved result once per round. `PuzzleBoard` reports `Dropped`, `PlacedCountChanged`, and `Solved`; it does not know scoring, dialogs, image loading, or persistence.

After a non-final win, persist `level + 1` with the newly accumulated score. A save from the result dialog must use the same next playable level. The final win keeps level 10, preserves the current completion behavior, and returns the existing result extras.

### 3. UI

Use the existing Material 3 theme and dark puzzle palette. Keep the board dominant.

- Keep level, timer, score, and placed-piece progress in a compact header; give the timer a semantic content description.
- Put scan in the primary action position, display remaining scans, and move save and undo into icon buttons or an overflow action when width is constrained.
- Show an in-board loading/retry panel with a concise cause and a retry button. Keep image refresh as a secondary action once a cached image is playable.
- Show the scan as a modal image with a visible countdown and an accessible close action. Closing it early does not refund the scan.
- Use one overlay host for onboarding, scan, timeout, completion, and image failure. Each state blocks board input only when needed.
- Make result content scannable: stars, round score, total score, moves, time, scans, and retries. The primary button advances; save and exit stays secondary.
- Preserve current drag, snap, zoom, undo, touch-target, and rotation/fold restoration behavior.

## Implementation Order

1. Add pure state and transition tests for a completed round, retry, scan use, and next-level save target. Keep `calculatePuzzleRoundResult` unchanged and extend its existing tests only if its inputs are restructured.
2. Refactor `PuzzleImageViewModel` to the sealed load state and safe cache write path. Add tests for cached success, failed refresh retaining cache, invalid response rejection, and stale request suppression.
3. Introduce session state and board events in `PuzzleActivity`; route save, exit, continue, timeout, and completion through it. Confirm a cleared level cannot be scored twice after saving and reopening.
4. Rebuild the action row and overlays using the new state. Add four-locale strings in the same change.
5. Run the focused unit tests, then `./gradlew assembleDebug lintDebug testDebugUnitTest`.

## Acceptance Criteria

- A cached level opens without network; a failed refresh cannot replace a valid cache or block play.
- A failed initial load shows one clear retry path, and no timer runs before an image is ready.
- Only one image request controls the active level's displayed state.
- Completing a non-final level, then saving and reopening, starts the next level with the accumulated score and cannot award the prior round again.
- Scan, timeout, completion, guide, and image failure have deterministic, mutually exclusive UI states.
- Compact widths keep scan, undo, and save reachable and labeled; scan exposes its remaining duration to assistive technology.
- Existing scoring, board placement, undo, grid difficulty, saved board restoration, and Room schema remain compatible.

## Out of Scope

- New image providers, account sync, leaderboards, Room migrations, new scoring rules, and multiplayer features.
- Replacing Compose board interactions with a custom View or adding a new dependency.

## Risks and Mitigations

| Risk | Mitigation |
| --- | --- |
| Changing save target can affect users who expect to replay a cleared level. | Treat saved progression as the next playable level, which matches the existing Continue flow; do not change stored score format. |
| Feed response formats may vary. | Retain the current regex extraction and fallback candidates; report safe failures and keep cached images usable. |
| State refactor could regress rotation restoration. | Preserve current Saver payloads and add recreation coverage for pieces, undo stack, elapsed time, scans, and retries. |
