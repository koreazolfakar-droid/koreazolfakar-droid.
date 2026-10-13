# SwiftDrop Ultra Engine Phase 1 — validation and delivery

Validated on 2026-10-13 against the fetched alpha branch at
`aee0a237693740663878dc7a7ccb44d856a0a232`. Development branch:
`codex/swiftdrop-ultra-phase1`. A final fetch confirmed the base had not changed.

## Implemented

- Existing app workflows now use `TransferController → TransferCoordinator →
  TransferTransport → NearbyTransport → NearbyTransferManager`.
- Nearby remains the default and only operational transport. The Ultra Engine
  feature flag is **false**. The v1 metadata/service/QR protocol and explicit
  authentication approval remain intact; no new handshake is transmitted.
- Bounded, synchronized session/transfer state machines, UUIDs, terminal-event
  deduplication, generation/endpoint guards, main-looper callback ownership,
  cancellation during transfer/preparation/saving, and destination publication
  serialized with cancellation. History commits with successful publication.
- Scoped temporary-resource cleanup and staging/rename on API 26–28. MediaStore
  pending entries remain the publication mechanism on API 29+.
- Measured local structured diagnostics without names, tokens, peer IDs or contents.
- A validated budget model for a future chunk scheduler; no chunk executor added.
- CI release publication requires explicit manual opt-in, default false. Existing
  validation and signing steps are preserved. No workflow was dispatched.

Full architecture, networking/permissions/service plan, adaptive-transfer plan and
physical test matrix are in
[Phase 1 foundation](../phases/PHASE08_ULTRA_ENGINE_FOUNDATION.md).

## Automated results

A pristine checkout was built separately to distinguish inherited warnings and
failures from this change. The final source was revalidated after the last safety
and UI-cancellation changes.

| Check | Pristine base | Final foundation |
| --- | --- | --- |
| Gradle `:app:testDebugUnitTest` | 71 passed; 0 failures/errors | **110 passed; 0 failures/errors** |
| Android `:app:lintDebug` | 0 errors, 54 warnings | **0 errors, 57 warnings** |
| `:app:assembleDebug` | Passed | **Passed** |
| `:app:assembleDebugAndroidTest` | Passed | **Passed** (compilation/packaging only) |
| APK permissions | Baseline | **Exactly equal to baseline** |
| APK package/version | app.swiftdrop / 23 / 0.5.0-alpha | **Unchanged; verified with aapt** |
| Debug APK signature | Local development key | **apksigner verification passed** |
| Workflow YAML / publication gate | Existing workflow | Parses; manual opt-in required |
| `git diff --check` | — | Passed |

The three additional Lint warnings are `NewerVersionAvailable` notices for the
pinned **test-only** org.json, Robolectric and Mockito dependencies. They are not
APK dependencies; no new runtime Lint warning category appeared. Existing warnings
are retained, not hidden by a Lint baseline or suppression. Public deprecated
Nearby constructors in test fixtures also generate compiler notices.

The 39 added cases cover legacy JSON keys/UTF-8/unknown length/>2 GB counters,
unknown/legacy peer fallback and feature gating, coordinator delegation and busy
sessions, approval and repeated connections, stale callbacks, sequential file sends,
progress and reset terminal counters, duplicate completion, send/storage failures,
synchronous Nearby errors, cancellation between files and while saving, exact-once
publication, existing-file preservation, invalid-length cleanup, save completion
after remote disconnect, reused payload IDs across sessions, bounded ledgers,
concurrent completion/cancellation and diagnostic privacy.

Robolectric executes production manager callbacks/storage code with an injected
Nearby client and content provider. These tests perform **no radio transfer** and
cannot establish real Play Services or legacy-device compatibility. Emulator UI tests
were compiled into the instrumentation APK, but were not executed. No adb devices
or accelerated emulator were attached.

Build environment: Gradle 8.13, Temurin JDK 17, Android SDK 36/build-tools 35.0.0.
SDK/JDK installation, proxy and CA trust configuration were local to the execution
workspace; repository toolchain requirements and signing configuration were not changed.
Initial provisioning/JDK/proxy-trust failures were resolved before final validation.

Final debug APK SHA-256:
`b42b891cfdb022711f6f1de1eb6489486234354daf3c8e9ae8cad7756766ccc1`

Instrumentation APK SHA-256:
`9df1b539f9fcfa32acb1afecc207655034b6aa4e027bd20f0b650ff9096a8690`

Artifacts remain local under `app/build/outputs/apk/`; no APK, tag, release or binary
was published or committed. The retained CI signing key / upgrade installation
check was not run locally; unchanged CI signing configuration continues to gate
that check. The local APK uses a development key.

## Compatibility and limits

