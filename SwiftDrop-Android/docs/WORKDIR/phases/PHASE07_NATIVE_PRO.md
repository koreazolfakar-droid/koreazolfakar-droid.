# SwiftDrop Native Pro implementation and validation

Baseline: `9fbf3d708a7929dd0c3750713f9f15412d4f6461`, fetched from
`swiftdrop-android-v0.1-alpha` before edits on 12 October 2026.

## Reference decisions

The two light references establish the home hierarchy and selected Concept B
identity. The dark overview supplies compact folder, media, app and transfer
layouts. Use an original pair of opposing horizontal vector arrows; neutral
surfaces; royal blue transfer actions; restrained cyan receiving accents; pink
photos, purple videos, coral music, green apps, amber folders and blue documents.
Do not reproduce the mockups' example media, counts, unsupported tabs or gradients.

## Audit and boundaries

- Existing destinations: welcome, home, files, standalone photos/videos/music,
  apps, documents/storage, selection, discovery, receive, transfer, history,
  settings and permissions. Native pickers and scanner are Android/Play Services UI.
- Preserve `NearbyTransferManager`, protocol, measurements, hashing, storage saving,
  app export, permissions and settings storage. History currently records completed
  files from the current session; no persisted or failed history is claimed.
- Preserve `SwiftDropLocalizedProviders` and its captured Activity Result / Back
  owners. Test Storage via the actual MainActivity in English and Arabic.
- Playback is owned by `SwiftDropMusicService` / Media3. UI controllers may be
  released without stopping the service. Keep audio focus and session controls.
- Package: `app.swiftdrop`; min SDK 26; compile SDK 36; target SDK 35.
- Existing CI: unit tests, APK/test APK compilation, Lint, Android 36 Google APIs
  Compose tests, retained development keystore and public GitHub prerelease.
- Environment: Java 21 initially available; Android SDK installed for this task.
  No KVM or physical Android device attached. CI provides accelerated emulator QA.

## Checklist

- [x] Fetch branch, inspect all three references and inventory existing UI.
- [x] Shared light/dark tokens, typography, shapes, search, rows and navigation.
- [x] Vector adaptive, legacy, monochrome, in-app and startup Concept B identity.
- [x] Home hierarchy, six distinct categories, live connection and real history.
- [x] Storage/SAF, distinct Documents, sorting, breadcrumbs, selection and sharing.
- [x] Photos/videos, adaptive thumbnails, albums, search, selection and viewers.
- [x] Music library, artwork, persistent mini player, player and queue.
- [x] Discovery, secure confirmation, QR, selection, transfer and history.
- [x] Settings, onboarding, permissions, empty/error states and dialogs.
- [ ] Kotlin, unit tests, Android Lint and Compose regression checks.
- [ ] English/Arabic, light/dark, font scaling, rotation and Android 16 QA.
- [ ] Reviewable commits, compatible signed APK and verified public release link.
