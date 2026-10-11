# Premium sharing UI — v0.2.0-alpha

## Before / after

| Area | Previous alpha | Updated alpha |
| --- | --- | --- |
| Visual system | Inline UI, inconsistent symbols and theme styling | Central Material 3 palette, typography, shapes, dimensions, motion and vector components |
| Home | Basic Send/Receive page | Compact dashboard, connectivity/session card, category shortcuts, recent transfers, bottom navigation |
| Selection | System picker without a dedicated library | Category library including music, real installed-app APK export, selected metadata/count/size review |
| Discovery / receive | Basic device list and status | Real endpoints, animated radar, pending state, retry/cancel, code-verification dialog |
| Transfer | Basic linear progress | Callback-backed circular progress, rotating active arc, bytes/device/file, measured speed/ETA, saving/completion/error/cancel |
| History | Basic session rows | Direction/file icons, actual sizes, device labels and localized timestamps |
| Settings | Temporary appearance | Persistent theme and language, Arabic RTL, privacy/storage/engine/version information |
| Launcher | Previous drawable | Original adaptive blue/cyan transfer silhouette and monochrome layer |

## Implementation and compatibility

Five incremental implementation stages preserve the Nearby engine, service ID, point-to-point strategy, metadata JSON fields, explicit authentication and Android file-saving paths. Presentation is separated into theme, components and screens. ViewModel ownership retains the active manager across Activity rotation. Callback measurements never synthesize speeds; ETA needs sufficient samples. Async save completion is tied to its payload so it cannot replace a newer file's progress. Session-generation checks discard stale outgoing work after cancellation or reconnect.

The dedicated applications list queries launcher-visible user apps without unrestricted package visibility. Export reads base and split APKs into scoped cache and exposes only that cache through a non-exported FileProvider. No app data or install operation is exposed. Music and other categories retain SAF. Required permissions are requested only when starting Nearby, with SDK-specific rules; Android 12 requests both coarse and fine location, as required by its location prompt behavior.

## Validation evidence

Every phase passed `testDebugUnitTest`, `lintDebug`, and `assembleDebug` before its commit. Final fresh build passed all tasks with JDK 17 / Gradle 8.13 / API 36. Unit tests: SafeFileName 2, NearbyPermissions 5, TransferMeasurements 10, TransferProgress 4; **21 passed, 0 failures/errors**. Final lint: **0 errors, 12 warnings** (target API advisory, versioned permission attribute, backup extraction advisory, redundant SDK guard, unused icon/string resources, v26 monochrome advisory despite a v33 monochrome resource, and KTX style suggestions). No lint baseline was introduced. Existing ComponentActivity has a narrowly documented suppression for an unused transitive Fragment dependency.

`apksigner verify` confirmed the installable Debug APK signature (v2). `aapt dump badging` confirmed package `app.swiftdrop`, code 2, version `0.2.0-alpha`, min SDK 26, target SDK 35, compile SDK 36, and the adaptive icon. The APK has no added ads/analytics, broad storage, Internet, installation or all-package permission.

The existing SwiftDrop workflow now includes lint and uploads APK plus reports. GitHub workflow status, final remote commit and actual artifact links are provided in the delivery report after the push is independently verified.

## Remaining device checks

No device/emulator was available; no screen rendering, TalkBack session, actual Nearby transfer, saving/provider integration or performance measurement has been claimed. Run the README's two-device checklist, especially radio permissions, code matching, split APKs, large/unknown-size files, cancellation, rotation, storage failure, RTL/font scaling and compatibility with the previous alpha. QR, pause/resume, background service, persisted history and automatic app installation remain unavailable. App sharing excludes invisible/non-launcher/system-only applications. Exported split APK files need a suitable installer. Session history ends at process death.

## Modified files