The package, signing configuration, min/target SDK, manifest permissions, Nearby
service ID/point-to-point strategy, QR pairing and legacy UTF-8 JSON fields are
preserved. Arabic/English resources, RTL, themes, gallery/media code, background
music service, APK export, permission/scoped-storage logic and history UI remain
in place. App/export code consumes the new interface; navigation also recognizes
radio startup/saving and the Cancel button now cancels during saving.

Compatibility is supported by source review and automated codec/lifecycle/manifest
checks, **not** by two-device measurements. Version remains **0.5.0-alpha / 23**;
**0.6.0-alpha** is the initial release target after release/device validation.

Only planned: independent local TCP, Wi-Fi Direct, Local-Only Hotspot, transport
readiness/band selection, TLS peer identity, chunk scheduling/parallel streams,
SHA-256 transfer verification, persistent checkpoints and resume. There is no
automatic reconnection, pause/resume UI, transfer foreground service or process-death
recovery. Length checks remain the current receive integrity mechanism; no speed or
cryptographic file-verification claim is made.

The ledger permits at most **4,096 file IDs per connection**, including terminal
IDs retained for duplicate protection. Reconnect explicitly to reset it. Progress
UI continues showing one current payload. Missing metadata has no new watchdog;
explicit disconnect/close cleans tracked resources. Untracked stale payload URIs
are not deleted by guesses; their descriptors close while storage remains owned by
Play Services. Crash/power-loss durability, orphan recovery and storage-provider
atomicity need later implementation and device testing.

## Unperformed validation / next phase

All physical-device tests are **unperformed**: Android 8–16 on two manufacturers;
legacy/new sender/receiver combinations; 100 MB, 1 GB, >2 GB, empty and many small
files; independent SHA-256 comparison; disconnection/cancellation/reconnection;
low storage; backgrounding and background music; network switching; permission
changes; gallery/app sharing; English/Arabic/RTL/themes/large text. Emulator UI
execution is also unperformed. See the foundation document for detailed evidence
requirements. Do not mark these passed or enable the Ultra Engine from JVM results.

Keep the PR draft and do not merge or publish a release until device/UI validation
and release review are complete. Next: validate this foundation with real legacy
peers, then prototype **one authenticated same-Wi-Fi TCP stream** behind the disabled
flag and benchmark it before adding adaptive chunks or more streams.

## Exact changed files

- `.github/workflows/swiftdrop-android-ci.yml`
- `SwiftDrop-Android/app/build.gradle.kts`
- `SwiftDrop-Android/app/src/main/java/app/swiftdrop/FileMetadataProtocol.kt`
- `SwiftDrop-Android/app/src/main/java/app/swiftdrop/InstalledAppsController.kt`
- `SwiftDrop-Android/app/src/main/java/app/swiftdrop/NearbyTransferManager.kt`
- `SwiftDrop-Android/app/src/main/java/app/swiftdrop/SwiftDropApp.kt`
- `SwiftDrop-Android/app/src/main/java/app/swiftdrop/SwiftDropViewModel.kt`
- `SwiftDrop-Android/app/src/main/java/app/swiftdrop/TransferCapabilities.kt`
- `SwiftDrop-Android/app/src/main/java/app/swiftdrop/TransferCoordinator.kt`
- `SwiftDrop-Android/app/src/main/java/app/swiftdrop/TransferDiagnostics.kt`
- `SwiftDrop-Android/app/src/main/java/app/swiftdrop/TransferModels.kt`
- `SwiftDrop-Android/app/src/main/java/app/swiftdrop/TransferSessionManager.kt`
- `SwiftDrop-Android/app/src/main/java/app/swiftdrop/TransferTransport.kt`
- `SwiftDrop-Android/app/src/test/java/app/swiftdrop/FileMetadataProtocolTest.kt`
- `SwiftDrop-Android/app/src/test/java/app/swiftdrop/NearbyLifecycleTest.kt`
- `SwiftDrop-Android/app/src/test/java/app/swiftdrop/TransferCapabilitiesTest.kt`
- `SwiftDrop-Android/app/src/test/java/app/swiftdrop/TransferCoordinatorTest.kt`
- `SwiftDrop-Android/app/src/test/java/app/swiftdrop/TransferDiagnosticsTest.kt`
- `SwiftDrop-Android/app/src/test/java/app/swiftdrop/TransferSessionManagerTest.kt`
- `SwiftDrop-Android/docs/DEV_BLOG/2026-10-DIARY.md`
- `SwiftDrop-Android/docs/WORKDIR/phases/PHASE08_ULTRA_ENGINE_FOUNDATION.md`
- `SwiftDrop-Android/docs/WORKDIR/reports/PHASE08_ULTRA_ENGINE_VALIDATION.md`
