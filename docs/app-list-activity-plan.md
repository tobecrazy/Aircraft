# Installed App List Activity Migration Plan

## Goal

Replace the installed-app modal dialog with a full-screen `AppListActivity` that follows the existing shared Activity and Compose theme conventions in `:developtools`.

## Current implementation

- `AppListDialog.kt` contains the app query/filter helpers and the Compose dialog content.
- `AndroidDevAssistantToolsActivity` opens that content through `AlertDialog` and `setDialogComposeContent` for the App Browser module.
- The list queries launchable apps, defaults to user apps, filters by app label or package name, loads icons lazily, launches an app when its row is tapped, and opens Android app details from the Details action.
- The current module manifest declares Activities with `Theme.Aircraft.Common`; Compose screens wrap their content in `AircraftTheme` to apply the persisted accent selection.
- The app manifest owns the `<queries>` declaration used for launcher app visibility. Keep it there; `:developtools` must not duplicate it.
- Existing app-list labels and filters already have translations in all four supported locales.

## Implementation plan

1. Extract the list UI from the dialog shell into an Activity-ready screen while retaining `InstalledApp`, `AppFilter`, `readInstalledApps`, `filterApps`, row icons, and app actions.
2. Add `AppListActivity : BaseAircraftActivity`. Follow the standard `initializeViewModel` / `initializeUI` lifecycle, apply the same `DeveloperMode` gate as the assistant entry point, use `enableEdgeToEdge`, and wrap Compose content in `AircraftTheme`.
3. Register the Activity in `developtools/src/main/AndroidManifest.xml` as non-exported with `@style/Theme.Aircraft.Common` and resize the window for the search keyboard.
4. Change the App Browser action in `AndroidDevAssistantToolsActivity` to launch `AppListActivity`; remove the dialog-specific Compose dismissal and palette dependencies from the app-list screen.
5. Keep search and selected filter state across Activity recreation. Query the installed apps off the main thread, retain loading and unavailable states, and keep icon loading lazy per visible row.
6. Keep existing behavior: tap a row to launch the app, tap Details to open Android's app details page, and use normal system back navigation to return to the assistant tools screen.

## Acceptance criteria

- Opening App Browser navigates to a full-screen Activity; it no longer creates a modal dialog.
- The new Activity uses both `Theme.Aircraft.Common` from the manifest and `AircraftTheme` for Compose content.
- Search, user/system/all filters, empty and load-failure states, app launching, and app details continue to work.
- Search remains usable when the keyboard is shown, and back navigation returns to the assistant screen.
- The app-level package visibility `<queries>` declaration remains the single source for launcher-app queries.
- No new permission, dependency, network access, or persistent app-list data is introduced.

## Files expected to change during implementation

- `developtools/src/main/java/com/young/developtools/gui/AppListDialog.kt` — retain reusable app data/query/filter/row logic and provide screen content without dialog-only behavior.
- `developtools/src/main/java/com/young/developtools/gui/AppListActivity.kt` — new Activity host and full-screen Compose screen.
- `developtools/src/main/java/com/young/developtools/gui/AndroidDevAssistantToolsActivity.kt` — navigate to the Activity instead of showing the dialog.
- `developtools/src/main/AndroidManifest.xml` — register the non-exported Activity with the common Android theme.
- `developtools/src/test/java/com/young/developtools/gui/AndroidDevAssistantToolsActivityTest.kt` and focused app-list screen tests — update navigation expectations and preserve filter/screen behavior coverage.

No string-resource changes are expected unless the new page needs labels beyond those already present in all four locales.
