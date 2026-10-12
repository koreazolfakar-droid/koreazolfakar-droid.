# SwiftDrop Android — 0.4.1-alpha

Native Kotlin / Jetpack Compose file sharing using Google's Nearby Connections API. The existing `app.swiftdrop` identity, `app.swiftdrop.nearby` service, metadata protocol and `P2P_POINT_TO_POINT` transport are preserved.

## Features

- Four-section local library for photos, videos, music and installed apps. MediaStore results load in pages; visible thumbnails use a bounded memory cache. Search, long-press multi-selection and Send selected feed the existing Nearby file-selection path.
- Photos open in a full-screen pinch-to-zoom viewer. Local videos and music play through Media3 with seek and pause controls; players release their resources when the preview closes and pause when the activity stops.
- Media permissions are requested only when the user taps Allow access in a section. Android 14+ limited photo/video grants and reselection are supported. Denial or revocation keeps that section locked; Android's document picker and an App settings shortcut remain available.
- Installed user apps show their actual icons. Export still copies base and split APK files to scoped cache before sharing. App data is never exported.
- Nearby discovery, QR pairing, mutual authentication, verified file persistence, transfer progress/history, Arabic and English, RTL, light/dark theme and SAF document selection remain available.
- Received files are saved to `Downloads/SwiftDrop` through MediaStore on Android 10+, or app-private downloads on Android 8/9.

## Build

Use JDK 17, Android SDK API 36, build tools 35.0.0 and Gradle 8.13. Open this directory in Android Studio, or run:

```sh
gradle --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

The wrapper properties are included but the wrapper executable/JAR are not packaged; use a local Gradle distribution. `.github/workflows/swiftdrop-android-ci.yml` runs unit tests, Lint, APK assembly and Compose UI tests on pushes to `swiftdrop-android-v0.1-alpha`. It uploads a signed development APK as `swiftdrop-v0.4.1-alpha-debug-apk` for 14 days. The development keystore is cached temporarily; production signing is separate.

## Automated validation

Unit tests cover media grant scopes, partial access, denial/revocation and independence from Nearby permissions. Compose UI tests cover the denied-gallery request path and app access under RTL. CI runs these tests on an Android 15 emulator; real two-device radio and media decoder behavior still require physical hardware checks.

## Required device validation

1. Install the same APK on two real devices with Google Play Services. Enable Wi-Fi/Bluetooth and grant the requested API-specific permissions.
2. Recipient: Receive. Sender: Send Files, choose a category, select actual files, review, Send, select the real discovered recipient. Compare the displayed authentication code on both devices before accepting.
3. Check PDF, music, photos, videos, ordinary APK, split APK sets, multiple files and a file larger than 2 GB; compare SHA-256 of source and saved bytes externally.
4. Exercise permission denial/retry/settings, rejection, cancellation, disconnect, rediscovery, rotation, navigation during transfer, duplicate filenames, full storage and providers with unknown size.
5. Test Android 8–16, Arabic/English, RTL, Light/Dark/System, TalkBack, 200% text, small phones, tablets, cutouts and gesture navigation. Check protocol compatibility with 0.1.0-alpha on the other device.

## Privacy and limitations

All library and playback data stays on the device. No ads, analytics, cloud uploads, `QUERY_ALL_PACKAGES`, `MANAGE_EXTERNAL_STORAGE` or installation permission were added. MediaStore queries use scoped Android media permissions, while SAF remains available without a library grant. Launcher package visibility is limited to the installed-app list. Nearby Bluetooth/Wi-Fi/location requests remain scoped to sharing and receiving. Android 8–12 storage permission is requested only when browsing media; it is not needed to initiate Nearby discovery.

A cached Debug signing key is for development updates only. Hardware compatibility, large collections, decoder support, Android 14 partial permission behavior and two-device transfer still need testing on representative phones.
