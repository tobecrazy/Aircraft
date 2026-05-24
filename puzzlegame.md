# Android Compose UI — Sliding Puzzle Game Design Prompt

## Project Overview

Design and implement a fully functional **sliding puzzle game** for Android using **Jetpack Compose**. The game should support multiple difficulty levels, track player progress, and provide optional hint functionality. All UI must be built with Compose — no legacy XML layouts.

---

## 1. Difficulty Levels

The game must support three difficulty levels that control the number of puzzle pieces:

| Level      | Coefficient | Puzzle Grid (example base: 3×3 = 9) |
|------------|-------------|--------------------------------------|
| Easy       | × 0.8       | Fewer pieces (e.g., 2×2 = 4 tiles)  |
| Normal     | × 1.0       | Standard pieces (e.g., 3×3 = 9 tiles)|
| Hard       | × 1.2       | More pieces (e.g., 4×4 = 16 tiles)  |

> **Implementation note:** Define a base grid size (e.g., `BASE_GRID = 3`). Multiply by the coefficient and round to the nearest integer to derive the actual grid dimension (N×N). Apply minimum and maximum clamps to avoid degenerate grids (e.g., min 2×2, max 6×6).

### Level Selection UI
- Present a **level selection screen** before the game starts.
- Use clearly labeled `Button` or `RadioButton` Compose components for `Easy`, `Normal`, and `Hard`.
- Persist the selected level across the current session using `remember` / `ViewModel` state.

---

## 2. Puzzle Mechanics

### Board Setup
- Divide a source image into an **N×N grid** of equal tiles, where N is derived from the difficulty level.
- One tile is **removed** (replaced by an empty slot), creating the sliding mechanic.
- Shuffle the tiles at game start ensuring the resulting configuration is **solvable**.

### Tile Interaction
- The player taps a tile adjacent to the empty slot to slide it into the empty position.
- Use `Modifier.clickable` on each `Box` composable tile.
- Animate tile movement using `animateOffsetAsState` or `Animatable` for smooth sliding transitions.

### Win Condition
- Detect when all tiles are in their correct positions.
- Display a **win dialog / screen** showing total moves and total time elapsed.

---

## 3. Progress Tracking

Track the following statistics for each game session and display them in a persistent **stats bar** at the top or bottom of the game screen:

### Move Counter
- Increment a `moves` integer in the `ViewModel` on every valid tile slide.
- Display as: `Moves: 42`

### Timer
- Start a coroutine-based timer (`LaunchedEffect`) when the game begins.
- Pause the timer if the app goes to background (use `Lifecycle` observer or `DisposableEffect`).
- Display elapsed time in `mm:ss` format, e.g., `Time: 02:35`.
- Stop and freeze the timer when the puzzle is solved.

### ViewModel State (suggested)
```kotlin
data class GameUiState(
    val tiles: List<TileState>,
    val gridSize: Int,
    val moves: Int = 0,
    val elapsedSeconds: Int = 0,
    val hintsRemaining: Int = 3,
    val isHintVisible: Boolean = false,
    val isSolved: Boolean = false
)
```

---

## 4. Hint System

Each level grants the player **exactly 3 hint opportunities** per game session.

### Hint Behavior
- When the player taps the **"Hint"** button:
  1. Decrement `hintsRemaining` by 1.
  2. Show a **thumbnail overlay** of the completed puzzle image for **3 seconds**.
  3. Automatically hide the overlay after 3 seconds using `LaunchedEffect` + `delay(3000)`.
  4. The hint overlay should appear as a semi-transparent modal on top of the board.

### Hint Button States
| State                    | UI Behavior                              |
|--------------------------|------------------------------------------|
| Hints available (1–3)    | Enabled button, show remaining count     |
| No hints remaining (0)   | Disabled / grayed out button             |
| Hint currently showing   | Button disabled to prevent re-triggering |

### Hint UI Components
```kotlin
// Example hint button label
"Hint (${hintsRemaining} left)"

// Overlay composable (shown for 3s)
Box(
    modifier = Modifier
        .fillMaxSize()
        .background(Color.Black.copy(alpha = 0.6f))
        .clickable(enabled = false) {} // block interaction during hint
) {
    Image(
        painter = ...,
        contentDescription = "Puzzle hint",
        modifier = Modifier
            .size(200.dp)
            .align(Alignment.Center)
            .clip(RoundedCornerShape(12.dp))
    )
    Text(
        text = "Hint — ${remainingTime}s",
        color = Color.White,
        modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
    )
}
```

---

## 5. Screen Structure & Navigation

