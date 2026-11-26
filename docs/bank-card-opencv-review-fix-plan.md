# Bank Card OpenCV Preprocessing Review and Fix Plan

## Goal

Make gallery recognition reliable for the supplied sideways blue UnionPay card with reflective embossed digits. Keep recognition on-device, keep the current Luhn and card-length checks, and do not store or upload OCR text. The supplied photo and its full card number must not be added to the repository, tests, logs, or documentation.

## Current implementation

The scanner already uses OpenCV 4.9.0 through `:developtools`, with a Kotlin fallback when the native library is unavailable. The gallery picker:

1. Decodes the selected image with a 2048 px long-side cap and applies EXIF rotation.
2. Runs an initial ML Kit pass on the full image.
3. If the result is not confirmed, sweeps 24 image rotations. At cardinal rotations it also tries the centered 42% number band, a binary version of that band, a centered 30% number line enlarged to the cap, an illumination-flattened version, and a Chinese recognizer fallback.
4. Requires valid card candidates to be corroborated across rotations or independent passes; if needed, it votes over OCR digit runs and applies the existing parser/Luhn gate.

`ImagePreprocessor` already contains custom grayscale/CLAHE enhancement, the high-pass illumination flattening, and binary stroke extraction. `OpenCvSupportTest` verifies the native library and synthetic stroke contrast on device. That test does not run ML Kit against realistic embossed glyphs.

## Review findings

### 1. No end-to-end failure evidence

The current error state only says that no candidate passed parsing/confirmation. It cannot distinguish: no text detected, digits split into fragments, near-correct digits rejected by Luhn, valid candidate rejected by confirmation, or the number line being cropped out. Without this separation, another filter adjustment would be guesswork.

### 2. Every crop assumes the card and number line are centered in the photo

The 42% and 30% crops use the center of the entire decoded photo, not the card bounds. This works only when the card fills and is centered in the frame. Background margin, off-center placement, perspective, or a partially cropped card can move the number outside either crop. The live-camera path also begins from a fixed centered crop.

### 3. No card-plane or perspective normalization

The pipeline rotates the full image but does not locate the four card corners, rectify perspective, or normalize the card to a consistent landscape coordinate system. A card photographed at an angle therefore reaches the OCR variants with distorted glyph widths and inconsistent number-line position.

### 4. The current synthetic fixture is too simple

The existing regression fixture uses a few straight, constant-color strokes on a flat background and a rectangular bright patch. It does not model embossed digit shapes, reflective highlights, directional shadows, textured printing, camera blur, JPEG artifacts, perspective, EXIF rotation, or real photo framing. Passing it proves pixel contrast separation, not that ML Kit can read card digits.

### 5. The enhancement gate is not a reliable quality decision

`needsBankCardEnhancement` measures gold and neutral-highlight area over the whole image. A small chip or narrow reflective digits can fall below its thresholds. Gallery fallback still runs when parsing fails, so this gate is not the only path, but it determines whether the full-image enhanced pass is attempted. It must not be treated as card or digit detection.

### 6. High-pass/binary filters can amplify the card artwork

Illumination flattening raises local deviations from the background. Embossed edges may improve, but map boundaries, holograms, grain, and compression noise are also emphasized. The current tests assert that selected pixels have contrast; they do not measure whether digit strokes remain connected or whether competing texture overwhelms recognition.

### 7. Rotation search is expensive and confirmation can obscure the failing stage

The gallery path can submit multiple ML Kit requests at each of 24 rotations. A correct candidate must meet the confirmation rules, while an invalid OCR run is collected for later voting. The final failure does not expose which rotation/pass had useful text. Do not broaden voting or relax Luhn as a workaround; first identify whether text geometry or candidate confirmation is the actual failure.

## Fix plan

## Recommended OpenCV pipeline

Use OpenCV where it has a measurable advantage: finding the card plane, correcting perspective, estimating image quality, normalizing uneven illumination, and proposing a small number of useful number-line regions. Do not run every available filter over the whole photo. A filter cannot recreate digit detail lost to defocus, motion blur, clipped highlights, or insufficient source resolution.

### Stage A — Preserve and assess the source

1. Decode at the best safe source resolution already supported by the picker, apply EXIF orientation exactly once, and retain the unmodified bitmap as the fallback source. Keep the 2048 px long-side memory bound.
2. Compute inexpensive image-quality measurements on a downscaled copy: clipped-highlight fraction, card-region sharpness, and card edge contrast. Measure sharpness inside the card candidate, not across the tabletop. Treat these as routing/diagnostic signals, not as a reason to silently reject a scan.
3. Do not upscale by default. Perspective warp should preserve the detected card's effective source sampling. If the number glyph height is below the calibrated OCR range and the source is sharp, allow one bounded interpolation upscale; record the scale factor. Upscaling a blurred or tiny image must not be described as recovered detail.

