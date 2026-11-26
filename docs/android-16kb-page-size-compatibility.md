# Android 16 KB Page Size Compatibility

## Finding

The device warning is about native ELF libraries in the installed APK, not an app startup crash. Android can still launch the app in its 4 KB compatibility mode, but native libraries may lose compatibility or performance on 16 KB page-size devices.

The pre-fix release merge report at `app/build/intermediates/merged_native_libs_blame/release/mergeReleaseNativeLibs/native-libs-blame-release-report.txt` attributed the affected files to:

| APK library | Gradle component | Version |
| --- | --- | --- |
| `libdatastore_shared_counter.so` | `androidx.datastore:datastore-core-android` | 1.2.0 |
| `libsurface_util_jni.so` | `androidx.camera:camera-core` | 1.6.2 |
| `libimage_processing_util_jni.so` | `androidx.camera:camera-core` | 1.6.2 |
| `libbarhopper_v3.so` | `com.google.mlkit:barcode-scanning` | 17.3.0 |
| `libandroidx.graphics.path.so` | `androidx.graphics:graphics-path` | 1.0.1 |

These are prebuilt third-party native libraries. The app does not compile its own C/C++ library. `app/build.gradle.kts` has no `ndkVersion` or native linker configuration, so changing the app's NDK settings alone cannot relink these binaries.

The inspected pre-fix local release APK (`app/build/outputs/apk/release/app-release.apk`) passed `zipalign -c -P 16 -v 4`. Its arm64 copies of all five libraries had 16 KB `PT_LOAD` alignment (`p_align = 0x4000`). The DataStore 1.2.0 library's `PT_GNU_RELRO` started at `0xc000` with a `p_memsz` of `0x1000` (`(0xc000 + 0x1000) % 0x4000 = 0x1000`), matching the RELRO-specific failure described in the [official 16 KB guide](https://developer.android.com/guide/practices/page-sizes). The device's “Unknown error” entries still need to be rechecked against the exact APK that produced the dialog: local ZIP alignment and `PT_LOAD` results do not prove every ELF segment in that installed package passes Android's checker.

Therefore the root cause is incompatible or incompletely 16 KB-compatible upstream ELF metadata in libraries bundled by the installed app. APK ZIP alignment is already correct in the inspected artifact. The screenshot may refer to an older/different install, so compare its version/build with this local release before treating the local APK inspection as the exact reproduction.

## Fix applied

1. **DataStore 1.2.0 → 1.2.1** (`gradle/libs.versions.toml`: `datastore = "1.2.1"`). The 1.2.1 AAR's arm64 `libdatastore_shared_counter.so` keeps `PT_LOAD p_align = 0x4000` and fixes RELRO: `VirtAddr 0x9440 + MemSiz 0x2bc0 = 0xc000`, `(0xc000) % 0x4000 == 0`. DataStore stays in release because `LogSettings`/`AircraftApplication` use it at runtime.
2. **graphics-path forced to 1.1.0** (new `graphics-path` entry in `gradle/libs.versions.toml` plus `implementation(libs.graphics.path)` in `app/build.gradle.kts`, overriding the 1.0.1 copy pulled transitively by Compose UI). Latest stable; all `PT_LOAD` segments are `0x4000`. See the residual note below for its RELRO status.
3. **Debug-only native payload stripped from the release variant only** (`androidComponents { onVariants(selector().withBuildType("release")) }` in `app/build.gradle.kts` excludes `**/libbarhopper_v3.so`, `**/libsurface_util_jni.so`, `**/libimage_processing_util_jni.so`). `CameraScanActivity` is a developer tool: release `DebugTools.isEnabled = false` makes it `finish()` before any CameraX/ML Kit call, so the release APK never needs these binaries. Debug keeps all five libraries and the scanner stays functional there.
   - `debugImplementation` was rejected: `CameraScanActivity` (main source set, referenced by `DevelopSettingsActivity`) imports CameraX/ML Kit classes, so release compilation requires those dependencies present.
   - A `buildTypes.release { packaging { … } }` block was tried first and reverted: on AGP 9.4.1 it also stripped the libraries from the debug APK (verified via `unzip -l app-debug.apk`). The `androidComponents` variant selector is correctly scoped (verified the same way).

## Verification (rebuilt 1.3.8 release)

- Release merge blame report now lists only `datastore-core-android:1.2.1` and `graphics-path:1.1.0`; the three CameraX/ML Kit `.so` files are gone. Debug APK still ships all five.
- `zipalign -c -P 16 -v 4 app-release.apk`: `Verification successful`.
- `llvm-readelf -Wl` on release `arm64-v8a` and `x86_64` copies: every `LOAD` segment has `Align = 0x4000`; DataStore RELRO remainder is `0x0`. This is what Play's ELF gate (`check_elf_alignment.sh`, LOAD-based) checks.
- `./gradlew :app:testDebugUnitTest`: `BUILD SUCCESSFUL`.

## Residual risk

- `libandroidx.graphics.path.so` 1.1.0 (latest stable, May 2026; identical in 1.0.1) still has a RELRO remainder of `0x2000` (`0x5b40 + 0x4c0 = 0x6000`) on both 64-bit ABIs. Per the official guide this can segfault at runtime on a 16 KB device *if the library is loaded there*. It is intentionally kept: `PathIteratorImpl` loads it lazily on the pre-API-34 path that Compose can reach on this app's supported API 31–33 range, so excluding it risks `UnsatisfiedLinkError`. `LOAD` alignment passes, so Play does not flag it; the fix must come from upstream `androidx.graphics`.
- CameraX has no newer stable than the flagged 1.6.2 (its `libsurface_util_jni.so` RELRO remainder is `0x1000`), and bundled ML Kit `barcode-scanning` has no newer release than the flagged 17.3.0 — both are debug-only here, hence excluded from release rather than upgraded.
- The exact APK from the device was not available for direct comparison. Re-run the ELF checks on that artifact if its version differs from this release, and install the rebuilt release on a 16 KB emulator/device (plus a standard 4 KB device) to confirm the dialog is gone and the QR scanner still works in debug.

