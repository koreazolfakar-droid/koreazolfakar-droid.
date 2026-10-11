# SwiftDrop Android — 0.2.0-alpha

Native Kotlin / Jetpack Compose file sharing using Google's Nearby Connections API. The existing `app.swiftdrop` identity, `app.swiftdrop.nearby` service, metadata protocol and `P2P_POINT_TO_POINT` transport are preserved.

## Features

- Material 3 dashboard, rounded vector icons, original adaptive blue/cyan launcher icon and Android 13 monochrome layer.
- Dedicated file library: photos, videos, documents, music and all files use Android's Storage Access Framework. Multiple selections show actual names, sizes and a review step before discovery.
- Applications lists real launcher-visible installed user applications. Selecting apps exports their base and split APK files into scoped app cache for sharing; application data is never exported. Existing APK files can also be selected through the system picker. Split applications require a compatible split APK installer on the recipient; automatic installation is not implemented.
- Real nearby discovery, waiting/connection states, animated radar, retry/cancel and explicit authentication-code matching on both devices.
- Per-file progress from Nearby callbacks, transferred bytes, total size, peer name, rotating activity arc, measured speed and estimated time only when sufficient real samples exist. Received files show a saving state until the copy succeeds.
- Files are saved to `Downloads/SwiftDrop` through MediaStore on Android 10+, or app-private downloads on Android 8/9. Original safe filename normalization and collision handling are retained.
- Session history with file sizes, direction, peer and timestamp. History survives Activity rotation but is not persistent across process death.
- Persistent Light / Dark / System appearance and Arabic / English / System language preferences, with immediate RTL/LTR switching.
- Scrollable layouts, adaptive home actions, system insets, font scaling, Material touch feedback and at least 48dp interactive controls. Compose previews cover small phones, large text, RTL, dark mode and tablets; runtime accessibility testing remains required.

## Build

Use JDK 17, Android SDK API 36, build tools 35.0.0 and Gradle 8.13. Open this directory in Android Studio, or run:

```sh
gradle --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

The wrapper properties are included but the wrapper executable/JAR are not packaged; use a local Gradle distribution. The existing GitHub workflow `.github/workflows/swiftdrop-android-ci.yml` runs the same checks on pushes to `swiftdrop-android-v0.1-alpha` and uploads `swiftdrop-v0.2.0-alpha-debug-apk` and validation reports for 14 days. The Debug APK is installable but is not a signed production release.

## Automated validation

All five implementation stages passed debug compilation, unit tests and lint locally. Final validation: **21 unit tests passed, 0 failures; lint 0 errors / 12 warnings; assembleDebug passed**. APK signature verification passed (v2), and packaged metadata confirmed `app.swiftdrop`, version code 2 and `0.2.0-alpha`. Tests cover safe filenames, API-specific permissions, real-measurement timing/reset conditions, and progress arithmetic including large files and unknown sizes.

No connected Android device, emulator or KVM was available. Compilation and unit tests do not prove radio discovery, Android provider behavior or successful transfers on hardware. See [implementation report](docs/WORKDIR/reports/PHASE05_PREMIUM_UI.md).

## Required device validation

1. Install the same APK on two real devices with Google Play Services. Enable Wi-Fi/Bluetooth and grant the requested API-specific permissions.
2. Recipient: Receive. Sender: Send Files, choose a category, select actual files, review, Send, select the real discovered recipient. Compare the displayed authentication code on both devices before accepting.
3. Check PDF, music, photos, videos, ordinary APK, split APK sets, multiple files and a file larger than 2 GB; compare SHA-256 of source and saved bytes externally.
4. Exercise permission denial/retry/settings, rejection, cancellation, disconnect, rediscovery, rotation, navigation during transfer, duplicate filenames, full storage and providers with unknown size.
5. Test Android 8–16, Arabic/English, RTL, Light/Dark/System, TalkBack, 200% text, small phones, tablets, cutouts and gesture navigation. Check protocol compatibility with 0.1.0-alpha on the other device.

## Privacy and limitations

No ads, analytics, paid services, account, upload server, app INTERNET permission, QUERY_ALL_PACKAGES, MANAGE_EXTERNAL_STORAGE or installation permission were added. Launcher package visibility is declared solely for the applications list. Required Nearby Bluetooth/Wi-Fi/location permissions vary by Android version; READ_EXTERNAL_STORAGE remains limited to API 32 and below for Nearby FILE payloads. File selection uses explicit system-picker grants. App-export cache is scoped to the app and may be reclaimed by Android or cleared in Android app storage settings.

QR connection is clearly marked upcoming. Pause/resume, foreground/background transfer service, persistent history, checksum verification, Windows transport and independent Wi-Fi Direct fallback are not implemented. The transfer manager is retained by a ViewModel across Activity rotation; leaving the process or OS termination can interrupt transfers. Google Play Services is required. This alpha requires the device checks above before a production release.
