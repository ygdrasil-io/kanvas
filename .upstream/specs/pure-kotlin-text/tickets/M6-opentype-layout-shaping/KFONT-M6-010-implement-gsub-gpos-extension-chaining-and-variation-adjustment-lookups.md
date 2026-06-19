---
id: "KFONT-M6-010"
title: "Implement GSUB/GPOS extension, chaining and variation-adjustment lookups"
status: "proposed"
milestone: "M6"
priority: "P1"
owner_area: "shaping"
claim_impact: "tracked-gap"
depends_on: ["KFONT-M6-003", "KFONT-M6-004", "KFONT-M6-005", "KFONT-M4-005"]
legacy_gate: null
---

# KFONT-M6-010 - Implement GSUB/GPOS extension, chaining and variation-adjustment lookups

## PM Note

Ce ticket ferme les lookups OpenType avances qui bloquent les scripts complexes et les ajustements variables.

## Problem

The required shaping matrix includes chaining contexts, extension lookups, reverse chaining where required, contextual positioning, and variation/device adjustments. Without these lookup classes, complex features may be silently skipped or applied without the correct variable-coordinate deltas.

## Scope

- Implement GSUB chaining contextual substitution, extension substitution, and reverse chaining substitution needed by target fixtures.
- Implement GPOS contextual positioning, chaining contextual positioning, extension positioning, and device/variation adjustment records for supported value formats.
- Resolve extension lookup targets with loop guards and lookup-type validation.
- Apply variation adjustments using normalized coordinates and variation data from the font/scaler stack, including CFF2 variation-dependent metrics where applicable.
- Emit `gsub-trace.json`, `gpos-trace.json`, and `variation-adjustment-trace.json` entries with lookup target, backtrack/input/lookahead match, extension resolution, device/variation deltas, and diagnostics.

## Non-Goals

- Do not add new scripts to the target matrix.
- Do not implement unsupported OpenType lookup types outside the target contract.
- Do not implement paragraph layout or GPU glyph artifacts.
- Do not mask missing variation data by applying static positioning without a diagnostic.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/02-opentype-layout-shaping-engine.md`
- `.upstream/specs/pure-kotlin-text/01-font-source-sfnt-and-scalers.md`
- `.upstream/specs/pure-kotlin-text/07-validation-conformance-and-drift.md`

## Design Sketch

```kotlin
class OpenTypeExtensionResolver(
    private val layoutTables: OpenTypeLayoutFacts,
    private val limits: LookupExecutionLimits,
) {
    fun resolveExtension(sourceLookup: LookupRef): ResolvedLookup
}

data class ChainingContextMatch(
    val backtrack: List<GlyphId>,
    val input: List<GlyphId>,
    val lookahead: List<GlyphId>,
    val nestedLookups: List<NestedLookupApplication>,
)

data class VariationAdjustment(
    val glyphId: GlyphId,
    val valueField: GposValueField,
    val deviceTableRef: DeviceTableRef,
    val normalizedPosition: NormalizedVariationPosition,
    val delta: FontUnit,
)
```

## Acceptance Criteria

- [ ] GSUB chaining contextual fixture matches backtrack/input/lookahead and applies nested substitutions in deterministic order.
- [ ] GSUB extension fixture resolves an extension lookup target and rejects extension cycles or wrong target lookup types.
- [ ] GPOS contextual and chaining contextual fixtures apply position adjustments with traceable match facts.
- [ ] Device/variation adjustment fixture changes placement or advance at different variation coordinates and records the delta source.
- [ ] Missing or malformed variation/device data emits stable diagnostics rather than applying static fallback silently.

## Required Evidence

- `gsub-trace.json` and `gpos-trace.json` with chaining match facts, extension target resolution, nested lookup order, before/after glyph buffer, and diagnostics.
- `variation-adjustment-trace.json` with normalized coordinates, device/variation references, applied deltas, and metric/position fields affected.
- Fixtures: `gsub-chaining-context.otf`, `gsub-extension-substitution.otf`, `gsub-reverse-chaining.otf`, `gpos-contextual-positioning.otf`, `gpos-chaining-positioning.otf`, `gpos-extension-positioning.otf`, `gpos-variation-device.otf`, `layout-extension-cycle.otf`.
- Diagnostics asserted in tests: `text.shaping.lookup-malformed`, `text.shaping.lookup-type-unsupported`, `text.shaping.lookup-cycle-detected`, `font.variation-data-malformed`, `font.metrics-variation-unavailable`.

## Fallback / Refusal Behavior

- Unsupported or malformed paths must emit one of: `text.shaping.lookup-type-unsupported`, `text.shaping.variation-adjustment-unsupported`.
- The diagnostic must name the affected range, glyph, cluster, lookup, font source, or route object when that subject exists.
- Silent fallback to platform/native/font engine behavior is not allowed; the ticket remains `tracked-gap` until the listed evidence and validation pass.

## Dashboard Impact

- Expected row: `Implement GSUB/GPOS extension, chaining and variation-adjustment lookups`.
- Expected classification: `tracked-gap`.
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:text:test --tests '*ExtensionLookup*' --tests '*ChainingLookup*' --tests '*VariationAdjustment*'
```

## Status Notes

- `proposed`: Advanced lookup work depends on basic GSUB/GPOS, mark/cursive positioning, and variation path foundations.
- Current blocker audit (2026-06-19): `KFONT-M6-003`, `KFONT-M6-004`, `KFONT-M6-005`, and `KFONT-M4-005` are now `done`, but the advanced-lookup fixture set `gsub-chaining-context.otf`, `gsub-extension-substitution.otf`, `gsub-reverse-chaining.otf`, `gpos-contextual-positioning.otf`, `gpos-chaining-positioning.otf`, `gpos-extension-positioning.otf`, `gpos-variation-device.otf`, and `layout-extension-cycle.otf` is still not present in-repo. The refreshed asset/license audit at `reports/pure-kotlin-text/2026-06-19-kfont-m6-fixture-asset-license-audit.md` confirms compatible candidate sources exist, but none yet satisfy the exact ticket-local fixture pack or the required `variation-adjustment-trace.json` evidence. Remaining gate is keep the bounded GSUB/GPOS base slices in place and add reviewed advanced-lookup fixture provenance plus variation-adjustment evidence without substituting unrelated robustness fonts for the named fixture family.
- Move to `ready` only after lookup-type coverage and variation diagnostics are reviewed.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M6`
- `area:shaping`
- `claim:tracked-gap`
