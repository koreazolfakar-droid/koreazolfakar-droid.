# SwiftDrop DESIGN.md — Graphite v2

> Native Kotlin / Jetpack Compose UI contract. A direct revision after the user
> rejected the oversized mint-and-peach SwiftMotion v1 layout. The goal is a
> recognisable, premium transfer utility — not a clone of Samsung, SHAREit,
> Revolut, or any other application.

## Direction

**Product-first, not decoration-first.** The actual tasks — Send, Receive,
Browse and Resume — should be recognizable immediately without giant colored
tiles. Use the steady, high-contrast content hierarchy observed in the static
Awesome DESIGN.md *Revolut* study as a principle. Do not copy its purple
identity, brand assets, copy, proprietary font or marketing layouts.

What was wrong with v1 from the supplied screenshot:
- Giant saturated pastel Send/Receive cards took up screen space.
- 28–32dp radii made every surface look like a floating bubble.
- Six large boxes made file categories resemble a toy launcher.
- Oversized rounded bottom navigation obscured the next content section at rest.
- Many large surfaces had equal visual weight, burying the actual next action.

## Visual tokens

| Role | Light | Dark | Purpose |
| --- | --- | --- | --- |
| Focused blue | `#315ED1` | `#ABC2FF` | Small active indicators, icons, progress, CTA |
| Neutral secondary | `#4E5B70` | `#BBC7D9` | Secondary controls; no peach |
| Background | `#F5F7FB` | `#0B1019` | Readable non-ornamental canvas |
| Surface | `#FFFFFF` | `#151C28` | Cards and interactive controls |
| Primary ink | `#172231` | `#EFF2F8` | Titles and content |
| Success | `#267452` | `#79D3A7` | Actual transfer complete only |
| Error | `#AD2A40` | `#FFAEBB` | Actionable errors |

Blue is an accent, not an entire screen of blue. File-type badges may retain
limited semantic colors (music/media/apps) to help recognition. Never use
background color alone to communicate transfer success or failure.

## Geometry & information density

- 4dp baseline. Screen insets 18dp, gaps typically 8–12dp.
- Radii: 6 / 10 / 14 / 18 / 22dp, increasing with surface role.
- Default Home viewport should prioritize header → device state → two actions →
  QR option → six file destinations → recent activity.
- Header and device row: compact, not a billboard.
- Send/Receive: 116dp high at regular font scales, 94dp minimum when stacked;
  supporting text may wrap, and dynamic font scale expands the content.
- File categories: 75dp minimum with quiet icons. Stay 3 across when readable,
  2 across at compact width/large font.
- Navigation: fixed full-width surface, thin top divider, no large pill.
  Selected state is an icon tint and a small 3dp indicator. Preserve 48dp+
  touch targets, original 3 destinations, native navigation bar insets.
- Home and detail content must scroll under a properly measured Scaffold
  bottom bar; never let bottom content be unreachable.
- Use a light hierarchy of 14–28sp text; keep Android font scaling and RTL.

## Interaction semantics

- Send opens the exact existing file browser; Receive uses current Nearby flow.
- QR tile opens the existing choose-files-first flow, not an unrelated scan route.
- Current-session resume, error feedback and device diagnostics stay functional.
- History and Settings routes remain distinct. Do not add fake shortcuts.
- The live transfer orbit displays actual progress or an indeterminate search;
  never fake speed, a completed transfer or secure pairing.
- Do not modify request permission ownership, SAF storage, music background
  playback, app export, transfer engine, encryption, or local storage.
- Keep QR code's white quiet zone in every theme.
- Respect reduced-motion preferences; avoid unnecessary shimmer and movement.
- Keep accessibility labels, high contrast, tab semantics and TalkBack progress.

## QA / publication

The design lives on a dedicated branch until it compiles and can be viewed
on a device at standard and 200% font size, Arabic/English, light/dark and
with gesture navigation. Check that the Recent section is actually scrollable.
Unit tests, Android Lint, build, and a two-phone send/receive smoke test must
pass. Maintain upgrade-compatible app signing. Never overwrite the published
APK with a differently signed file.

## Reference provenance

The Awesome DESIGN.md Revolut study is a static design analysis, not a
snapshot of its current product. Only its restrained color usage, hierarchy
and division between strong CTAs and muted informational panels informed
Graphite v2. Everything in SwiftDrop's app remains purpose-built.
