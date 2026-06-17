---
id: "KFONT-M6-005"
title: "Implement mark and cursive positioning"
status: "done"
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
- Do not claim ambiguous multi-component ligature-component resolution without dedicated component mapping evidence; those cases must stay on explicit refusal diagnostics.
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

- [x] Mark-to-base fixture positions a combining mark using GDEF class and anchor data.
- [x] Mark-to-ligature evidence stays bounded: unique matches may attach, while ambiguous reviewed fixture matches refuse deterministically with `text.shaping.mark-positioning-unavailable`.
- [x] Mark-to-mark fixture stacks two marks deterministically.
- [x] Cursive fixture applies entry/exit anchors and records attachment chain order.
- [x] Missing required GDEF or malformed anchor data emits `text.shaping.gdef-required`, `text.shaping.mark-positioning-unavailable`, or `text.shaping.cursive-attachment-unavailable` as appropriate.

## Required Evidence

- `gpos-trace.json` with anchor formats, glyph classes, mark classes, cursive chain links, and diagnostics; include ligature component indexes and attachment vectors only when a reviewed mark-to-ligature case proves a unique runtime component choice.
- `shaped-glyph-run.json` showing final mark offsets, cursive advances, cluster mappings, and run direction.
- Fixtures: `gpos-mark-to-base.otf`, `gpos-mark-to-ligature.otf`, `gpos-mark-to-mark.otf`, `gpos-cursive-attachment.otf`, `gpos-missing-gdef.otf`, `gpos-anchor-malformed.otf`.
- Diagnostics asserted in tests: `text.shaping.gdef-required`, `text.shaping.mark-positioning-unavailable`, `text.shaping.cursive-attachment-unavailable`, `text.shaping.lookup-malformed`.

## Fallback / Refusal Behavior

- Unsupported or malformed paths must emit one of: `text.shaping.gdef-required`, `text.shaping.mark-positioning-unavailable`.
- Ambiguous mark-to-ligature matches that expose multiple component indexes without a unique runtime choice must emit `text.shaping.mark-positioning-unavailable` instead of silently choosing a component.
- The diagnostic must name the affected range, glyph, cluster, lookup, font source, or route object when that subject exists.
- Silent fallback to platform/native/font engine behavior is not allowed; the ticket remains `tracked-gap` until the listed evidence and validation pass.

## Dashboard Impact

- Expected row: `Implement mark and cursive positioning`.
- Expected classification: `tracked-gap`.
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:sfnt:test --tests org.graphiks.kanvas.font.sfnt.SFNTSurfaceTest.defaultOpenTypeFaceParserLoadsReviewedMarkAndCursiveGposFixtureFontsFromRepo --tests org.graphiks.kanvas.font.sfnt.SFNTSurfaceTest.defaultOpenTypeFaceParserPreservesMissingGdefAndMalformedAnchorFixtureFacts
rtk ./gradlew --no-daemon :font:text:test --tests org.graphiks.kanvas.text.TextStackSurfaceTest.gposTraceGoldenPinsFixtureBackedLatinCasesAndMalformedDiagnostics --tests org.graphiks.kanvas.text.TextStackSurfaceTest.shapedGlyphRunGoldenPinsFixtureBackedGsubAndGposRuns --tests org.graphiks.kanvas.text.TextStackSurfaceTest.arabicSeedReadinessGoldenPinsDiagnosticsWithoutSupportClaim --tests org.graphiks.kanvas.text.TextStackSurfaceTest.basicOpenTypeShapingEngineAppliesReviewedMarkAndCursiveFixtureFontsFromRepo --tests org.graphiks.kanvas.text.TextStackSurfaceTest.basicOpenTypeShapingEngineReportsReviewedMarkAndCursiveFixtureDiagnosticsFromRepo --tests org.graphiks.kanvas.text.TextStackSurfaceTest.shapingKeepsReviewedGsubClustersWhenTypefaceHasUnmatchedMarkLookups --tests org.graphiks.kanvas.text.TextStackSurfaceTest.basicOpenTypeShapingEngineRefusesAmbiguousLigatureComponentAttachments --tests org.graphiks.kanvas.text.TextStackSurfaceTest.basicOpenTypeShapingEngineRefusesAmbiguousSingleCodePointLigatureComponentAttachments --tests org.graphiks.kanvas.text.TextStackSurfaceTest.basicOpenTypeShapingEngineDoesNotReportUnavailableWhenCursiveMatchHasZeroAdvanceDelta --tests org.graphiks.kanvas.text.TextStackSurfaceTest.basicOpenTypeShapingEngineReportsRtlCursiveAttachmentFailuresWithLogicalTextRange
rtk run "python3 scripts/validate_font_fixture_assets.py && python3 scripts/validate_pure_kotlin_text_fixture_manifest.py && python3 scripts/validate_pure_kotlin_text_dump_index.py"
```

## Status Notes

- `review`: parser/runtime slices, checked-in fixture provenance, fresh focused validations, and dump updates for `gpos-trace.json` / `shaped-glyph-run.json` are now in place for bounded mark/cursive positioning.
- 2026-06-17 closeout: the required fixture set `gpos-mark-to-base.otf`, `gpos-mark-to-ligature.otf`, `gpos-mark-to-mark.otf`, `gpos-cursive-attachment.otf`, `gpos-missing-gdef.otf`, and `gpos-anchor-malformed.otf` is now present in-repo with reviewed provenance and deterministic parser/runtime assertions.
- `done`: independent review surfaced bounded runtime and evidence gaps, now fixed with fresh regression coverage for ambiguous ligature-component refusal, ambiguous mono-codepoint reviewed-fixture refusal, RTL cursive logical ranges, zero-advance cursive matches, and GSUB cluster preservation under mark/cursive-capable typefaces. Fresh parser/runtime/manifest validations are green, and the remaining non-claims stay explicit.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M6`
- `area:shaping`
- `claim:tracked-gap`
