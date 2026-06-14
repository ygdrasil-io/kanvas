---
id: "KFONT-M7-003"
title: "Add variable-axis-aware fallback"
status: "proposed"
milestone: "M7"
priority: "P1"
owner_area: "fallback"
claim_impact: "tracked-gap"
depends_on: ["KFONT-M7-001", "KFONT-M7-002", "KFONT-M3-003", "KFONT-M4-005"]
legacy_gate: null
---

# KFONT-M7-003 - Add variable-axis-aware fallback

## PM Note

Ce ticket evite qu'un fallback perde les axes variables demandes, comme poids ou largeur, sans le signaler.

## Problem

Fallback can change visible output if the selected face does not support requested variation axes or clamps coordinates differently. Kanvas needs axis-aware fallback ranking, selected coordinate serialization, and diagnostics for unsupported or substituted variation positions.

## Scope

- Extend fallback requests with desired variation axes, named instance, style synthesis policy, and tolerated axis substitutions.
- Rank candidate faces by axis support, coordinate range, named instance compatibility, metrics availability, and glyph coverage.
- Serialize selected fallback variation coordinates in `fallback-decision-trace.json`, `resolved-font-runs.json`, and typeface identity references.
- Add diagnostics for missing axis, clamped coordinate, incompatible named instance, and missing variation metrics.
- Cover both TrueType variable and CFF2 variable fallback candidates where fixtures exist.

## Non-Goals

- Do not implement variation scaler math; M3 and M4 own glyph/metric variation output.
- Do not synthesize fake variable axes when the fallback face lacks them.
- Do not implement style matching unrelated to variation axes beyond fields needed for candidate scoring.
- Do not claim renderer or glyph artifact support.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/01-font-source-sfnt-and-scalers.md`
- `.upstream/specs/pure-kotlin-text/02-opentype-layout-shaping-engine.md`
- `.upstream/specs/pure-kotlin-text/07-validation-conformance-and-drift.md`

## Design Sketch

```kotlin
data class VariableFallbackRequest(
    val textRange: IntRange,
    val requestedAxes: Map<AxisTag, Float>,
    val namedInstance: String?,
    val synthesisPolicy: StyleSynthesisPolicy,
)

data class AxisCompatibilityScore(
    val typefaceId: TypefaceID,
    val supportedAxes: Set<AxisTag>,
    val selectedCoordinates: Map<AxisTag, Float>,
    val clampedAxes: Map<AxisTag, AxisClamp>,
    val diagnostics: List<RouteDiagnostic>,
)

data class VariableFallbackDecision(
    val baseDecision: FallbackDecision,
    val axisScore: AxisCompatibilityScore,
)
```

## Acceptance Criteria

- [ ] Candidate with matching glyph coverage but missing requested axis loses to a candidate with both coverage and axis support when both are available.
- [ ] Coordinate clamping is serialized with requested, min, max, and selected values.
- [ ] Missing variation metrics emits `font.metrics-variation-unavailable` when metrics would affect shaped advances.
- [ ] CFF2 variable fallback fixture records CFF2 variation support without requiring host font engines.
- [ ] Typeface identity for the selected fallback includes selected variation coordinates.

## Required Evidence

- `fallback-axis-trace.json` or `fallback-decision-trace.json` section with requested axes, candidate axis coverage, selected coordinates, clamping, named instance match, and diagnostics.
- `resolved-font-runs.json` showing selected fallback typeface IDs with variation coordinates.
- Fixtures: `fallback-variable-weight.ttf`, `fallback-variable-width.ttf`, `fallback-variable-cff2.otf`, `fallback-axis-missing.ttf`, `fallback-axis-clamped.ttf`, `fallback-metrics-variation-missing.ttf`.
- Diagnostics asserted in tests: `font.variation-axis-unsupported`, `font.metrics-variation-unavailable`, `font.fallback-glyph-unavailable`, `font.fallback.axis-clamped`.

## Fallback / Refusal Behavior

- Unsupported or malformed paths must emit one of: `font.variation-axis-unsupported`, `text.fallback.variation-defaulted`.
- The diagnostic must name the affected range, glyph, cluster, lookup, font source, or route object when that subject exists.
- Silent fallback to platform/native/font engine behavior is not allowed; the ticket remains `tracked-gap` until the listed evidence and validation pass.

## Dashboard Impact

- Expected row: `Add variable-axis-aware fallback`.
- Expected classification: `tracked-gap`.
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:text:test --tests '*VariableFallback*'
```

## Status Notes

- `proposed`: Depends on fallback trace plus TrueType/CFF2 variation foundations.
- Move to `ready` only after axis scoring policy and fixture availability are reviewed.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M7`
- `area:fallback`
- `claim:tracked-gap`
