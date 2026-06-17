---
id: "KFONT-M6-004"
title: "Implement GPOS single/pair positioning"
status: "review"
milestone: "M6"
priority: "P0"
owner_area: "shaping"
claim_impact: "tracked-gap"
depends_on: ["KFONT-M6-001", "KFONT-M2-003"]
legacy_gate: null
---

# KFONT-M6-004 - Implement GPOS single/pair positioning

## PM Note

Ce ticket apporte le kerning et les ajustements de position de base avec des preuves numeriques stables.

## 2026-06-16 Implementation Slice

- `font/sfnt` parse maintenant un slice borne de GPOS LookupType 1 single positioning et conserve les `ValueRecord` utiles pour LookupType 2 pair positioning formats 1 et 2.
- `font/text` applique `xPlacement`, `yPlacement` et `xAdvance` sur `BasicOpenTypeShapingEngine`, y compris les `firstValueRecord`/`secondValueRecord` des pairs.
- Les pair adjustments sont maintenant explicitement desactives quand `FeatureSet["kern"] == 0`, avec preservation du comportement legacy `kern`.
- Cette vague ne promeut aucun support claim complet GPOS: elle couvre uniquement le slice deterministe verifie par les tests de surface ci-dessous.

## Problem

Glyph IDs alone are not enough for shaped output. Kanvas must apply GPOS single adjustments and pair adjustments so advances, offsets, and kerning facts are deterministic and visible in dumps instead of being hidden in renderer behavior.

## Scope

- Implement GPOS LookupType 1 single positioning and LookupType 2 pair positioning for supported value formats.
- Support pair positioning formats 1 and 2, including pair sets, class pair records, value records, and horizontal/vertical adjustment fields required by target fixtures.
- Apply positioning in lookup order to the current glyph buffer and preserve cluster identity.
- Emit `gpos-trace.json` events with lookup index, feature tag, matched glyphs/classes, value records, before/after positions, and diagnostics.
- Refuse malformed coverage, class definitions, value formats, or out-of-range pair records with stable diagnostics.

## Non-Goals

- Do not implement cursive attachment, mark positioning, contextual positioning, extension positioning, device tables, or variation adjustments.
- Do not implement glyph rasterization, atlas placement, or GPU handoff.
- Do not infer kerning from legacy `kern` tables unless a separate parser/adapter explicitly maps it into this contract.
- Do not use platform text APIs as normative positioning behavior.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/02-opentype-layout-shaping-engine.md`
- `.upstream/specs/pure-kotlin-text/07-validation-conformance-and-drift.md`

## Design Sketch

```kotlin
class GposPositioningEngine(
    private val table: GposTable,
    private val diagnostics: MutableList<RouteDiagnostic>,
) {
    fun apply(run: PositionedGlyphBuffer, features: ResolvedFeatureSet): GposResult
}

data class GposValueRecord(
    val xPlacement: FontUnit = FontUnit.Zero,
    val yPlacement: FontUnit = FontUnit.Zero,
    val xAdvance: FontUnit = FontUnit.Zero,
    val yAdvance: FontUnit = FontUnit.Zero,
)

data class GposPairAdjustment(
    val first: GlyphId,
    val second: GlyphId,
    val firstValue: GposValueRecord,
    val secondValue: GposValueRecord,
)
```

## Acceptance Criteria

- [ ] Single positioning fixture applies x/y placement and advance adjustments and records them in `gpos-trace.json`.
- [ ] Pair positioning format 1 fixture applies a specific kerning pair.
- [ ] Pair positioning format 2 fixture applies class-based kerning and records class IDs.
- [ ] Malformed value formats, coverage tables, class definitions, and out-of-range pair records refuse with `text.shaping.lookup-malformed`.
- [ ] `shaped-glyph-run.json` records final positions and advances after GPOS.

## Required Evidence

- `gpos-trace.json` with lookup type, feature tag, coverage match, value records, class IDs, before/after positions, and diagnostics.
- `shaped-glyph-run.json` with final advances, offsets, cluster ranges, and positioning trace references.
- Fixtures: `gpos-single-adjustment.otf`, `gpos-pair-format1-kerning.otf`, `gpos-pair-format2-class.otf`, `gpos-valueformat-malformed.otf`, `gpos-pair-out-of-range.otf`.
- Diagnostics asserted in tests: `text.shaping.lookup-malformed`, `text.shaping.lookup-type-unsupported`, `text.shaping.cluster-invariant-failed`.

## Fallback / Refusal Behavior

- Unsupported or malformed paths must emit one of: `text.shaping.gpos-value-malformed`, `text.shaping.pair-positioning-unsupported`.
- The diagnostic must name the affected range, glyph, cluster, lookup, font source, or route object when that subject exists.
- Silent fallback to platform/native/font engine behavior is not allowed; the ticket remains `tracked-gap` until the listed evidence and validation pass.

## Dashboard Impact

- Expected row: `Implement GPOS single/pair positioning`.
- Expected classification: `tracked-gap`.
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:sfnt:test --tests org.graphiks.kanvas.font.sfnt.SFNTSurfaceTest
rtk ./gradlew --no-daemon :font:text:test --tests org.graphiks.kanvas.text.TextStackSurfaceTest
rtk ./gradlew --no-daemon :font:text:test --tests '*GposPair*' --tests '*Kerning*'
```

## Status Notes

- `proposed`: Base GPOS positioning depends on shaping contract and parsed table facts.
- `review`: bounded single/pair positioning parser and `BasicOpenTypeShapingEngine` application are implemented and freshly validated. Remaining gate: add reviewed fixture provenance and expected dumps for `gpos-single-adjustment.otf`, `gpos-pair-format1-kerning.otf`, `gpos-pair-format2-class.otf`, `gpos-valueformat-malformed.otf`, and `gpos-pair-out-of-range.otf`, then promote `gpos-trace.json` / `shaped-glyph-run.json` and layout-contract malformed/refusal diagnostics beyond the current contract-only goldens.
- Move to `ready` only after value-format coverage and kerning fixtures are reviewed.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M6`
- `area:shaping`
- `claim:tracked-gap`
