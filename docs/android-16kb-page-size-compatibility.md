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
3. **~~Debug-only native payload stripped from the release variant only~~ — reverted in v1.4.2** (`ed3562e`). The `androidComponents { onVariants(selector().withBuildType("release")) }` block that excluded `**/libbarhopper_v3.so`, `**/libsurface_util_jni.so` and `**/libimage_processing_util_jni.so` shipped in 1.3.8 but caused `UnsatisfiedLinkError` on devices, so it was removed. `app/build.gradle.kts` has no `androidComponents` packaging block today: release packages every ML Kit/CameraX native library again (verified in the rebuilt APK below), so those libraries must pass the 16 KB check too.

## Verification (rebuilt 1.3.8 release)

- Release merge blame report at the time listed only `datastore-core-android:1.2.1` and `graphics-path:1.1.0`; the three CameraX/ML Kit `.so` files were gone. That described the 1.3.8 build only — after the v1.4.2 revert release ships them again, and DataStore has since moved to 1.3.0.
- `zipalign -c -P 16 -v 4 app-release.apk`: `Verification successful`.
- `llvm-readelf -Wl` on release `arm64-v8a` and `x86_64` copies: every `LOAD` segment has `Align = 0x4000`; DataStore RELRO remainder is `0x0`. This is what Play's ELF gate (`check_elf_alignment.sh`, LOAD-based) checks.
- `./gradlew :app:testDebugUnitTest`: `BUILD SUCCESSFUL`.

## Residual risk

- `libandroidx.graphics.path.so` 1.1.0 (latest stable, May 2026; identical in 1.0.1) still has a RELRO remainder of `0x2000` (`0x5b40 + 0x4c0 = 0x6000`) on both 64-bit ABIs. Per the official guide this can segfault at runtime on a 16 KB device *if the library is loaded there*. It is intentionally kept: `PathIteratorImpl` loads it lazily on the pre-API-34 path that Compose can reach on this app's supported API 31–33 range, so excluding it risks `UnsatisfiedLinkError`. `LOAD` alignment passes, so Play does not flag it; the fix must come from upstream `androidx.graphics`.
- CameraX has no newer stable than the flagged 1.6.2 (its `libsurface_util_jni.so` RELRO remainder is `0x1000`), and bundled ML Kit `barcode-scanning` has no newer release than the flagged 17.3.0. Excluding them from release is no longer an option — v1.4.2 restored them to stop the `UnsatisfiedLinkError` — so their `LOAD` alignment (all `0x4000` in both APKs, checked below) is what keeps Play's gate green.
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

## Verified: OpenCV 5.1.0

`gradle/libs.versions.toml` selects `org.opencv:opencv:5.1.0`. The AAR renames the JNI library to `libopencv_java5.so` and still ships all four ABIs (arm64 32.7 MB, armeabi-v7a 20.9 MB, x86 56.7 MB, x86_64 74.8 MB).

Two code changes were required, and both were missing from the first draft of this section:

1. **`app/build.gradle.kts` still excluded `libopencv_java4.so`.** After the bump no exclude matched, so all four ABIs would have entered the APK. The three entries now name `libopencv_java5.so`; the arm64 copy ships and armeabi-v7a/x86/x86_64 stay excluded.
2. **OpenCV 5 moved two Java bindings from `Imgproc` to `org.opencv.geometry.Geometry`:** `Imgproc.contourArea` → `Geometry.contourArea` and `Imgproc.getPerspectiveTransform` → `Geometry.getPerspectiveTransform`. `ImagePreprocessor.kt` now imports `org.opencv.geometry.Geometry` and calls it at both sites; without this `:developtools:compileDebugKotlin` fails with `UNRESOLVED_REFERENCE`. `OpenCVLoader.initLocal()` needed no change: it loads the `opencv_java5` name baked into the AAR.

Rebuilt artifacts (`:app:assembleDebug`, `:app:assembleRelease`; release also needed `-x lintVitalAnalyzeRelease -x lintVitalRelease` because lint 32.5.3 was not in the offline cache):

| Check | Result |
| --- | --- |
| Merge blame (debug + release) | `lib/arm64-v8a/libopencv_java5.so -> org.opencv:opencv:5.1.0`; `libc++_shared.so` on every ABI also from 5.1.0 |
| APK payload | Debug 167 MB, release 111 MB. Only `lib/arm64-v8a/libopencv_java5.so` is packaged; armeabi-v7a/x86/x86_64 carry no OpenCV JNI library |
| `llvm-readelf -Wl`, every `arm64-v8a`/`x86_64` `.so` in both APKs | all `LOAD` segments `Align = 0x4000` |
| `zipalign -c -P 16 -v 4` | `Verification successful` (debug and release) |
| `:app:dependencyInsight --configuration releaseRuntimeClasspath` | `org.opencv:opencv:5.1.0` |
| `OpenCvSupportTest` (`am instrument`, arm64 emulator, API 36, 4 KB pages) | `OK (1 test)` — `initLocal()` loads the renamed library and both number-line variants match the expected pixels |

`PT_GNU_RELRO` is still not 16 KB-aligned in 5.1.0: `arm64-v8a/libopencv_java5.so` `0x1e4cdd0 + 0xa9230 = 0x1ef6000`, remainder `0x2000`; `arm64-v8a/libc++_shared.so` remainder `0x3000`. Play's gate only inspects `LOAD` alignment, so submission passes, but this is the same upstream class of risk as `libandroidx.graphics.path.so`.

Still open: neither attached device has 16 KB pages (`getconf PAGESIZE` is 4096 on both the physical device and the emulator), so the 16 KB runtime run is pending — install the debug build on a 16 KB emulator, run the bank-card scan, and smoke-test the 4 KB path through the UI (only the OpenCV filter test ran there).

Android's 16 KB guide says NDK r28 and newer produce 16 KB ELF alignment by default, but that does not change already compiled SDK binaries; those dependencies must be replaced or rebuilt. Sources: [Android 16 KB page-size guide](https://developer.android.com/guide/practices/page-sizes), [OpenCV releases](https://github.com/opencv/opencv/releases).
