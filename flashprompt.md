# Android Flashlight App — AI Coding Prompt

> **Stack:** Kotlin · Jetpack Compose · Material 3 · Camera2 API · Kotlin Coroutines  
> **Min SDK:** 26 · **Target SDK:** 34

---

## Project Setup

### `build.gradle.kts` dependencies

```kotlin
dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")
    implementation("com.google.accompanist:accompanist-permissions:0.34.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
```

### `AndroidManifest.xml`

Declare the `CAMERA` permission (required to access the torch):

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-feature android:name="android.hardware.camera.flash" android:required="false" />
```

---

## Architecture

Use **MVVM** with a single `FlashlightUiState` data class and a `FlashlightViewModel` that exposes state via `StateFlow`.

### `FlashlightUiState`

```kotlin
data class FlashlightUiState(
    val isOn: Boolean = false,
    val isSosMode: Boolean = false,
    val sosUnitMs: Float = 150f,          // base time unit in ms, range 50..500
    val brightnessLevel: Float = 1f,      // 0f..1f, mapped to device max level
    val maxBrightnessLevel: Int = 1,      // queried at runtime (API 33+)
    val isFlashAvailable: Boolean = false
)
```

### `FlashlightViewModel`

- Inject `Context` via `AndroidViewModel`.
- Obtain `CameraManager` from `context.getSystemService(Context.CAMERA_SERVICE)`.
- Register a `CameraManager.TorchCallback` in `init` to sync system torch state back to `_uiState`, preventing UI/hardware desync.
- Store the SOS coroutine as a `Job?`; cancel it before starting a new one.
- In `onCleared()`: cancel the SOS job, turn off the torch, and unregister the `TorchCallback`.

---

## Feature 1 — Basic On/Off Toggle

### Hardware control

```kotlin
fun toggleFlashlight(enabled: Boolean) {
    val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
        cameraManager.getCameraCharacteristics(id)
            .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
    } ?: return
    cameraManager.setTorchMode(cameraId, enabled)
    _uiState.update { it.copy(isOn = enabled) }
}
```

### Compose UI

- Center a large `Switch` on screen.
- Customise the track color to `Color(0xFFFFD700)` (gold) when checked.
- Show a label "Flashlight" next to the switch.
- Recommended switch size: `switchWidth = 80.dp`, thumb radius `= 24.dp` via `SwitchDefaults`.

```kotlin
Switch(
    checked = uiState.isOn,
    onCheckedChange = { viewModel.toggleFlashlight(it) },
    colors = SwitchDefaults.colors(
        checkedTrackColor = Color(0xFFFFD700),
        checkedThumbColor = Color.White
    )
)
```

---

## Feature 2 — SOS Blink Mode

### International Morse Code timing (· · · — — — · · ·)

| Signal | Duration |
|--------|----------|
| Short dot (·) | 1 unit ON + 1 unit OFF |
| Long dash (—) | 3 units ON + 1 unit OFF |
| Letter gap (S→O, O→S) | 3 units OFF |
| Full SOS cycle gap | 7 units OFF, then repeat |

The **base unit** is `sosUnitMs` (controlled by a Slider, 50 ms – 500 ms).  
Lower value = faster blinking.

### Coroutine implementation

```kotlin
private var sosJob: Job? = null

fun toggleSosMode(enabled: Boolean) {
    sosJob?.cancel()
    _uiState.update { it.copy(isSosMode = enabled) }
    if (enabled) {
        sosJob = viewModelScope.launch {
            val sos = listOf(
                // S: ...
                1, 1, 1, 1, 1, 1,
                // inter-letter gap
                0, 0,
                // O: ---
                3, 1, 3, 1, 3, 1,
                // inter-letter gap
                0, 0,
                // S: ...
                1, 1, 1, 1, 1, 1
            )
            while (isActive) {
                for (units in sos) {
                    val on = units > 0
                    setTorchDirect(on)
                    delay(abs(units) * uiState.value.sosUnitMs.toLong())
                }
                setTorchDirect(false)
                delay(7 * uiState.value.sosUnitMs.toLong()) // word gap
            }
        }
    } else {
        setTorchDirect(false)
    }
}
```

> **Note:** Always call `sosJob?.cancel()` before starting a new job to prevent multiple concurrent blink loops.

### Compose UI

- A toggle button labeled **"SOS Mode"** with a warning icon (`Icons.Outlined.Warning`).  
  Highlight it with a gold border when active.
- A `Slider` below, labeled **"Blink Speed"**:
  - `valueRange = 50f..500f`
  - `steps = 8`
  - Display current value as `"${value.toInt()} ms / unit"`
  - Left label: "Fast", right label: "Slow"

```kotlin
Slider(
    value = uiState.sosUnitMs,
    onValueChange = { viewModel.setSosFrequency(it) },
    valueRange = 50f..500f,
    steps = 8,
    colors = SliderDefaults.colors(
        activeTrackColor = Color(0xFFFFD700),
        thumbColor = Color(0xFFFFD700)
    )
)
```

---

## Feature 3 — Brightness Level Control

### API detection & hardware call

```kotlin
fun initBrightness() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val chars = cameraManager.getCameraCharacteristics(cameraId)
        val maxLevel = chars.get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAX_LEVEL) ?: 1
        _uiState.update { it.copy(maxBrightnessLevel = maxLevel) }
    }
    // API < 33: maxBrightnessLevel stays 1 → graceful degradation
}