### Stage B — Find and rectify the card

1. Run card detection on a reduced-resolution copy for speed, then map the selected corners back to the original-resolution bitmap. Use a combination of Canny edges, morphological closing, contour extraction, and `approxPolyDP` quadrilateral candidates.
2. Score candidates by convexity, area, edge support, rectangularity, and plausible aspect ratio near the ISO ID-1 card ratio (85.60 × 53.98 mm, about 1.586). Allow tolerance for perspective and partial occlusion. Use blue in HSV as a supporting cue for this sample only; never require a blue card for the general scanner.
3. Refine corners against the original-resolution edges. Reject crossed, near-collinear, implausibly small, or out-of-bounds quadrilaterals. If confidence is low, use the original image path instead of warping a bad candidate.
4. Order corners consistently and apply `getPerspectiveTransform` + `warpPerspective` to create a landscape card plane. Choose output dimensions from the measured source edge lengths and cap them at the existing limit. Preserve an orientation hypothesis for both 0° and 180° because corner ordering alone cannot determine which end is upright.
5. Make all subsequent ROIs relative to the rectified card. This removes the current assumption that the card and its number line are centered in the photograph and reduces the need for the 24-angle sweep.

### Stage C — Locate a number-line ROI

1. Run one OCR pass on the full rectified card. If ML Kit returns line boxes, rank lines by digit-like content, horizontal extent, and line geometry, then crop the strongest candidate with margins proportional to its height.
2. If OCR returns no useful line, use OpenCV to propose a few horizontal bands within the card: combine grayscale gradient/morphological response with connected-component alignment and a normalized vertical prior. For an initial UnionPay-card validation, search a bounded central-to-lower card region, but keep the bounds configurable from measured cards rather than assuming a universal issuer layout.
3. Keep the best few bands, not every row. Expand each band enough to retain glyph tops/bottoms and spacing; avoid cutting digits at the crop boundary. Report the normalized rectangle in the debug trace.
4. Validate ROI coverage with overlay visualizations during development. Do not commit the supplied card image; generated, non-sensitive test images can assert that varied card offsets and perspectives still place the full number row inside the crop.

### Stage D — Generate complementary, controlled image variants

For each selected number-line ROI, retain the original color crop and generate only a small, ordered set of candidates:

1. **Raw rectified crop:** preserve the camera signal and let ML Kit handle it first.
2. **Local contrast crop:** use OpenCV `cvtColor` from the bitmap's RGBA representation to Lab and `createCLAHE` on the L channel only, then merge the original a/b chroma channels back if the OCR input remains color. The current CLAHE implementation is custom Kotlin, not OpenCV; retain it as the no-native fallback and compare native output/latency before removing it. Start calibration near `clipLimit=2` and an 8×8 tile grid, but treat these as initial values; tile size must be large enough relative to the digit height to avoid amplifying every emboss mark and printed-map edge.
3. **Illumination-normalized crop:** work in `CV_32F`; estimate a slowly varying background with a Gaussian/box filter whose scale is several times the measured stroke width, subtract or divide by that background, and robustly map a chosen percentile range back to 8-bit. Keep both positive and negative relief responses. Avoid doing subtraction in `CV_8U`, where saturation clips negative residuals before later processing.
4. **Relief-detail candidate:** calculate a modest morphological gradient or paired top-hat/black-hat response on the normalized luminance. This targets raised bright and shadowed dark edges. Use it as a separate OCR candidate; do not merge it blindly into the raw image because it also highlights map boundaries and card texture.
5. **Binary candidate, last:** apply adaptive thresholding only to the best contrast/relief crop. Set the neighborhood relative to measured glyph height and illumination variation, and test both expected polarities if needed. Binary output is a fallback because it can join adjacent embossed strokes or erase thin glyphs.

The current `Core.addWeighted` 8-bit flattening and fixed `ILLUMINATION_WINDOW_DIVISOR`, `ADAPTIVE_RADIUS`, and `EDGE_THRESHOLD` should be benchmarked against the float-domain variants above. Keep the existing Kotlin fallback and resource ownership rules; OpenCV availability must not become a scan prerequisite.

### Stage E — Order OCR passes and combine evidence safely

