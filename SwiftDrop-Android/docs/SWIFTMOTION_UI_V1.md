# SwiftMotion UI — original SwiftDrop design language (v1)

## Product identity
SwiftMotion is designed for SwiftDrop. Its recognisable element is the **signal path**:
a single ring that searches for a device when progress is unknown and becomes a
measured arc when a transfer starts. This is not a Samsung/One UI, SHAREit or Quick
Share clone. No Android component is asked to imply a capability that the transport
does not actually provide.

## Palette
- **Signal**: #006C5B in light mode; #81F0D1 in dark mode. Primary actions and live progress.
- **Spark**: #A6472F in light mode; #FFB096 in dark mode. Secondary send/receive distinction.
- **Lime**: #526B0D / #D2EB87. Successful completion.
- **Ink**: #152326 / #081317. Core text and dark canvas.
- Soft neutral surfaces use green-tinted grays, rather than a blue-tinted system palette.
- Meaningful states remain theme-aware: success=tertiary, failures=error; never use colour alone to communicate outcome.

## Component and interaction rules
1. **Orbit**: when fraction is known, render actual 0..1 progress; when unknown and
   discover/receive/connect is active, animate an indeterminate arc. Do not invent
   transfer speed, time or file completion.
2. **Reduced motion**: stop perpetual orbit animation if the Android animator
   duration scale is 0. Static status, readable labels, and actions remain.
3. **Action surfaces**: large corner radius, strong send and receive distinction,
   at least 48dp touch targets.
4. **Navigation dock**: keep routes, state and tab accessibility semantics intact.
5. **Transfer footer**: cancellation/done remains reachable even on long queues.
6. **Consistency**: all screens take colors, shapes and typography from MaterialTheme.
   The underlying components are reused; behaviour, protocols, package, keys and
   existing permissions are unchanged.

## First implementation scope
- Shared app-wide light/dark palette and shape tokens.
- Original adaptive SignalOrbit component.
- Home send/receive action surfaces.
- Nearby discovery and receive live signal hero, connected-peer transitions.
- Nearby transfer progress/status hero.
- Ultra Hotspot transfer progress/status hero and persistent bottom action.
- Three-tab navigation dock.

## Next refinement batch (not yet implemented)
- File browser and media selection: more coherent preview/selection affordances,
  robust large-font/RTL states and per-item selection motion.
- Connection consent and pairing QR: same visual states, preserve mutual approval.
- History, onboarding, permissions, settings and empty/error states: further
  page-level refinements beyond inherited tokens.
- Accessibility QA: 200% font, RTL, TalkBack, reduced motion, small devices,
  tablets, dark/light, contrast, touch targets.
- Performance QA: instrument Compose recomposition/frame timings and verify
  transfer + animated UI together on a Samsung A56 and a second OEM device.

## Release gate
Changes live on a dedicated branch. **No APK has been assembled or published here.**
Run `gradle --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
from `SwiftDrop-Android` with JDK 17 and Android SDK 36. Run instrumented
UI tests, two-device transfer tests and regression checks before merge.
Confirm version/signing identity and never trigger auto-release merely for
a visual review. No engine, transport or permissions were changed by this batch.

## Second refinement batch (2026-10-31)
- Refined file/media selection bar for narrow widths and 140%+ font scale.
- Document tree and file-picker actions adapt from two columns to stacked buttons.
- Photo/video/music selection receives consistent shapes and subtle state transitions.
- History overview presents total transferred bytes and count responsively.
- Settings disclosure, theme/language cards and header were refreshed without losing Back.
- Onboarding, permission recovery and receive pairing QR share SwiftMotion surfaces.
- Ultra hotspot invitation card and file review summary now align with the wider UI.
- The pairing QR deliberately retains its white quiet zone and session-local encoding.

Current verification remains **source-only**: no Gradle, instrumentation, or physical
device validation has run. No release or merge is authorized based on source checks
alone. No transfer protocol, file persistence, activity result ownership, version,
signing or permission changes are part of this work.

## Third refinement (2026-10-31): canonical DESIGN.md

- New `SwiftDrop-Android/DESIGN.md` is the primary product-specific visual contract.
  Expo/Linear reference studies informed content hierarchy and understated
  surface layering, **not brand colors or layouts**.
- Semantic `Signal`, `Spark`, `Ink` roles added with aliases for older screen code.
- `swiftMotionDurationMillis()` derives from one lifecycle-aware app preference,
  with no per-frame settings lookups; reduced motion now covers dock transitions,
  press effects, settings and media selection as well as live transfer progress.
- Primary/secondary action palette has white text on dark enough fills. New
  `SwiftMotionDesignContractTest` checks action contrast and touch-target tokens.
- History items and empty records, section headings at large font, dock labels,
  transfer speed/ETA layout and SignalOrbit progress semantics were improved.
- Validation performed: static source/diff review and independent contrast
  calculation. Kotlin/JUnit tests, Gradle build, instrumentation and hardware
  transfer were **not** executed, and the draft must not be merged yet.

