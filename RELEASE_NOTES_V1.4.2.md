# Aircraft v1.4.2 Release Notes

## 🔥 Critical Fixes

### Fixed ML Kit Native Library Crash
- **Issue**: Users experienced `UnsatisfiedLinkError: library "libbarhopper_v3.so" not found` crashes when using QR code scanning functionality in release builds
- **Root Cause**: Native libraries were excluded from release builds while ML Kit Java/Kotlin code remained, causing runtime crashes
- **Solution**: Removed the `androidComponents` exclusion rules that stripped ML Kit native libraries from release APK
- **Impact**: QR code scanning now works correctly in all builds without crashes

### Added Crash Test Confirmation Dialog
- **Issue**: Test crash button in Developer Settings could be accidentally triggered
- **Solution**: Added a confirmation dialog before triggering intentional crashes for Firebase Crashlytics testing
- **Languages**: Fully localized in English, Simplified Chinese, Traditional Chinese (Taiwan), and Traditional Chinese (Hong Kong)

## 📦 Build Information

- **Version**: 1.4.2 (versionCode 14)
- **APK Size**: ~35 MB (increased from ~16 MB)
- **Minimum SDK**: Android 12 (API 31)
- **Target SDK**: Android 15 (API 37)

## 📝 Detailed Changes

### Fixed
- Critical `UnsatisfiedLinkError` crash when ML Kit tries to load native barcode scanning libraries in release builds
- Test crash button now requires explicit user confirmation before triggering crash

### Added
- Crash confirmation dialog with localized strings:
  - `develop_settings_crash_dialog_title`
  - `develop_settings_crash_dialog_message`
  - `develop_settings_crash_dialog_confirm`

### Changed
- ML Kit native libraries (`libbarhopper_v3.so`, `libsurface_util_jni.so`, `libimage_processing_util_jni.so`) are now included in all build variants
- Release APK size increased by ~19 MB to ensure barcode scanning functionality works correctly
- Updated `DevelopSettingsActivity` to use confirmation dialog before test crashes

### Technical Details
- Removed `androidComponents.onVariants` packaging exclusions for ML Kit native libraries
- All four CPU architectures (arm64-v8a, armeabi-v7a, x86, x86_64) include complete ML Kit native library set
- No code functionality changes - all features work identically to v1.4.1

## 🔄 Migration Notes

### For Existing Users
- No migration required - this is a bug fix release
- QR code scanning that previously crashed will now work correctly
- All saved data and preferences are preserved

### For Developers
- If you were working around the ML Kit crash, you can remove any workarounds
- The APK size increase is necessary to prevent runtime crashes
- Consider this the stable baseline for ML Kit functionality

## 📊 Known Issues

None reported in this release.

## 🙏 Acknowledgments

This release fixes a critical production issue affecting QR code scanning functionality. Thanks to all users who reported the crash.

## 📥 Download

- **Release APK**: [app-release.apk](https://github.com/tobecrazy/Aircraft/releases/download/V1.4.2/app-release.apk)
- **Full Changelog**: [ChangeLogs.md](https://github.com/tobecrazy/Aircraft/blob/main/ChangeLogs.md#142---2026-10-08)

## ✅ Verification

To verify the fix, test QR code scanning:
1. Open the app
2. Go to Settings → QR Code Tool
3. Tap "Scan QR Code" button
4. Grant camera permission if prompted
5. Point camera at a QR code
6. Verify successful scan without crashes

---

**Full source code**: https://github.com/tobecrazy/Aircraft
**Previous release**: [v1.4.1](https://github.com/tobecrazy/Aircraft/releases/tag/V1.4.1)
