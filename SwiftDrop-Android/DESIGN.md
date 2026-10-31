# SwiftDrop DESIGN.md — SwiftMotion UI 1.1

> Product-specific design contract for the existing **native Kotlin / Jetpack Compose** app.
> SwiftDrop is not a re-skin of One UI, SHAREit, Quick Share or any other product.
> This file is normative for new and modified SwiftDrop screens.

## Design intention

**Move data without making people think about the network.** The interface shows
only what the actual transport knows. SwiftMotion's original visual signature
is a **signal path**: an orbit while devices are being discovered or a transfer
has no measurable total, transitioning to a measured arc once bytes/total are
known. A complete arc is never shown merely because the animation finished.

We extracted *principles*, not visual assets, from two static studies in
Awesome DESIGN.md: Expo (clear information hierarchy, restrained spacing) and
Linear (quiet layered surfaces and focused emphasis). Neither site's colors,
logos, copy, composition, proprietary typefaces or branding are used here.
SwiftMotion's own contrasting signal/spark/ink system remains authoritative.

## Brand palette: semantic names, not one-off hex literals

| Role | Light | Dark | Usage |
| --- | --- | --- | --- |
| Signal | `#006C5B` | `#81F0D1` | Primary action, live path, selected state |
| Spark | `#A6472F` | `#FFB096` | Receive action and secondary accent |
| Confirmation | `#526B0D` | `#D2EB87` | Completed action only |
| Ink | `#152326` | `#E7F3EE` | High-priority text |
| Canvas | `#F5F8F5` | `#081317` | App background |
| Card | `#FFFFFF` | `#112125` | Interactive or informational surface |
| Card high | `#E8F0EB` | `#1D3033` | Emphasised card, nested surface |
| Error | `#B32635` | `#FFB1B8` | Failure, disconnect warning |

Use `MaterialTheme.colorScheme` roles mapped by `ui/theme/SwiftDropTheme.kt`.
Avoid hardcoded accent values in screens. The QR bitmap must remain black on
pure white, with a white quiet zone even in dark mode, to remain scannable.
File-type badges may use their existing distinctive colors, but those colors
must not replace semantic state indicators.

## Layout scale and visual hierarchy

* Baseline grid: **4 dp**. Gaps: 4 / 8 / 12 / 16 / 24 / 32 dp.
* Screen side inset: **18 dp** on handsets; avoid invisible edge affordances.
* Minimum touch target: **48 × 48 dp**. Prefer **52–56 dp** for primary actions.
* Content priorities: current task and status → next action → technical detail.
* Radii: 10 dp (small primitives), 16 dp (small cards), 22 dp (medium panels),
  28 dp (main cards), 32 dp (emphasis surfaces); not every row needs a giant pill.
* Depth comes from three differentiated surfaces and subtle dividers, not giant
  drop shadows, random gradients or decorative glass blurs.
* Use a maximum sensible content width where appropriate on tablets. Do not
  stretch a 160 dp progress indicator to fill a landscape screen.

### Typography

* Use accessible system sans: no added proprietary typeface or internet font dependency.
* Display 30–44sp / medium or semibold; headline 23–30sp / semibold;
  title 14–20sp / medium; body 14–16sp / normal; labels 11–14sp.
* Respect Android `fontScale` and bidirectional Arabic. Do not hardcode
  uppercase English metadata or truncate action labels unnecessarily.
* Long file names may ellipsize in rows, but the final transfer details must
  still expose the full name to accessibility APIs.

### Responsive breakpoints

* Phone with compact available width or `fontScale >= 1.4`: stack peer
  actions and selection controls vertically.
* Standard phone: share action cards can be side-by-side when both labels fit.
* Tablet: preserve readable column widths and avoid an oversized empty hero.
* Respect system insets, cutouts, navigation bars and keyboard.
* Test landscape, 200% font, Arabic RTL and the user changing theme at runtime.

## Motion grammar

| Intent | Recommended duration | Interaction |
| --- | --- | --- |
| Tap / press confirmation | 120–160 ms | Gentle scale or color transition |
| Selection change | 180–240 ms | Contrast and checkmark; preserve state |
| Panel expansion | 240–300 ms | Resize content, do not move primary CTA off-screen |
| Discovery | 2300 ms cycle | One quiet orbit **only while active** |
| Transfer fraction | 180–240 ms | Smooth actual received fraction, never invent bytes |

Respect Android's animator duration setting. When animations are disabled,
render the final static state immediately; do not spin, shimmer, or show false
motion. Avoid infinite effects while idle or in the background. Prefer Compose
state-driven rendering to a per-frame timer or constantly updating a ViewModel.
The ring must not trigger new media queries or transfer calculation.

## Components and interaction contracts

**SignalOrbit**: reading `null` fraction with active discovery produces an
indeterminate arc; a known fraction in `0f..1f` paints only that fraction.
Complete/error are semantic and include visible text, not only color.

**Home actions**: send and receive must stay obviously different. Tapping Send
opens the existing picker, and Receive uses the existing discover/advertise flow.
Current-session resume remains reachable and does not duplicate a transfer.

**Device selection**: show actual discovered peers, prevent duplicate connects
during pending handshake, and keep cancel/retry/error recovery. Do not animate a
fictitious device or say “secure” before mutual approval completes.

**Selected files**: show a truthful count and size when known. Keep Clear,
Choose Again, QR, Ultra QR and Send actions. At large font sizes, stack
buttons instead of squeezing labels.

**Live transfer**: show current filename, actual bytes / total, speed and ETA
only if measured, number of files and saving state, and a pinned Cancel/Done
control. Complete is **after data publication**, not after UI reaches 100%.
Never restart progress animation in a way that implies a second transfer.
No Pause action unless the transport really supports Pause.

**File browser / gallery**: maintain scoped-storage and SAF permission model,
pagination, media previews, installed app export and real selection. Loading,
permission denied, empty, search-no-match and load-error states must be distinct.

**History**: search and filter local records; show the actual file count and
total bytes; do not imply an audit/completion status not in the history model.

**QR**: preserve existing payload and authentication, white square code and
scanning contrast. Never log or upload invitation credentials. Keep device
pairing confirmation legible.

**Settings and onboarding**: every CTA remains connected to its original action.
Keep the Back route, Light/Dark/System, Arabic/English/System, and skip. No
false claims about transport security or speed.

## Quality gates

Before marking this design ready, run Android unit tests, Lint, debug compilation,
Compose instrumentation tests, and two-device Nearby + Ultra smoke tests.
Check TalkBack focus order, 200% text scale, theme contrast and reduced motion.
Measure actual frame time during a real large-file transfer on a midrange device;
60fps is a *target*, not a verified claim.

**Never** change transfer protocol, signing, permissions or APK identity as part
of styling work. Never merge a draft PR or publish a signed APK solely because
the UI screenshots look good. Verify the real device flows first.
