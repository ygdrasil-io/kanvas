---
id: "KFONT-M6-010"
title: "Implement GSUB/GPOS extension, chaining and variation-adjustment lookups"
status: "blocked"
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
- Checked-in fixture wave (2026-06-23): `gsub-extension-substitution.otf` and `layout-extension-cycle.otf` are now checked in under synthetic Apache-2.0 provenance, and `ExtensionLookupFixtureTest` plus `extension-lookup-report.json` now prove bounded repo-backed GSUB extension substitution for `A -> glyph 15` and `fi -> glyph 42` together with a deterministic `font.sfnt.optional-table-malformed` refusal for a self-targeting extension lookup type `7`.
- `blocked` (2026-06-23 compatible source intake): `reports/font/fixtures/fonts/shaping/FallbackPlus-Small.otf` is now vendored from `simoncozens/test-fonts` under checked-in Apache-2.0 provenance as a bounded source asset for future advanced-lookup derivation, but this does not satisfy the named `KFONT-M6-010` fixture family on its own.
- `blocked` (2026-06-23 resource-seed wave): the remaining named advanced-lookup resources `gsub-chaining-context.otf`, `gsub-reverse-chaining.otf`, `gpos-contextual-positioning.otf`, `gpos-chaining-positioning.otf`, `gpos-extension-positioning.otf`, `gpos-variation-device.otf`, and `variation-adjustment-trace.json` are now checked in under reviewed provenance as seed resources, but this ticket still lacks live chaining/reverse-chaining/runtime GPOS assertions and a non-stub variation/device trace. Keep the ticket `blocked` until those runtime/parser gates land without promoting support from resource presence alone.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M6`
- `area:shaping`
- `claim:tracked-gap`
