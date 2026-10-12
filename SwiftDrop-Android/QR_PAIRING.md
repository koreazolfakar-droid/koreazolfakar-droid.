# SwiftDrop QR pairing (alpha)

1. Receiver taps Receive. A fresh random 128-bit QR session hint appears after Nearby advertising starts.
2. Sender selects files and taps Scan receiver QR. Google Code Scanner scans a QR code, supports automatic zoom, and returns only the decoded text to SwiftDrop. No CAMERA permission is requested by the app.
3. The sender verifies a strict `swiftdrop://pair/v1/<32 lowercase hex>` format, and discovers only the matching advertising device. QR contents are never executed or opened as an external URL.
4. BOTH devices must still compare and accept the Nearby authentication digits. Never bypass this step. Once accepted, the existing transfer engine handles payloads unchanged.

QR is NOT a replacement for Nearby discovery and does NOT bypass Google Play Services device-permission requirements (including Android 16 issues 8034 / 8036). The first scanner launch may download a Google Play Services module and require internet. QR images are generated offline with ZXing. Sessions are rotated when Receive restarts and invalidated when receiving stops.

Test on two physical Android phones, including scan in dark/low light, autozoom from a distance, mismatched QR, retry, denial/revocation, and interrupted transfer. CI tests and APK build alone do not validate camera or device-to-device connectivity.