- `.github/workflows/swiftdrop-android-ci.yml`
- `SwiftDrop-Android/README.md`
- `SwiftDrop-Android/app/build.gradle.kts`
- `SwiftDrop-Android/app/src/main/AndroidManifest.xml`
- `SwiftDrop-Android/app/src/main/java/app/swiftdrop/AppSettings.kt`
- `SwiftDrop-Android/app/src/main/java/app/swiftdrop/InstalledAppsController.kt`
- `SwiftDrop-Android/app/src/main/java/app/swiftdrop/MainActivity.kt`
- `SwiftDrop-Android/app/src/main/java/app/swiftdrop/NearbyPermissions.kt`
- `SwiftDrop-Android/app/src/main/java/app/swiftdrop/NearbyTransferManager.kt`
- `SwiftDrop-Android/app/src/main/java/app/swiftdrop/SwiftDropApp.kt`
- `SwiftDrop-Android/app/src/main/java/app/swiftdrop/SwiftDropViewModel.kt`
- `SwiftDrop-Android/app/src/main/java/app/swiftdrop/TransferMeasurements.kt`
- `SwiftDrop-Android/app/src/main/java/app/swiftdrop/TransferModels.kt`
- `SwiftDrop-Android/app/src/main/java/app/swiftdrop/ui/components/Components.kt`
- `SwiftDrop-Android/app/src/main/java/app/swiftdrop/ui/previews/HomePreviews.kt`
- `SwiftDrop-Android/app/src/main/java/app/swiftdrop/ui/screens/discovery/DiscoveryScreen.kt`
- `SwiftDrop-Android/app/src/main/java/app/swiftdrop/ui/screens/files/FileLibraryScreen.kt`
- `SwiftDrop-Android/app/src/main/java/app/swiftdrop/ui/screens/history/HistoryScreen.kt`
- `SwiftDrop-Android/app/src/main/java/app/swiftdrop/ui/screens/home/HomeScreen.kt`
- `SwiftDrop-Android/app/src/main/java/app/swiftdrop/ui/screens/permissions/PermissionsScreen.kt`
- `SwiftDrop-Android/app/src/main/java/app/swiftdrop/ui/screens/settings/SettingsScreen.kt`
- `SwiftDrop-Android/app/src/main/java/app/swiftdrop/ui/screens/transfer/TransferScreen.kt`
- `SwiftDrop-Android/app/src/main/java/app/swiftdrop/ui/theme/SwiftDropTheme.kt`
- `SwiftDrop-Android/app/src/main/res/drawable/ic_appearance.xml`
- `SwiftDrop-Android/app/src/main/res/drawable/ic_applications.xml`
- `SwiftDrop-Android/app/src/main/res/drawable/ic_back.xml`
- `SwiftDrop-Android/app/src/main/res/drawable/ic_check.xml`
- `SwiftDrop-Android/app/src/main/res/drawable/ic_close.xml`
- `SwiftDrop-Android/app/src/main/res/drawable/ic_documents.xml`
- `SwiftDrop-Android/app/src/main/res/drawable/ic_error.xml`
- `SwiftDrop-Android/app/src/main/res/drawable/ic_folder.xml`
- `SwiftDrop-Android/app/src/main/res/drawable/ic_history.xml`
- `SwiftDrop-Android/app/src/main/res/drawable/ic_home.xml`
- `SwiftDrop-Android/app/src/main/res/drawable/ic_info.xml`
- `SwiftDrop-Android/app/src/main/res/drawable/ic_language.xml`
- `SwiftDrop-Android/app/src/main/res/drawable/ic_launcher_background.xml`
- `SwiftDrop-Android/app/src/main/res/drawable/ic_launcher_foreground.xml`
- `SwiftDrop-Android/app/src/main/res/drawable/ic_launcher_monochrome.xml`
- `SwiftDrop-Android/app/src/main/res/drawable/ic_music.xml`
- `SwiftDrop-Android/app/src/main/res/drawable/ic_nearby.xml`
- `SwiftDrop-Android/app/src/main/res/drawable/ic_photos.xml`
- `SwiftDrop-Android/app/src/main/res/drawable/ic_progress.xml`
- `SwiftDrop-Android/app/src/main/res/drawable/ic_qr.xml`
- `SwiftDrop-Android/app/src/main/res/drawable/ic_receive.xml`
- `SwiftDrop-Android/app/src/main/res/drawable/ic_retry.xml`
- `SwiftDrop-Android/app/src/main/res/drawable/ic_security.xml`
- `SwiftDrop-Android/app/src/main/res/drawable/ic_send.xml`
- `SwiftDrop-Android/app/src/main/res/drawable/ic_settings.xml`
- `SwiftDrop-Android/app/src/main/res/drawable/ic_videos.xml`
- `SwiftDrop-Android/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- `SwiftDrop-Android/app/src/main/res/mipmap-anydpi-v33/ic_launcher.xml`
- `SwiftDrop-Android/app/src/main/res/values-ar/strings.xml`
- `SwiftDrop-Android/app/src/main/res/values-night/styles.xml`
- `SwiftDrop-Android/app/src/main/res/values/strings.xml`
- `SwiftDrop-Android/app/src/main/res/values/styles.xml`
- `SwiftDrop-Android/app/src/main/res/xml/shared_file_paths.xml`
- `SwiftDrop-Android/app/src/test/java/app/swiftdrop/NearbyPermissionsTest.kt`
- `SwiftDrop-Android/app/src/test/java/app/swiftdrop/TransferMeasurementsTest.kt`
- `SwiftDrop-Android/app/src/test/java/app/swiftdrop/TransferProgressTest.kt`
- `SwiftDrop-Android/docs/DEV_BLOG/2026-10-DIARY.md`
- `SwiftDrop-Android/docs/WORKDIR/reports/PHASE05_PREMIUM_UI.md`

## Updated icon resources

- `SwiftDrop-Android/app/src/main/res/drawable/ic_appearance.xml`
- `SwiftDrop-Android/app/src/main/res/drawable/ic_applications.xml`
- `SwiftDrop-Android/app/src/main/res/drawable/ic_back.xml`
- `SwiftDrop-Android/app/src/main/res/drawable/ic_check.xml`
- `SwiftDrop-Android/app/src/main/res/drawable/ic_close.xml`
- `SwiftDrop-Android/app/src/main/res/drawable/ic_documents.xml`
- `SwiftDrop-Android/app/src/main/res/drawable/ic_error.xml`
- `SwiftDrop-Android/app/src/main/res/drawable/ic_folder.xml`
- `SwiftDrop-Android/app/src/main/res/drawable/ic_history.xml`
- `SwiftDrop-Android/app/src/main/res/drawable/ic_home.xml`
- `SwiftDrop-Android/app/src/main/res/drawable/ic_info.xml`
- `SwiftDrop-Android/app/src/main/res/drawable/ic_language.xml`
- `SwiftDrop-Android/app/src/main/res/drawable/ic_launcher_background.xml`
- `SwiftDrop-Android/app/src/main/res/drawable/ic_launcher_foreground.xml`
- `SwiftDrop-Android/app/src/main/res/drawable/ic_launcher_monochrome.xml`
- `SwiftDrop-Android/app/src/main/res/drawable/ic_music.xml`
- `SwiftDrop-Android/app/src/main/res/drawable/ic_nearby.xml`
- `SwiftDrop-Android/app/src/main/res/drawable/ic_photos.xml`
- `SwiftDrop-Android/app/src/main/res/drawable/ic_progress.xml`
- `SwiftDrop-Android/app/src/main/res/drawable/ic_qr.xml`
- `SwiftDrop-Android/app/src/main/res/drawable/ic_receive.xml`
- `SwiftDrop-Android/app/src/main/res/drawable/ic_retry.xml`
- `SwiftDrop-Android/app/src/main/res/drawable/ic_security.xml`
- `SwiftDrop-Android/app/src/main/res/drawable/ic_send.xml`
- `SwiftDrop-Android/app/src/main/res/drawable/ic_settings.xml`
- `SwiftDrop-Android/app/src/main/res/drawable/ic_videos.xml`
- `SwiftDrop-Android/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- `SwiftDrop-Android/app/src/main/res/mipmap-anydpi-v33/ic_launcher.xml`
