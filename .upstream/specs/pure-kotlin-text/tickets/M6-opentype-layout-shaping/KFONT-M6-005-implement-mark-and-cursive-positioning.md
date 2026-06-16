---
id: "KFONT-M6-005"
title: "Implement mark and cursive positioning"
status: "proposed"
milestone: "M6"
priority: "P0"
owner_area: "shaping"
claim_impact: "tracked-gap"
depends_on: ["KFONT-M6-004", "KFONT-M2-003"]
legacy_gate: null
---

# KFONT-M6-005 - Implement mark and cursive positioning

## PM Note

Ce ticket positionne correctement les marques et attaches cursives, sans quoi Arabic, Hebrew et Devanagari restent visuellement faux.

## Problem

Required script rows depend on GPOS mark-to-base, mark-to-ligature, mark-to-mark, and cursive attachment behavior. Without anchors, GDEF glyph classes, mark attachment classes, and attachment traces, Kanvas cannot claim complex script shaping even if glyph substitutions work.

## Scope

- Implement GPOS LookupType 3 cursive attachment and LookupTypes 4, 5, and 6 mark positioning.
- Consume GDEF glyph classes, mark attachment classes, and ligature caret facts when required by a lookup.
- Resolve anchor formats needed by target fixtures, including x/y coordinates and device/variation placeholders that later tickets can fill.
- Emit `gpos-trace.json` events for base/mark/ligature glyph selection, anchor resolution, attachment vector, cursive entry/exit anchors, and diagnostics.
- Preserve cluster mappings and glyph identities through mark and cursive positioning.

## Non-Goals

- Do not implement device tables, variation adjustments, or extension positioning; KFONT-M6-010 owns those.
- Do not implement script-specific syllable or joining policy; fixture tickets and feature policy own script behavior.
- Do not synthesize anchors when GPOS/GDEF data is missing.
- Do not render glyphs or evaluate visual images in this ticket.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/02-opentype-layout-shaping-engine.md`
- `.upstream/specs/pure-kotlin-text/07-validation-conformance-and-drift.md`

## Design Sketch

```kotlin
data class OpenTypeAnchor(
    val format: AnchorFormat,
    val x: FontUnit,
    val y: FontUnit,
    val pointIndex: Int?,
    val variationDevice: VariationDeviceRef?,
)

data class MarkAttachment(
    val markGlyph: GlyphId,
    val baseGlyph: GlyphId,
    val markClass: Int,
    val markAnchor: OpenTypeAnchor,
    val baseAnchor: OpenTypeAnchor,
    val adjustment: GposValueRecord,
)

data class CursiveAttachment(
    val previousGlyph: GlyphId,
    val currentGlyph: GlyphId,
    val exitAnchor: OpenTypeAnchor,
    val entryAnchor: OpenTypeAnchor,
    val adjustment: GposValueRecord,
)
```

## Acceptance Criteria

- [ ] Mark-to-base fixture positions a combining mark using GDEF class and anchor data.
- [ ] Mark-to-ligature fixture attaches marks to the correct ligature component.
- [ ] Mark-to-mark fixture stacks two marks deterministically.
- [ ] Cursive fixture applies entry/exit anchors and records attachment chain order.
- [ ] Missing required GDEF or malformed anchor data emits `text.shaping.gdef-required`, `text.shaping.mark-positioning-unavailable`, or `text.shaping.cursive-attachment-unavailable` as appropriate.

## Required Evidence

- `gpos-trace.json` with anchor formats, glyph classes, mark classes, ligature component index, cursive chain links, attachment vectors, and diagnostics.
- `shaped-glyph-run.json` showing final mark offsets, cursive advances, cluster mappings, and run direction.
- Fixtures: `gpos-mark-to-base.otf`, `gpos-mark-to-ligature.otf`, `gpos-mark-to-mark.otf`, `gpos-cursive-attachment.otf`, `gpos-missing-gdef.otf`, `gpos-anchor-malformed.otf`.
- Diagnostics asserted in tests: `text.shaping.gdef-required`, `text.shaping.mark-positioning-unavailable`, `text.shaping.cursive-attachment-unavailable`, `text.shaping.lookup-malformed`.

## Fallback / Refusal Behavior

- Unsupported or malformed paths must emit one of: `text.shaping.gdef-required`, `text.shaping.mark-positioning-unsupported`.
- The diagnostic must name the affected range, glyph, cluster, lookup, font source, or route object when that subject exists.
- Silent fallback to platform/native/font engine behavior is not allowed; the ticket remains `tracked-gap` until the listed evidence and validation pass.

## Dashboard Impact

- Expected row: `Implement mark and cursive positioning`.
- Expected classification: `tracked-gap`.
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:text:test --tests '*GposMark*' --tests '*Cursive*'
```

## Status Notes

- `proposed`: Builds on base GPOS value application from KFONT-M6-004.
- Current blocker audit (2026-06-16): draft PR `#1705` (`KFONT-M6-004`) is still open, and the required fixture set `gpos-mark-to-base.otf`, `gpos-mark-to-ligature.otf`, `gpos-mark-to-mark.otf`, `gpos-cursive-attachment.otf`, `gpos-missing-gdef.otf`, and `gpos-anchor-malformed.otf` is not present in-repo. Do not bypass these gates with synthetic-only mark/cursive coverage; remaining gate is merge/adopt the bounded GPOS base plus add reviewed fixture provenance and anchor/GDEF refusal evidence.
- Move to `ready` only after required anchor formats and GDEF diagnostics are reviewed.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M6`
- `area:shaping`
- `claim:tracked-gap`
