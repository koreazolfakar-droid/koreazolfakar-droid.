# Wi-Fi permission correction and reference UI — v0.2.1-alpha

## Reported failure and correction

The supplied device screenshot shows Nearby error 8032 / MISSING_PERMISSION_ACCESS_WIFI_STATE while receiving. The previous manifest capped ACCESS_WIFI_STATE and CHANGE_WIFI_STATE at API 31, causing Android 32+ to exclude both normal permissions. Removed only those two caps. Runtime Bluetooth, Nearby Wi-Fi, legacy location and legacy FILE permissions retain their existing API-specific rules. No new permission group, broad storage access, analytics or service was introduced. NearbyTransferManager, service ID, strategy, protocol, authentication and file persistence code are unchanged in this follow-up.

Added four JUnit checks that inspect SingleArtifact.MERGED_MANIFEST from the actual debug variant, rather than testing only the runtime permission helper. They prevent Wi-Fi caps from returning, validate runtime declarations across APIs 26–36, preserve package identity, and exclude unrestricted package/storage/Internet/install permissions. An Android 15 ATD install independently reported both ACCESS_WIFI_STATE and CHANGE_WIFI_STATE as granted=true.

## Reference implementation

| Screen | Changes |
| --- | --- |
| Launch branding / welcome | Original geometric paper-plane foreground, gradient adaptive background, monochrome layer, vector brand mark; skippable welcome persists completion |
| Home | Compact wordmark/status, blue/cyan action gradients, working file/history shortcuts, music/apps/photos shortcuts and actual session transfers |
| File library | Responsive colorful category grid, real Files/Apps tabs, Android picker actions, measured device-volume free/total storage |
| Applications | Real launcher-visible apps, working search and selection, base/split APK export and existing-APK fallback preserved |
| Discovery / receive | Filled animated radar, real device names, clear failure title and error card; no waiting claim after startup failure |
| Selection | Category icons, actual metadata, unknown total while metadata loads, existing send action |
| Transfer / completion | Current-file circular progress, activity arc, measured speed/ETA, file/device card, green completion and working send-more/history actions |
| History | Real session records, search and All/Sent/Received filters |
| Settings | Compact expandable appearance/language choices, privacy/storage/engine/version information |

Kept native Android navigation, real authentication-code confirmation, both themes and Arabic/English RTL/LTR. Large fonts stack action/metric cards and scale grid cells. The security dialog scrolls when needed; layouts remain scrollable and include system/IME insets. The reference's fabricated ranges/counts, non-Android endpoints, functional QR scanner and pause/resume are not reproduced: the existing engine has no QR/pause support, and only actual engine data is displayed.

## Validation

Local JDK 17 / Gradle 8.13 / API 36: testDebugUnitTest, lintDebug and assembleDebug passed. **25 tests passed, zero failures/errors. Lint: zero errors, 11 warnings.** APK signature verified (v2). Package app.swiftdrop is preserved; version code 3, version name 0.2.1-alpha, min API 26 and target API 35. Arabic/English resource identifiers match. Stale generated incremental outputs were retained outside the checkout before fresh output validation; no git reset or clean was used.

Runtime evidence currently includes a successful Android 15 ATD install, a populated onboarding UI tree and both normal Wi-Fi grants. ATD disables hardware rendering; its black screenshot is not presented as a UI preview. A regular graphics emulator timed out during boot without KVM acceleration, so visual screenshot verification was unavailable. No physical two-device transfer, radio performance, split-APK installation or TalkBack session has been claimed. Test receipt and discovery on the user's Android device after installing the updated APK, compare codes on both phones, and verify saved-file hashes. QR, pause/resume, background service and persistent history remain outside this update.

The workflow now caches its development keystore for consistent signatures across subsequent successful Debug builds. Earlier Debug APKs can have a different signing key; this does not establish production signing.

The existing branch workflow builds tests/lint/APK and uploads swiftdrop-v0.2.1-alpha-debug-apk and validation reports. Actual workflow/artifact status is supplied in the delivery message after GitHub verification.

## Changed files

- `.github/workflows/swiftdrop-android-ci.yml`
- `SwiftDrop-Android/README.md`
- `SwiftDrop-Android/app/build.gradle.kts`
- `SwiftDrop-Android/app/src/main/AndroidManifest.xml`
- `SwiftDrop-Android/app/src/main/java/app/swiftdrop/AppSettings.kt`
- `SwiftDrop-Android/app/src/main/java/app/swiftdrop/SwiftDropApp.kt`
- `SwiftDrop-Android/app/src/main/java/app/swiftdrop/SwiftDropViewModel.kt`
- `SwiftDrop-Android/app/src/main/java/app/swiftdrop/ui/components/Components.kt`
- `SwiftDrop-Android/app/src/main/java/app/swiftdrop/ui/components/PremiumComponents.kt`
- `SwiftDrop-Android/app/src/main/java/app/swiftdrop/ui/screens/discovery/DiscoveryScreen.kt`
- `SwiftDrop-Android/app/src/main/java/app/swiftdrop/ui/screens/files/FileLibraryScreen.kt`
- `SwiftDrop-Android/app/src/main/java/app/swiftdrop/ui/screens/history/HistoryScreen.kt`
- `SwiftDrop-Android/app/src/main/java/app/swiftdrop/ui/screens/home/HomeScreen.kt`
- `SwiftDrop-Android/app/src/main/java/app/swiftdrop/ui/screens/onboarding/WelcomeScreen.kt`
- `SwiftDrop-Android/app/src/main/java/app/swiftdrop/ui/screens/settings/SettingsScreen.kt`
- `SwiftDrop-Android/app/src/main/java/app/swiftdrop/ui/screens/transfer/TransferScreen.kt`
- `SwiftDrop-Android/app/src/main/java/app/swiftdrop/ui/theme/SwiftDropTheme.kt`
- `SwiftDrop-Android/app/src/main/res/drawable/ic_brand.xml`
- `SwiftDrop-Android/app/src/main/res/drawable/ic_expand.xml`
- `SwiftDrop-Android/app/src/main/res/drawable/ic_launcher_background.xml`
- `SwiftDrop-Android/app/src/main/res/drawable/ic_launcher_foreground.xml`
- `SwiftDrop-Android/app/src/main/res/drawable/ic_launcher_monochrome.xml`
- `SwiftDrop-Android/app/src/main/res/drawable/ic_no_ads.xml`
- `SwiftDrop-Android/app/src/main/res/drawable/ic_search.xml`
- `SwiftDrop-Android/app/src/main/res/values-ar/strings.xml`
- `SwiftDrop-Android/app/src/main/res/values/strings.xml`
- `SwiftDrop-Android/app/src/test/java/app/swiftdrop/NearbyManifestTest.kt`
- `SwiftDrop-Android/docs/DEV_BLOG/2026-10-DIARY.md`
- `SwiftDrop-Android/docs/WORKDIR/reports/PHASE06_WIFI_REFERENCE_UI.md`
