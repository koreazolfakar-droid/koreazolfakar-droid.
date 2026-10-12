# SwiftDrop 0.5.0-alpha — Native Pro

A complete native Compose visual refresh of the existing SwiftDrop app.

- Original vector opposing-arrow identity, adaptive and monochrome launcher icons,
  theme-aware startup, compact home hierarchy and six distinct category destinations.
- Consistent neutral light/dark surfaces, native typography, category accents,
  reusable search and navigation, restrained press feedback and system insets.
- Storage capacity, functional breadcrumbs, name/date/size/type sorting, scoped
  folder access, selection and Android sharing; a separate Documents destination
  with working file-type filters.
- Photos albums, video folders, adaptive date-grouped thumbnails, full-screen
  previews, photo zoom, Android sharing and selection.
- Songs, albums, artists, real artwork, coral playback controls, queue navigation,
  shuffle/repeat and a persistent mini player across destinations.
- Consistent discovery, selection, secure confirmation, QR, measured progress,
  transfer history, settings, onboarding, permission, empty and error UI.

The Nearby transfer engine, package identity, preferences, SAF grants, APK export
and MediaSessionService are retained. History continues to show completed files
from the current session; this release does not invent failed or persisted history.

CI runs unit tests, Android Lint and real MainActivity Compose tests on Android 16,
including English/Arabic Storage, all home categories, theme switching, recreation,
rotation, music queue and playback while navigating/backgrounded. A second pass
covers a smaller display and increased font size. The APK is published only after
these checks and comparison against the previous release's signing certificate.

No physical-device test is claimed. Two-device radio transfer, real QR scanning,
OEM lock-screen UI, Bluetooth/headset integration and large real media collections
still need physical-device validation. Playlists/favorites are not exposed as
unsupported tabs. Android/Play Services own system pickers and QR scanner UI.
