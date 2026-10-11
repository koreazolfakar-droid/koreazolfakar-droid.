# SwiftDrop Roadmap

## 0.1.0-alpha (source baseline) — current
- Jetpack Compose Android interface, send/receive, nearby device list.
- `P2P_POINT_TO_POINT` Nearby Connections, consent via matching authentication code.
- Files from Android's document picker, individual file progress, Downloads/SwiftDrop, in-memory history.
- Local dark theme switch; no advertising or backend.

## 0.2 — device-verified quality
- Complete Android 8–16 test matrix; stable foreground service, notification and lifecycle state persistence.
- Multiple file/folder UX with totals, cancellation, saved history and intelligent filenames.
- File metadata limits, SHA-256 after transfer, transfer acceptance and retry controls.
- RTL and Arabic translation, accessibility and dark theme persistence.

## 0.3 — ultra-fast transfer
- Add pluggable native Wi-Fi Direct / local-network transport for phones without Google Play Services.
- Detect available 5/6/6E/7 hardware/network capabilities; Android does not let the app force Wi-Fi 7 MLO.
- Throughput benchmarks over controlled 1/10/30GB real-phone transfers, adaptive buffering, profiling.
- Resume and integrity checking with segmented transfer protocol and verified manifest.

## 0.4 — expanded devices
- QR-based pairing, encrypted authenticated session independent from Nearby, desktop app Windows.
- iOS only after researching transport restrictions and confirming interoperability.

## Ground rules
- Verify each feature on real devices before marking it done.
- Never claim a throughput number without measuring bytes/time on devices.
- Preserve existing working features while iterating.
