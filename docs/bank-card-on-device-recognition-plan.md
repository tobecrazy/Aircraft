# On-device bank card number recognition plan

## Scope

Extend the existing debug-only live scan tool instead of adding a user-facing entry point:

- Entry point: `developtools/src/main/java/com/young/developtools/gui/CameraScanActivity.kt`.
- Current pipeline: CameraX `LifecycleCameraController` plus `MlKitAnalyzer`, limited to QR codes.
- Goal: recognize a payment card primary account number (PAN) only. Do not recognize, display, or retain CVV, expiry date, or cardholder name.
- Keep the `DeveloperMode` gate. This feature must remain unavailable in release builds.

The app minimum SDK is 33, which already satisfies the ML Kit GenAI Prompt API minimum of API 26.

## Design

1. Keep the existing CameraX preview and permission flow. Add a bank-card analysis mode that:
   - Uses on-device ML Kit Text Recognition to obtain text from preview frames.
   - Drops frames while OCR or generation is in progress, preventing an unbounded analysis queue and duplicate inferences.
   - Locks the preview after a validated result, reusing the existing first-result-wins and rescan behavior.

2. Send OCR text, not the camera image, to the ML Kit GenAI Prompt API:
   - Check the model `FeatureStatus` before inference. If it is `AVAILABLE` and structured output is supported, use the model. If the model is downloadable, offer the download. Otherwise fall back to deterministic local OCR extraction and never use a cloud fallback.
   - Check `isStructuredOutputFeatureAvailable()` before requesting typed output. Devices without it use the local OCR fallback rather than parsing free-form model text.
   - Define a narrow `@Generable` result such as `CardNumberExtraction`, containing only a candidate PAN and a found/not-found outcome.
   - Use a short prompt: extract only a 12- to 19-digit card number, allowing spaces or hyphens in the source text; never infer, complete, or return any other card field.
   - Do not use prefix caching because the prompt is short.
   - Close the GenAI client when its ViewModel is cleared.

3. Treat the model response as untrusted and apply local deterministic validation:
   - Normalize both OCR candidates and the model response to digits.
   - Accept only 12- to 19-digit values.
   - Require the model's candidate to occur in OCR text so the model cannot hallucinate or alter a card number.
   - Apply the Luhn checksum. On failure, report that no valid card number was recognized and let the user rescan.
   - Do not infer issuer or card scheme from BIN ranges.

4. Local fallback when the model cannot run:
   - Extract 12- to 19-digit sequences directly from OCR text when AICore, Gemini Nano, or structured output is unavailable.
   - Reuse the same length, OCR-source, and Luhn checks as the model path.
   - Keep the fallback entirely on device: no cloud call, no free-form model parsing, and no extra data retention.

## Expected changes

| Location | Change |
| --- | --- |
| `gradle/libs.versions.toml` | Add version-catalog aliases for ML Kit GenAI Prompt API and on-device text recognition. |
| `developtools/build.gradle.kts` | Add those dependencies without adding a network dependency. |
| `developtools/.../gui/CameraScanActivity.kt` | Add card mode state and UI while reusing the CameraX preview, permissions, and result lock. |
| `developtools/.../viewmodel/CameraScanViewModel.kt` | Own model availability and scan states, close the GenAI client, and never persist a result. |
| `developtools/src/main/res/values*/strings.xml` | Add matching strings in all four locales for model state, scanning, failure, and privacy guidance. |
| `developtools/src/test/.../viewmodel/` | Add pure tests for number normalization, OCR-source matching, length checks, Luhn checks, and rejecting invented values. |
| `developtools/src/test/.../gui/` | Add Robolectric UI-state coverage without real camera or GenAI hardware. |
| `docs/android-16kb-page-size-compatibility.md` | Update only if the new dependencies add release native payloads or require changed packaging verification. |

## Privacy and security rules

- Never write a preview frame, OCR text, PAN, or generation result to logs, Room, SharedPreferences, request history, or crash reporting.
- Do not capture or persist photos. Process only CameraX analysis frames.
- Display a masked PAN by default. Copy the full PAN only after an explicit user action.
- Mark clipboard content as sensitive and warn about clipboard exposure.
- Clear result references when rescanning, leaving the screen, or failing.
- Do not implement autofill, payment execution, online verification, or issuer detection.

## Verification

1. Unit-test valid numbers, invalid Luhn checks, invalid lengths, separators, local OCR extraction, and hallucinated values absent from OCR text.
2. Robolectric-test unavailable-model, local-only fallback, scanning, masked-success, rescan, and error-recovery UI states.
3. Test on devices with and without the supported on-device model, including first-download flow, denied camera permission, glare, low light, portrait, and landscape.
4. Run `./gradlew :developtools:testDebugUnitTest` and `./gradlew assembleDebug lintDebug`.
5. If the dependency graph changes release packaging, assemble release and repeat the repository's 16 KB APK alignment and native-library checks.

## Open feasibility gate

Confirm on the target devices that Gemini Nano and structured output are available for the ML Kit GenAI Prompt API. The local OCR fallback keeps card mode usable when they are not; the model refines the OCR result but must not replace OCR-source matching or Luhn validation.
