# SwiftDrop Ultra — experimental offline Wi-Fi transfer

**Branch**: `feature/swiftdrop-ultra-hotspot`. **Do not ship or merge until physical-device QA passes.**

## Implemented in this draft branch

- Receiver explicitly creates an Android `LocalOnlyHotspot`, with OS-generated network credentials. No internet tethering, no forced 5 GHz / 6 GHz / Wi-Fi 7.
- On receiver's screen, a temporary QR invitation contains SSID, password, ephemeral 256-bit transfer secret and randomly bound TCP/UDP port. Keep it private.
- Sender selects files, scans Ultra QR, and Android 10+ asks the user to join the receiver's Wi-Fi using `WifiNetworkSpecifier`. On Android 8–9 the user must join in Wi-Fi Settings first.
- UDP broadcast discovery runs only on the joined network and answers valid keyed HMAC probes. It does **not** guess the hotspot gateway.
- Local TCP session authenticates both sides with independent HMAC challenges, derives direction-specific AES-256-GCM keys, and encrypts framed file metadata and blocks.
- Receiver explicitly accepts/declines each offered file, with a timeout.
- 64-KiB bounded streaming chunks, declared-length bounds and verified SHA-256. Downloads stay hidden/pending until verification; partial data is deleted on errors.
- App displays measured byte progress and MB/s; no claimed transfer speed without benchmarking.
- Nearby Connections remains intact and serves as manual fallback. Existing file picker, music, media library, package name and signing are preserved.
- Extra `INTERNET` permission is required by Android for local TCP/UDP sockets, **not** for a cloud backend.

## Automated tests

- Android Gradle unit, manifest, Lint and build checks in PR-only `SwiftDrop Ultra Preview QA`. This is not a signed public release.
- Loopback socket-level tests for mutual authentication, key rejection, encrypted frames, exact-size streaming, offer rejection, corrupted SHA-256 rollback, and QR validation.
- CI emulators do **not** certify Android physical Wi-Fi radios or LocalOnlyHotspot networking.

## Physical device checks REQUIRED before release

1. Install this development build on two real phones (test Android 10–16; separately Android 8/9 manual join) without internet access.
2. Receiver: Receive → Create local hotspot. Confirm network SSID and QR appear.
3. Sender: pick 1 file → Ultra QR → scan the receiver code → approve Android's local Wi-Fi association dialog.
4. Check verified UDP discovery, mutual authentication, receiver file confirmation, actual byte progress, completion and SHA-256 comparison.
5. Transfer a 0-byte file, Unicode filename, many files, 2+ GiB file and large video. Include duplicate names, low disk space and source-URI revocation.
6. Disconnect in mid-transfer, deny receiving, turn off Wi-Fi/hotspot, rotate device, lock screen, revoke permissions and enable battery saver.
7. Test WPA2/WPA3-only/transition hotspot differences, client isolation, aggressive OEM background lifecycle policies, VPN/mobile-data interplay and QR expiry (hotspot reset).
8. Measure throughput on repeatable files under known hardware/radio conditions. Do not report speed claims without measurements.
9. If native LAN discovery fails, confirm stopping Ultra restores the original Nearby flow.

## Known current limitations

- Android 10+ OS network dialog is mandatory; the application may not silently connect to the hotspot.
- `WifiNetworkSpecifier` currently requests WPA2; WPA3-only hotspot association can fail on some devices.
- UDP broadcast can be blocked by OEM implementations/client isolation. No mDNS/manual-IP recovery has been shipped yet.
- Native transfer runs while the app process is alive; a dedicated foreground service for reliable screen-off/long transfers is still future work.
- Ultra has no resume or multiple parallel file streams; cancellation restarts a file.
- Physical cross-device validation is pending. Do not mistake successful CI for tested over-the-air transfer.
