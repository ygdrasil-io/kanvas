---
id: "KFONT-M10-009"
title: "Implement emoji sequence planner"
status: "proposed"
milestone: "M10"
priority: "P0"
owner_area: "color"
claim_impact: "tracked-gap"
depends_on: ["KFONT-M5-001", "KFONT-M6-001", "KFONT-M7-001", "KFONT-M10-001", "KFONT-M10-006"]
legacy_gate: ["scaledemoji"]
---

# KFONT-M10-009 - Implement emoji sequence planner

## PM Note

Ce ticket rend les séquences emoji traçables avant de promettre leur rendu couleur.

## Problem

Emoji support crosses Unicode data, shaping, fallback, and glyph representation selection. The gap is an `EmojiRouteTrace` that records variation selectors, ZWJ sequences, keycaps, flags, skin-tone modifiers, emoji-capable fallback faces, and the selected color/bitmap/SVG/outline route. Without it, `scaledemoji` can fail as a vague emoji blocker instead of a precise sequence, fallback, or color-glyph diagnostic.

## Scope

- Consume pinned Unicode emoji property data from M5 and shaped sequence facts from M6.
- Plan emoji routes for VS15/VS16, ZWJ sequences, keycaps, regional-indicator flags, skin-tone modifiers, and gender/role ZWJ sequences where represented.
- Resolve emoji-capable fallback family preference through M7 fallback facts.
- Dispatch each accepted sequence to COLR, PNG bitmap, SVG, or monochrome outline routes according to the color glyph policy.
- Emit `emoji-route-trace.json` with source cluster range, sequence kind, selected typeface, representation route, fallback attempts, and diagnostics.

## Non-Goals

- Do not implement Unicode segmentation, bidi, or GSUB/GPOS shaping in this ticket.
- Do not render emoji glyphs or create GPU resources.
- Do not use platform emoji engines as normative fallback.
- Do not claim unsupported emoji sequences as monochrome fallback without diagnostics.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/05-color-fonts-bitmap-svg-emoji.md`
- `.upstream/specs/pure-kotlin-text/02-opentype-layout-shaping-engine.md`
- `.upstream/specs/pure-kotlin-text/07-validation-conformance-and-drift.md`
- `.upstream/specs/pure-kotlin-text/09-migration-from-current-font-pack.md`

## Design Sketch

```kotlin
data class EmojiRouteTrace(
    val clusterRange: TextRange,
    val sequence: EmojiSequence,
    val unicodeVersion: UnicodeVersion,
    val fallbackAttempts: List<EmojiFallbackAttempt>,
    val selectedTypeface: TypefaceID?,
    val selectedRoute: GlyphRepresentationRoute,
    val colorGlyphPlanRef: ColorGlyphPlanRef?,
    val diagnostics: List<TextDiagnostic>,
)

sealed interface EmojiSequence {
    data class VariationSelector(val base: CodePoint, val selector: CodePoint) : EmojiSequence
    data class ZWJ(val codepoints: List<CodePoint>) : EmojiSequence
    data class Keycap(val base: CodePoint) : EmojiSequence
    data class Flag(val regionalIndicators: Pair<CodePoint, CodePoint>) : EmojiSequence
    data class SkinTone(val base: CodePoint, val modifier: CodePoint) : EmojiSequence
}
```

## Acceptance Criteria

- [ ] Fixtures cover VS15/VS16, skin tone, ZWJ family sequence, keycap, flag, missing emoji-capable fallback, and color glyph unavailable.
- [ ] Each trace records Unicode version, source cluster range, fallback attempts, selected typeface, selected representation, and diagnostics.
- [ ] Unsupported sequences emit `text.emoji.sequence-unsupported` with the cluster range and codepoint list.
- [ ] Missing emoji fallback emits `text.emoji.fallback-unavailable`; unavailable color glyph emits `text.emoji.color-glyph-unavailable`.
- [ ] Monochrome fallback is cluster-safe and visible in the route trace.

## Required Evidence

- `emoji-route-trace.json` fixtures for variation selector, skin tone, ZWJ family, keycap, flag, and fallback-unavailable cases.
- `color-glyph-plan.json` or `bitmap-glyph-plan.json` refs for accepted color/bitmap routes.
- Diagnostic snapshots for sequence unsupported, fallback unavailable, and color glyph unavailable.

## Fallback / Refusal Behavior

- Unsupported emoji sequences refuse the sequence route rather than splitting the cluster into unrelated glyphs.
- Monochrome fallback is allowed only when valid for the entire cluster and recorded in `EmojiRouteTrace`.
- Legacy gate `scaledemoji` remains open until sequence planning, fallback, glyph representation, and dashboard evidence are linked.

## Dashboard Impact

- Expected row: `Emoji sequence planner / scaledemoji`.
- Expected classification: `tracked-gap`.
- Claim promotion allowed: no, unless route traces and fallback diagnostics are attached.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:glyph:test --tests '*Emoji*'
```

## Status Notes

- `proposed`: Bridges Unicode emoji data, fallback, shaping output, and color glyph dispatch.
- Move to `ready` only after sequence kinds, fallback trace fields, and diagnostics are reviewed.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M10`
- `area:color`
- `claim:tracked-gap`
- `legacy:scaledemoji`
