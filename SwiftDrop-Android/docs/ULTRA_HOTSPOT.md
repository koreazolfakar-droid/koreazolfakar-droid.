# SwiftDrop Ultra — Local-only Hotspot foundation (not a release)

## Phase 1 implemented in this branch

- Separate user-initiated Android LocalOnlyHotspot lifecycle (Android 8 / API 26+).
- Actual OS-generated SSID and passphrase are shown only while the local hotspot reservation is active.
- Cancel and stop release the reservation and clear credentials; late callbacks are ignored.
- Starting Ultra stops Nearby advertising to avoid pretending the two paths are linked.
- Returning to Nearby stops the hotspot and restores the existing encrypted Nearby receive mode.
- Arabic/English preview UI, no GPS coordinate collection, no connectivity setting changes.
- No APK version change or changes to the existing transfer protocol.

**Not completed:** this phase creates a local Wi-Fi access point only. The app cannot send files over that access point yet. Do not label it completed, fast, encrypted end-to-end, 5/6 GHz, or release it to end users.

## Phase 2 — required before release

1. Add `android.permission.INTERNET` (Android LAN sockets also require it); retain offline-only architecture.
2. Implement authenticated encrypted socket transport over Wi-Fi LAN, with per-session key agreement, peer confirmation and explicit file acceptance.
3. Android 10+ joining flow: `WifiNetworkSpecifier` + `ConnectivityManager.requestNetwork` with user consent; bind the transfer sockets to the granted `Network`, not the global internet network.
4. Receiver supplies the actual endpoint address, ephemeral session QR and port. Do not hardcode gateway IPs or build unauthenticated open servers.
5. Bounded streaming memory, verified SHA-256 per file, atomic rename/publish, cancellation, timeouts, connection loss, cleanup, safe filename and disk quota guards.
6. Expose actual throughput and ETA; avoid unsupported 5/6 GHz promises or simulated progress.
7. Test on **two physical Android phones** under normal Wi-Fi, hotspot, no-internet, battery saver, screen-off, API 26–36 and interruptions. Compare checksums and measured MB/s.

## Risk and lifecycle

`LocalOnlyHotspot` may fail on an OEM phone if internet tethering is already enabled, the radio is busy, location/Wi-Fi permissions are missing, or the OS blocks access. Android can revoke a reservation asynchronously. The interface keeps Nearby as a fallback, never assumes the AP is available, and never publishes this as working Ultra transfer.

See Android's official Local-only Hotspot guidance for permission and lifecycle rules.