## Follow-up: OpenCV in the debug APK

The original debug APK reported a separate pair of unaligned native libraries:

| APK library | Gradle component | Version |
| --- | --- | --- |
| `lib/arm64-v8a/libc++_shared.so` | `org.opencv:opencv` | 4.9.0 |
| `lib/arm64-v8a/libopencv_java4.so` | `org.opencv:opencv` | 4.9.0 |

The original debug merge blame report attributed both files to the OpenCV AAR. Direct inspection confirmed every `LOAD` segment in these arm64 libraries had `p_align = 0x1000`, below the required `0x4000`. The app does not build OpenCV from source, so changing only the app's NDK version or linker flags cannot repair those prebuilt files.

## Fix applied: OpenCV 4.9.0 → 4.14.0

`org.opencv:opencv` is now 4.14.0 (latest stable 4.x) in `gradle/libs.versions.toml`. Its AAR ships 16 KB-aligned 64-bit ELF images, so no source build or NDK change was needed:

| Library | `LOAD` align (4.9.0) | `LOAD` align (4.14.0) |
| --- | --- | --- |
| `arm64-v8a/libopencv_java4.so` | `0x1000` | `0x4000` |
| `arm64-v8a/libc++_shared.so` | `0x1000` | `0x4000` |
| `x86_64/libc++_shared.so` | `0x1000` | `0x4000` |

Verified on the rebuilt artifacts:

- `llvm-readelf -Wl` over every `lib/arm64-v8a` and `lib/x86_64` `.so` inside the debug APK: all `LOAD` segments are `0x4000`. The remaining 4 KB-aligned libraries in the APK sit only under `lib/armeabi-v7a` and `lib/x86` (`libbarhopper_v3.so`, `libc++_shared.so`, `libmlkit_google_ocr_pipeline.so`); 32-bit ABIs are outside the Play 16 KB requirement.
- `./gradlew :developtools:compileDebugKotlin :app:assembleDebug :developtools:testDebugUnitTest` succeeds with the new AAR; the Java API used by `ImagePreprocessor` is unchanged.
- Debug APK grows from ~160.4 MB to ~166.0 MB (arm64 `libopencv_java4.so` 20.2 MB → 24.7 MB, `libc++_shared.so` 1.03 MB → 1.29 MB). The `:app` ABI excludes stay as they were.

## Remaining verification

1. **Validate the release artifact the same way.** The release variant packages the same AAR, so rebuild `assembleRelease` and re-run `llvm-readelf -lW` over its `arm64-v8a` libraries, then confirm `zipalign -c -P 16 -v 4` still passes. Re-run `:app:dependencyInsight` for `org.opencv:opencv` after any dependency change so the version inside the APK stays visible.
2. **Exercise both page-size modes.** Install the debug build on a 16 KB emulator/device and run the bank-card scan far enough to confirm `OpenCVLoader` succeeds and preprocessing works; also run the scanner with the Kotlin fallback unavailable path and smoke-test on a 4 KB device.
3. **Keep the ABI-size policy and the fallback option.** OpenCV stays arm64-only. `ImagePreprocessor` keeps its pure-Kotlin filters as the escape hatch if a future OpenCV release regresses ELF alignment or would force a risky migration.

`PT_GNU_RELRO` is still not 16 KB-aligned in OpenCV 4.14.0 on 64-bit ABIs (`arm64-v8a/libopencv_java4.so`: `0x16cec80 + 0x73380 = 0x1742000`, remainder `0x2000`; `arm64-v8a/libc++_shared.so`: remainder `0x3000`). Play's gate only inspects `LOAD` alignment, so submission passes, but this is the same upstream class of risk as `libandroidx.graphics.path.so` and the reason step 2 must run on a 16 KB device.

## Current update: OpenCV 5.1.0 selected

`gradle/libs.versions.toml` now selects `org.opencv:opencv:5.1.0`. Direct inspection of the cached 5.1.0 AAR shows that all arm64 `LOAD` segments in its OpenCV JNI library and `libc++_shared.so` are aligned at `0x4000`. The AAR names the JNI library `libopencv_java5.so`; the existing debug APK and merge blame report still show OpenCV 4.14.0, so the new dependency has not yet been verified in a rebuilt APK.

Before considering the fix complete:

1. Rebuild the debug APK and confirm the merge blame report resolves OpenCV 5.1.0 and packages `libopencv_java5.so` plus its matching `libc++_shared.so`.
2. Inspect every `arm64-v8a` and `x86_64` library in the rebuilt APK for 16 KB `LOAD` alignment, and separately check APK ZIP alignment.
3. Confirm `OpenCVLoader.initLocal()` still loads the renamed native library and run the bank-card preprocessing path. Also build and inspect the release artifact, then exercise the scanner on a 16 KB device.

Android's 16 KB guide says NDK r28 and newer produce 16 KB ELF alignment by default, but that does not change already compiled SDK binaries; those dependencies must be replaced or rebuilt. Sources: [Android 16 KB page-size guide](https://developer.android.com/guide/practices/page-sizes), [OpenCV releases](https://github.com/opencv/opencv/releases).
