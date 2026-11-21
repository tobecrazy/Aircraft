# Embossed Bank Card OCR Failure — Fix Plan

## Observed failure

The gallery scan of the supplied blue UnionPay card ends with “No Luhn-valid card number found in this text.” The card number is embossed with reflective silver digits and the photo presents the card sideways. The image itself is not checked into the repository; its PAN must not be copied into logs, tests, or documentation.

## Diagnosis

The message confirms that the current pipeline did not produce a candidate accepted by `parseBankCards`; it does not tell us whether OCR returned no text, split or misread the PAN, or returned digits that failed length/Luhn validation. The app exposes no OCR-stage diagnostics, so the exact per-image failure cannot be proven from the screen alone.

The most likely image-specific cause is the enhancement and crop strategy:

1. Full-image enhancement is selected only when gold pixels cover at least 4% of the whole image or neutral highlights cover at least 1%. The sample has a blue card, a small gold chip, and narrow silver embossed digits; these local details may fall below both thresholds.
2. When that gate returns false, the full-image OCR pass uses raw pixels. The number-band fallback does apply grayscale/CLAHE, but crops a broad fixed 42% of the card height. The binary fallback thresholds that same broad raw crop. Neither fallback isolates and enlarges the embossed number line.
3. The broad crop retains much of the textured card background, which may leave the reflective digit strokes too small or low-contrast for OCR.

Rotation is already handled in the gallery path, and the parser already normalizes common OCR letter/digit confusions and validates candidates with Luhn. These are not missing features in the current code.

ML Kit consumes the image supplied by the app; it does not provide this card-specific image enhancement stage. The project already has both Text Recognition v2 recognizers: the Latin recognizer is used for bank cards and the bundled Chinese recognizer is used for ID cards. The Chinese model supports Chinese and Latin text, so it can also be tried as a fallback for cards containing both scripts.

**Confidence:** high that the global enhancement gate can miss this card type; medium that this is the direct cause of this particular scan. Confirm on device before changing recognition thresholds.

## Fix plan

1. **Add OpenCV 4.9.0 to `:developtools`.** Follow the repository's version-catalog rule: declare `org.opencv:opencv:4.9.0` in `gradle/libs.versions.toml` and use its alias from `developtools/build.gradle.kts` (`implementation(libs.opencv)`). Initialize OpenCV once, off the camera frame callback, and handle initialization failure by retaining the current Android bitmap pipeline.
2. **Create bounded OCR variants** in the existing `ImagePreprocessor` path after orientation correction and before `InputImage` creation: grayscale + CLAHE for local contrast, plus an adaptive-threshold variant for reflective embossed strokes. After the current number-band pass fails, crop a tighter number-line region and enlarge it within the 2048 px long-side cap. Keep the raw pass first and run heavier variants only if it yields no parsed card; do not make the whole-image gold/glare heuristic the only route to enhanced OCR.
3. **Try the Chinese recognizer as a final model fallback** on the enhanced number-line variant after the Latin recognizer returns no accepted candidate. Reuse the already-declared `ChineseTextRecognizerOptions` client; add no ML Kit dependency. Keep the existing rotation confirmation, parser, and Luhn validation unchanged. Do not join unrelated OCR fragments into a candidate.
4. **Preserve image ownership and bounds.** Keep the 2048 px long-side cap. Recycle every temporary `Bitmap` and OpenCV `Mat` only after its awaited ML Kit task completes. Never persist or upload recognized text. Do not run OpenCV on every live frame; invoke the heavier fallback only after raw OCR fails.
5. **Add focused regression coverage.** Extend `ImagePreprocessorTest` with a synthetic blue-card bitmap containing a small gold chip and narrow silver embossed strokes; verify the OpenCV variants preserve local contrast and stay within the size limit. Add a pass-selection test only if the sequence is extracted into testable logic. Never commit the supplied image or its PAN.
6. **Verify on device** with the supplied card and a matte card. Confirm the embossed sample yields the correct Luhn-valid result, the matte control still succeeds through the raw pass, both portrait and landscape photos work, fallback latency is acceptable, and recognition remains on-device.