fun setBrightness(level: Float) {
    _uiState.update { it.copy(brightnessLevel = level) }
    if (!uiState.value.isOn) return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && uiState.value.maxBrightnessLevel > 1) {
        val intLevel = (level * uiState.value.maxBrightnessLevel)
            .roundToInt()
            .coerceIn(1, uiState.value.maxBrightnessLevel)
        cameraManager.turnOnTorchWithStrengthLevel(cameraId, intLevel)
    }
    // else: brightness is binary — no extra call needed
}
```

### Graceful degradation (API < 33)

- Keep the `Slider` visible but map its value to ON/OFF only.
- Show an info chip below the slider:  
  `"Multi-level brightness requires Android 13+"`

### Compose UI

- Section title: **"Brightness"**
- `Slider` with `valueRange = 0f..1f`; display value as `"${(level * 100).roundToInt()}%"` on the right.
- Custom thumb using a sun icon (`Icons.Outlined.WbSunny`).
- Active track color: `Color(0xFFFFD700)`.

```kotlin
Slider(
    value = uiState.brightnessLevel,
    onValueChange = { viewModel.setBrightness(it) },
    valueRange = 0f..1f,
    thumb = {
        Icon(
            imageVector = Icons.Outlined.WbSunny,
            contentDescription = "Brightness",
            tint = Color(0xFFFFD700)
        )
    },
    colors = SliderDefaults.colors(
        activeTrackColor = Color(0xFFFFD700),
        inactiveTrackColor = Color(0xFF555555)
    )
)
```

---

## UI Layout & Visual Design

Use a **dark theme** throughout (`Background = #0A0A0A`, card surface `= #1A1A1A`).

### Screen structure (top → bottom)

1. **Top bar** — App title `"FlashLight"` + subtitle showing flash availability status.
2. **Torch animation** — A `Canvas` composable drawing concentric circles. When the torch is ON, animate the outermost ring's alpha with `animateFloatAsState` for a pulsing glow effect.
3. **Control cards** (rounded `16.dp`, background `#1A1A1A`, `animateContentSize()`):
   - **Toggle card** — main `Switch`
   - **SOS card** — mode button + frequency `Slider`
   - **Brightness card** — brightness `Slider`

### Theme tokens

```kotlin
object FlashTheme {
    val Gold = Color(0xFFFFD700)
    val Background = Color(0xFF0A0A0A)
    val Surface = Color(0xFF1A1A1A)
    val OnSurface = Color(0xFFE0E0E0)
}
```

All `Slider` active tracks and interactive accent elements use `FlashTheme.Gold`.  
All cards use `Modifier.animateContentSize()` to animate expand/collapse transitions.

---

## Permission Handling

Use Accompanist `rememberPermissionState` in `MainActivity`:

```kotlin
val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

LaunchedEffect(Unit) {
    if (!cameraPermission.status.isGranted) {
        cameraPermission.launchPermissionRequest()
    }
}

if (!cameraPermission.status.isGranted) {
    // Show AlertDialog explaining why CAMERA permission is needed.
    // On permanent denial, show a Snackbar with a "Open Settings" action.
    PermissionRationaleDialog(
        onConfirm = { cameraPermission.launchPermissionRequest() },
        onDismiss = { /* navigate to settings */ }
    )
}
```

---

## Lifecycle & Cleanup

Override `onCleared()` in `FlashlightViewModel` to release all resources:

```kotlin
override fun onCleared() {
    super.onCleared()
    sosJob?.cancel()                                  // stop SOS loop
    if (uiState.value.isOn) setTorchDirect(false)    // turn off torch
    cameraManager.unregisterTorchCallback(torchCallback) // release callback
}
```

This guarantees the flashlight never stays on after the app is killed or the screen is closed.

---

## Files to Generate

Please output complete, runnable code for the following files:

| File | Responsibility |
|------|---------------|
| `MainActivity.kt` | Entry point, permission handling, Compose host |
| `FlashlightViewModel.kt` | All business logic, CameraManager, coroutines |
| `FlashlightScreen.kt` | Full Compose UI (screen + cards + sliders) |
| `FlashTheme.kt` | Color tokens and MaterialTheme setup |
| `build.gradle.kts` (snippet) | Dependency block |

---

## Quick Reference — Key APIs

| Feature | API | Min API |
|---------|-----|---------|
| Toggle torch | `CameraManager.setTorchMode()` | 23 |
| Multi-level brightness | `CameraManager.turnOnTorchWithStrengthLevel()` | 33 |
| Query max brightness | `CameraCharacteristics.FLASH_INFO_STRENGTH_MAX_LEVEL` | 33 |
| Sync torch state | `CameraManager.TorchCallback` | 23 |
| SOS timing | `kotlinx.coroutines.delay()` | — |
