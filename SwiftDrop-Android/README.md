# SwiftDrop Android — 0.1.0-alpha

Open-source style MVP **source project**, not a prebuilt APK. Native Android application with Kotlin, Jetpack Compose, and Google's Nearby Connections API.

## Implemented in this milestone
- Ad-free home screen (Send / Receive), device discovery, connection confirmation code, file selection, transfer progress, history, settings.
- Nearby Connections high-bandwidth `P2P_POINT_TO_POINT` strategy. No account, upload server, or Internet connection required for the transfer.
- Multiple file selection via Android system document picker (`OpenMultipleDocuments`). File descriptors are streamed to Nearby without making an up-front copy.
- Both endpoints must accept the connection after comparing the displayed authentication digits.
- Files are saved to `Downloads/SwiftDrop` via `MediaStore` (Android 10+) or app-private downloads (Android 8/9). Received payload is copied only *after* its transfer succeeded.
- Transfer history is in memory in v0.1 (clears upon restart). Dark theme setting applies for the current app process only.

## Build (Android Studio, JDK 17)
1. Open the project folder in Android Studio with Android SDK API 36 installed.
2. Ensure `gradle` version 8.13 available. If the distribution has no wrapper JAR, use **Android Studio > Settings > Build Tools > Gradle > Use local Gradle distribution** OR run `gradle wrapper --gradle-version 8.13` once on a machine with Gradle installed. The wrapper properties are included; the wrapper executable/JAR aren't packaged.
3. Sync dependencies and run **app** on **two real Android devices with Google Play Services**.
4. Install the same APK on both. Enable Bluetooth and Wi-Fi; grant requested Nearby permissions.
5. On recipient tap Receive; on sender tap Send and select files; pick the discovered device; compare PIN on BOTH phones and accept; confirm files appear in Downloads/SwiftDrop.

## Verification checklist (required before public release)
- Gradle assembleDebug and JUnit tests in Android SDK environment (NOT run in this environment — no Android SDK or Gradle installed).
- Two-device transfer: small PDF, APK and >2GB file; Android 8–16, permissions denied/allowed, airplane mode with Wi-Fi/Bluetooth enabled, screen rotation, connection drops, storage full, duplicate filenames, corrupted bytes, and concurrent requests.
- Test payload metadata arriving before or after FILE payload; verify bytes SHA-256 before using for high-assurance integrity (not yet implemented).
- Reconnect/resume after interruption (not implemented); Android ↔ Windows and Wi-Fi Direct independent transport (not implemented).
- Background transfer, notifications, accessibility and Arabic translation (not yet implemented).

## Security / limitations
- The built-in Nearby handshake uses authentication digits that users must compare before acceptance.
- No INTERNET permission; network interactions go through Google Play Services; zero app ads/analytics.
- Google Play Services required in v0.1; native Wi-Fi Direct fallback / Wi-Fi 7 optimization require future work and measurement on compatible hardware.
- Progress is per-file; transferred-file history is not persisted; no pause/resume or QR scanning in this version.
- Connection lifecycle is tied to MainActivity. Avoid leaving the screen or rotating while transferring until a foreground service is added.
- This code is a source baseline, **not** a tested production release.