1. Run raw full-card OCR, then raw number-ROI OCR, then CLAHE ROI, then illumination/relief ROI. Run binary OCR only if those produce no accepted candidate. This keeps matte-card latency low and avoids making the noisiest variant authoritative.
2. If the card plane is confidently rectified, do not repeat the same work at 24 arbitrary angles. Try the two 180° orientation hypotheses; reserve the current broad rotation sweep for the no-card-detection fallback only.
3. Parse each pass independently with the existing scheme-length and Luhn rules. Where ML Kit exposes element/line confidence, use it only as supporting evidence, never to override a failed checksum. Let independent preprocessing passes corroborate the same validated candidate; never count repeated processing of an identical bitmap as independent evidence.
4. Keep the current strict candidate voting protections if voting is needed: same-length near-match cluster, strict positional majority, and final Luhn validation. Do not glue digits from unrelated text lines or weaken the checksum to compensate for image quality.
5. Add per-pass timing and redacted quality metrics. Keep OCR text, PAN, expiry, image bytes, and OCR-text hashes out of logs, Crashlytics, persistence, and network requests.

### Stage F — Calibrate with evidence, not visual intuition

1. Build a small validation matrix with non-sensitive generated card-like images plus manually captured cards whose data is not committed: matte/embossed, blue/light/dark artwork, glare/no glare, front-on/mild perspective, centered/off-center, native/rotated EXIF, and sharp/soft focus.
2. For image-only tests, measure detection/rectification success, quadrilateral corner error on generated ground-truth geometry, ROI coverage of the entire number row, local contrast-to-noise ratio, and false edge/component density outside glyph strokes. Pixel contrast at one hand-picked point is not enough.
3. On device, measure exact-card recognition rate, false accepted candidates, pass count, median and tail latency, and failure category. Use separate samples for parameter tuning and final evaluation; do not tune and report on the same photos.
4. Tune one parameter family at a time: geometry thresholds, illumination scale, CLAHE settings, then binary thresholding. Keep parameters tied to measured glyph/ROI dimensions where possible instead of fixed whole-image pixel constants.
5. The release criterion is a measured improvement over the current raw-first baseline without increasing false acceptance or materially slowing matte-card scans. No image-processing stack can guarantee recovery from irretrievably blurred, clipped, occluded, or undersampled digits; in those cases the safe result is a rescan hint.

### Phase 1 — Reproduce and identify the failing stage

1. Add a temporary, debug-only, in-memory trace around the existing gallery passes. Record pass name, rotation, source/crop dimensions, normalized crop rectangle, OCR block/line count, digit-run lengths, parsed-candidate count, and whether a candidate passed Luhn and confirmation.
2. Never record OCR strings, card-number digits, expiry text, bitmap contents, or stable hashes of OCR text. Do not send traces to Crashlytics or any network service. Clear the trace when the scan ends or the user resets the screen.
3. Run the supplied photo through the unmodified pipeline and save only this redacted stage summary for analysis. Confirm the expected result manually from the card; do not place the PAN in the trace or artifact.
4. Use the trace to classify the failure before proceeding:
   - no useful OCR line/digit runs: localization, scale, focus, or preprocessing problem;
   - near-complete digit runs but no parsed candidate: glyph recognition/parser boundary;
   - a valid parsed candidate that never locks: confirmation/voting problem;
   - useful digits absent from the recorded crop: crop geometry problem.

### Phase 2 — Localize and rectify the card before number-line processing

1. Add a card-ROI path to the existing `ImagePreprocessor`; do not add another image-processing dependency. Use OpenCV contour/edge and color contrast candidates to estimate the card quadrilateral. The blue card against a light table is a suitable first target, but candidate selection must use rectangular shape, plausible card aspect ratio, and image bounds rather than a fixed blue threshold alone.
2. Order the four corners, reject degenerate or implausible quadrilaterals, and perspective-warp the best candidate into a landscape card image with a stable aspect ratio. Preserve the original image as a fallback when no reliable rectangle is found.
3. Keep orientation handling deterministic: apply EXIF orientation once, normalize the detected card to landscape, and avoid reapplying EXIF or double-rotating pixels in the ViewModel. Keep arbitrary rotation search only as a fallback for uncertain corner/orientation detection.
4. Derive all later crops from the rectified card dimensions. Define normalized card coordinates and search a conservative vertical range for the number row rather than cropping a fixed band from the whole photo.
5. For the first implementation, retain a bounded multi-band fallback around the expected number-row region. Use OCR line boxes from a successful full-card pass to refine the crop; if full-card OCR finds no line, evaluate a small set of nearby normalized rows. Avoid a broad combinatorial sweep over the whole photo.
6. Return crop geometry with each bitmap (or keep it in a small internal result) so the debug trace can show which region each pass examined. Keep the helper private/internal to this scanner; no public API is needed.

### Phase 3 — Select preprocessing by observed image failure

1. Keep raw rectified-card OCR as the first pass. Run enhanced passes only when the raw result has no accepted candidate, preserving latency for matte cards.
2. Compare a small, fixed set of variants on the localized number ROI:
   - grayscale/local contrast (reuse the existing enhancement implementation where useful);
   - illumination flattening at the current bounded scale;
   - stroke thresholding only when the flattened ROI has usable signal.