### Screens
1. **HomeScreen** — App title, difficulty selector, Start button, best scores (optional).
2. **GameScreen** — Puzzle board, stats bar (moves + timer), hint button, pause/restart controls.
3. **WinScreen / Dialog** — Displays final moves count, time taken, and a "Play Again" / "Change Level" option.

### Navigation
- Use `NavHost` from `androidx.navigation.compose`.
- Routes: `"home"` → `"game/{difficulty}"` → back to `"home"` on win or restart.

---

## 6. UI / UX Design Guidelines

### Visual Style
- Use **Material 3** (`androidx.compose.material3`) components and theming.
- Apply a clean, modern aesthetic: rounded tile corners (`RoundedCornerShape`), subtle elevation/shadow.
- Support both **light and dark themes** via `MaterialTheme`.

### Tile Design
- Each tile displays a **cropped subsection** of the source image using `Canvas` or `BitmapShader`.
- Display a **subtle tile number** (1-based index) in the corner for accessibility (optional, toggleable).
- The empty slot should be visually distinct: transparent background or a dashed border.

### Animations
- Tile slide: smooth positional animation (duration ~150ms).
- Hint overlay: fade-in / fade-out using `AnimatedVisibility`.
- Win state: confetti or a celebratory `Lottie` animation (optional enhancement).

### Accessibility
- Set meaningful `contentDescription` on all image tiles.
- Ensure touch targets are at least `48.dp × 48.dp`.

---

## 7. Technical Architecture

### Recommended Stack
| Concern           | Technology                                      |
|-------------------|-------------------------------------------------|
| UI Framework      | Jetpack Compose (latest stable)                 |
| State Management  | `ViewModel` + `StateFlow` / `collectAsState()`  |
| Navigation        | Navigation Compose                              |
| Image Loading     | Coil (`AsyncImage`) or `BitmapFactory`          |
| Coroutines        | `viewModelScope` + `LaunchedEffect`             |
| Persistence       | `DataStore` (for best scores, preferences)      |
| DI (optional)     | Hilt                                            |

### Puzzle Solvability Check
Implement a solvability check before presenting the shuffled board. A sliding puzzle is solvable if:
- For **odd N**: the number of inversions is even.
- For **even N**: (inversions + row of blank from bottom) is odd.

```kotlin
fun isSolvable(tiles: List<Int>, gridSize: Int): Boolean {
    val inversions = countInversions(tiles)
    return if (gridSize % 2 != 0) {
        inversions % 2 == 0
    } else {
        val blankRow = tiles.indexOf(0) / gridSize
        val rowFromBottom = gridSize - blankRow
        (inversions + rowFromBottom) % 2 != 0
    }
}
```

---

## 8. File / Module Structure (Suggested)

```
app/
├── ui/
│   ├── home/
│   │   └── HomeScreen.kt
│   ├── game/
│   │   ├── GameScreen.kt
│   │   ├── GameViewModel.kt
│   │   ├── PuzzleBoard.kt
│   │   ├── TileItem.kt
│   │   └── HintOverlay.kt
│   ├── win/
│   │   └── WinDialog.kt
│   └── theme/
│       ├── Color.kt
│       ├── Theme.kt
│       └── Type.kt
├── model/
│   ├── Difficulty.kt
│   ├── TileState.kt
│   └── GameUiState.kt
├── utils/
│   ├── PuzzleUtils.kt       ← shuffle, solvability check
│   └── ImageSplitter.kt     ← crop image into tiles
└── MainActivity.kt
```

---

## 9. Acceptance Criteria

- [ ] Difficulty level selection correctly adjusts the grid size using the 0.8 / 1.0 / 1.2 coefficient.
- [ ] Move counter increments on every valid tile slide.
- [ ] Timer starts at game begin, pauses on background, stops on win.
- [ ] Hint button shows thumbnail overlay for exactly 3 seconds.
- [ ] Hint count decrements correctly; button disables at 0.
- [ ] Win detection triggers the win screen/dialog with final stats.
- [ ] All animations are smooth and do not block user interaction.
- [ ] App handles rotation / process death via `ViewModel` state survival.
- [ ] Puzzle is always in a solvable state when presented to the player.

---

## 10. Optional Enhancements

- **Best Score Tracking**: Save best moves & time per difficulty using `DataStore`.
- **Custom Image Support**: Let users pick an image from the gallery (`ActivityResultContracts.PickVisualMedia`).
- **Sound Effects**: Play a subtle click sound on tile moves using `SoundPool`.
- **Undo Button**: Allow one step undo (deduct from a limited undo count).
- **Countdown Hint Timer**: Show a small circular progress indicator during the 3-second hint window.
- **Leaderboard**: Local top-5 scores per difficulty level displayed on the Home screen.
