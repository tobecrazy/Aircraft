# Device Information Screen Enhancement Plan

## Goal

Make the device information screen more reliable, easier to read, and accurate about what Android can expose, while keeping it a local, read-only developer tool.

## Current implementation

- `DeviceInfoActivity` renders a Compose screen with device/app identity, CPU, memory, storage, battery, network, clock, uptime, resolution, and boot time.
- `DeviceInfoViewModel` builds the UI state and reads `/proc` and `/sys` data, Android system services, and `TrafficStats`.
- The Activity triggers a refresh every second from a main-thread `Handler`. Dynamic refresh includes filesystem reads for CPU statistics, per-core frequencies, and thermal zones.
- The screen already adapts its resource cards to narrow and wide windows and uses `WindowInfoTracker` for fold state.
- `DeviceInfoActivityTest` covers screen sections, back navigation, fold/unfold layout, uptime formatting, and byte-rate formatting.
- Device information strings are maintained in four locales under `developtools/src/main/res/values*`.

## Proposed work

### Phase 1: Make sampling lifecycle-safe and non-blocking

1. Move periodic sampling off the main thread, using the existing ViewModel and coroutine patterns in `:developtools`.
2. Start sampling while the screen is visible and stop it when the Activity leaves the started state. Keep the battery receiver lifecycle aligned with that state.
3. Keep static device details initialized once; refresh only dynamic resource and network values.
4. Avoid overlapping refreshes and retain the last valid value when a source temporarily fails.

**Acceptance criteria**

- No `/proc`, `/sys`, thermal-zone, or other potentially blocking sampling runs on the main thread.
- Sampling stops while the screen is not visible and resumes without stale rate calculations.
- A failed or unavailable system source does not crash the screen or replace a valid reading with a fabricated zero.

### Phase 2: Clarify metric accuracy and unavailable data

1. Represent unavailable readings distinctly from valid zero readings where the UI currently cannot tell them apart.
2. Label the CPU frequency-based fallback as an estimate, or omit its percentage when CPU usage cannot be measured. Do not present frequency divided by maximum frequency as measured CPU utilization.
3. Keep CPU temperature and per-core frequency optional because access differs by device and Android build.
4. Review memory and storage labels against their calculations, including the distinction between device-wide memory and app-process memory.

**Acceptance criteria**

- Every percentage shown as a usage value has a documented calculation that matches its label.
- Unsupported metrics render as unavailable and remain readable without breaking adjacent cards.
- No new permission is requested for metrics that can be omitted or reported as unavailable.

### Phase 3: Improve readability and adaptive presentation

1. Preserve the existing phone, wide-window, and foldable behavior; adjust card layout only where content is cramped.
2. Ensure long device names, CPU identifiers, network details, and localized text wrap or truncate without clipping.
3. Do not communicate resource severity through color alone; pair state colors with the existing numeric values and clear labels.
4. Check font scaling and touch target sizes for screen controls while retaining the compact monitoring layout.

**Acceptance criteria**

- Core values remain legible at larger font scales and on narrow windows.
- Wide windows use available space without stretching each metric into an unreadable row.
- Existing back navigation and scrolling remain accessible.

### Phase 4: Verify behavior and localization

1. Add focused ViewModel tests for source-unavailable behavior, CPU fallback labeling/calculation, and refresh state transitions.
2. Extend Compose tests for unavailable values, long localized content, and narrow/wide layouts.
3. Keep any new user-visible strings synchronized across `values/`, `values-zh/`, `values-zh-rTW/`, and `values-zh-rHK/`.
4. Run the `:developtools` unit tests and relevant lint/build tasks after implementation.

## Files expected to change during implementation

- `developtools/src/main/java/com/young/developtools/gui/DeviceInfoActivity.kt` — lifecycle collection and presentation only.
- `developtools/src/main/java/com/young/developtools/viewmodel/DeviceInfoViewModel.kt` and `DeviceInfoUiState.kt` — sampling, availability, and metric semantics.
- `developtools/src/test/java/com/young/developtools/gui/DeviceInfoActivityTest.kt` plus focused ViewModel tests — regression coverage.
- `developtools/src/main/res/values*/strings.xml` — only if labels or unavailable states change.

No new dependency, network request, persistence, or permission is planned. Device readings remain local to the screen.

## Out of scope

- Exporting or sharing a device report.
- Background monitoring or historical graphs.
- Collecting identifiers or transmitting device information.
- Adding metrics that require sensitive permissions without a separate product requirement.