## Files and scope

- `gradle/libs.versions.toml` — OpenCV 4.9.0 version and library alias.
- `developtools/build.gradle.kts` — `implementation(libs.opencv)`.
- `developtools/src/main/java/com/young/developtools/utils/ImagePreprocessor.kt` — bounded OpenCV bitmap/Mat conversion, contrast variants, and tighter number-line crop.
- `developtools/src/main/java/com/young/developtools/viewmodel/CardScanViewModel.kt` — gallery pass ordering, Chinese-recognizer fallback, and temporary image lifecycle.
- `developtools/src/test/java/com/young/developtools/utils/ImagePreprocessorTest.kt` — synthetic embossed-card regression.
- `developtools/src/test/java/com/young/developtools/viewmodel/CardScanViewModelTest.kt` — pass-selection coverage only if that sequence is extracted into testable logic.

No parser/API, UI copy, manifest, or persistence changes are needed. OpenCV adds native binaries and APK size; check its supported ABIs, initialization behavior, and packaged variants. Avoid OCR text logging because full card numbers are sensitive.

## Verification

```bash
./gradlew :developtools:testDebugUnitTest \
  --tests "com.young.developtools.utils.ImagePreprocessorTest" \
  --tests "com.young.developtools.viewmodel.CardScanViewModelTest"
```

Unit tests cover preprocessing and pass selection; only the on-device check validates ML Kit against reflective embossed digits.

## Implementation notes

- OpenCV ships for `arm64-v8a` only. The Maven artifact is 110 MB (four ABIs of
  `libopencv_java4.so`), so `app/build.gradle.kts` excludes the armeabi-v7a, x86
  and x86_64 copies from the APK instead of applying a global `abiFilters` —
  that keeps ML Kit's own natives for every ABI, so emulator runs still work.
  On a device without the library `OpenCvSupport.isAvailable()` returns false
  and the variants fall back to the pure-Kotlin filters, which is also the path
  the Robolectric tests exercise.
- `R8` keeps `org.opencv.**` through `developtools/consumer-rules.pro`: the
  native library resolves those Java entry points by name.
- The live path no longer requires the gold/glare gate to reach the heavier
  passes; "the raw pass produced no card" selects them, gated to cardinal
  rotations. The Chinese recognizer fallback stays gallery-only to keep live
  frame latency bounded.
- **The number-line variant flattens illumination instead of applying CLAHE.**
  On a first on-device attempt the scan still failed on a 19-digit embossed
  UnionPay card. Measured on that card's band, CLAHE moved a 110-level stroke
  to 103 and left the segment printed over the card's white map graphic at 0 —
  with 95% of the band at one flat colour its equalisation table is close to
  the identity. The variant now subtracts a blurred copy and amplifies the
  difference around mid-grey (`ILLUMINATION_GAIN = 2`), and the binary variant
  thresholds `abs(flattened - 128)`, which keeps raised and shadowed strokes
  solid in both polarities.
- **Readings are voted per position, inside one cluster.** A 19-digit number
  fails Luhn on a single substituted glyph, and the passes usually misread
  different positions. `voteCardReadings` first clusters readings by Hamming
  distance (an unrelated run of the same length differs in far more glyphs),
  then requires at least four readings in the largest cluster, a strict
  per-position majority, and the unchanged Luhn/length gate. A bare majority
  would average three different misreads into a number no pass ever saw, so
  that case is covered by `vote never averages unrelated readings into a card`.
  `ENHANCED_FULL` is now its own pass id — it used to share `FULL`, so two
  independently pre-processed images could never corroborate each other.
- **Device check, step 6 (partial).** `OpenCvSupportTest` ran on an arm64-v8a
  device through `:developtools:connectedDebugAndroidTest`: the packaged OpenCV
  build initialises, and both number-line variants separate an embossed stroke
  from a dark card face and from a bright printed graphic within the 2048 px
  cap. Both paths use a box-filter background so OpenCV and the Kotlin fallback
  behave identically. Still outstanding: end-to-end recognition of the
  reflective UnionPay card and a matte control, plus fallback latency — those
  need the physical card and a manual gallery pick.