3. Measure both useful digit-stroke continuity and background false edges on real and synthetic fixtures. Tune blur window and thresholds from those measurements; do not tune solely to make a few sampled pixels pass.
4. Avoid repeated OpenCV initialization or Mat/Bitmap conversion within one scan where a single rectified/cropped representation can be reused. Release every Mat and recycle every owned Bitmap after its awaited ML Kit task completes. Keep the existing 2048 px long-side cap and native-load fallback.
5. Keep the Chinese recognizer as an optional final fallback only if Phase 1 evidence shows the Latin model is the limiting stage. Reuse the existing recognizer; do not add a dependency preemptively.

### Phase 4 — Keep candidate validation strict and make confirmation explainable

1. Preserve the current card-length, scheme, OCR-confusion, and Luhn validation. Do not accept a partial number, concatenate unrelated text blocks, or weaken Luhn to make the sample appear successful.
2. Use per-pass metadata to determine whether valid candidates are being rejected only because they appear in one rotation. If so, adjust confirmation narrowly: independent preprocessing passes on the same rectified orientation may corroborate each other, but duplicate readings of the same bitmap/pass must not count twice.
3. Keep voting fenced to same-length, near-matching digit runs with a strict per-position majority and a final Luhn check. Add a test for any change to confirmation/voting before enabling it.
4. Clear trace buffers and temporary candidate data at completion/reset. Keep the existing no-persistence/no-upload rule for recognized card data.

### Phase 5 — Regression coverage and physical-device validation

1. Extend `ImagePreprocessorTest` with generated card images that vary card offset, scale, rotation, perspective, textured print, highlight, and background. Assert corner/ROI bounds, sensible fallback when no card is found, normalized output bounds, and preservation of the digit-row region.
2. Keep test PANs synthetic and non-sensitive. Prefer images with digit-like glyph shapes and gradients over isolated straight bars; assert geometric crop coverage and contrast/connected-component properties rather than a specific OCR result in a JVM test.
3. Add ViewModel/pass-order tests for the minimal fallback sequence and for candidate confirmation only if production logic is extracted into testable pure logic.
4. Run the existing unit and connected OpenCV tests. Then manually test on an arm64 device with the supplied card and at least one matte card. Check the supplied card in the original orientation, after a 90-degree image rotation, off-center framing, and mild perspective. Verify the result against the card without recording the PAN.
5. Record only redacted outcomes: pass that produced the accepted candidate, candidate length/scheme, total latency, and whether card rectification succeeded. Test that matte-card latency remains close to the current raw-pass path and that OpenCV-unavailable devices still use the Kotlin fallback.

## Files likely to change during implementation

- `developtools/src/main/java/com/young/developtools/utils/ImagePreprocessor.kt` — card candidate detection, corner validation, perspective correction, normalized ROI, and bounded variants.
- `developtools/src/main/java/com/young/developtools/viewmodel/CardScanViewModel.kt` — gallery pass order, rotation fallback, debug-only redacted pass trace, and any evidence-backed confirmation adjustment.
- `developtools/src/main/java/com/young/developtools/gui/CardScanActivity.kt` — only if the live camera path needs to provide a full frame/card ROI instead of the current centered crop, or to expose a temporary debug trace during manual validation.
- `developtools/src/test/java/com/young/developtools/utils/ImagePreprocessorTest.kt` — synthetic geometry and preprocessing regression cases.
- `developtools/src/test/java/com/young/developtools/viewmodel/CardScanViewModelTest.kt` — pass and confirmation behavior, only where logic changes.
- `developtools/src/androidTest/java/com/young/developtools/utils/OpenCvSupportTest.kt` — retain native-load/fallback coverage; add geometric checks if the rectification uses native-only behavior.

No new OpenCV dependency, parser rewrite, manifest permission, persistence layer, or network behavior is indicated by this review. Update `README.md` only if the annotated source tree changes; update `CLAUDE.md` and `AGENTS.md` only if a lasting project fact changes.

## Acceptance criteria

- The supplied reflective card is recognized correctly from the selected photo without relaxing card validation.
- Redacted trace data identifies the successful preprocessing pass and confirms the number ROI covered the embossed line.
- Off-center and mildly perspective-distorted photos either rectify and scan, or safely fall back to the existing full-image route.
- Matte cards retain the fast raw-first path; fallback latency is measured and acceptable on the target device.
- No card number or OCR text is committed, persisted, logged, uploaded, or included in crash reports.
- Bitmap/Mat ownership, 2048 px bounds, and OpenCV-unavailable fallback remain correct.

## Out of scope

Do not change card-number parsing rules, add cloud OCR, upload images, add a new OCR SDK, store scan history, redesign the scanner UI, or use the supplied card as a checked-in fixture. These do not address the demonstrated uncertainty in image localization and preprocessing.
