---
id: "KFONT-M3-002"
title: "Implement TrueType IUP interpolation tests"
status: "proposed"
milestone: "M3"
priority: "P0"
owner_area: "font-scaler"
claim_impact: "tracked-gap"
depends_on: ["KFONT-M3-001"]
legacy_gate: null
---

# KFONT-M3-002 - Implement TrueType IUP interpolation tests

## PM Note

Ce ticket rend les fontes variables TrueType vérifiables quand certains points n'ont pas de delta explicite.

## Problem

TrueType `gvar` deltas may omit point deltas that must be inferred with IUP interpolation. Without focused tests, variable font outlines can pass default-coordinate cases but drift at min/max axis positions, especially for contours with sparse deltas or composite glyph dependencies.

## Scope

- Add IUP interpolation coverage for x and y deltas on simple glyph contours.
- Cover contour endpoints, all-points-missing cases, one explicit delta, two explicit deltas, and wraparound interpolation.
- Connect `fvar` axis coordinates and `avar` normalization to `gvar` tuple selection.
- Include composite glyph variation cases where component outlines depend on interpolated child deltas.
- Emit `variation-deltas.json` and updated `glyph-outline.json` for min/default/max axis positions.

## Non-Goals

- Do not implement CFF2 variation blending.
- Do not add complete shaping or layout for variable fonts.
- Do not implement a TrueType instruction VM.
- Do not claim GPU artifacts for varied outlines.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/01-font-source-sfnt-and-scalers.md`
- `.upstream/specs/pure-kotlin-text/04-glyph-representation-and-artifacts.md`
- `.upstream/specs/pure-kotlin-text/07-validation-conformance-and-drift.md`
- `.upstream/specs/pure-kotlin-text/09-migration-from-current-font-pack.md`

## Design Sketch

```kotlin
data class VariationPosition(
    val coordinates: SortedMap<String, Double>,
    val normalizedCoordinates: SortedMap<String, Double>,
)

data class GvarContourDeltas(
    val explicitDeltas: Map<PointIndex, DeltaXY>,
    val inferredDeltas: Map<PointIndex, DeltaXY>,
)

class IUPInterpolator {
    fun interpolate(contour: GlyfContour, explicit: Map<PointIndex, DeltaXY>): GvarContourDeltas
}
```

## Acceptance Criteria

- [ ] IUP tests cover x-only, y-only, both-axis, endpoint, wraparound, and sparse-delta contours.
- [ ] `variation-deltas.json` separates explicit and inferred deltas for each tested point.
- [ ] Min/default/max variation positions produce stable `glyph-outline.json` path hashes.
- [ ] Malformed `gvar` tuple data emits `font.variation-data-malformed`.
- [ ] Default-coordinate output remains identical to the non-varied outline when no deltas apply.

## Required Evidence

- `variation-deltas.json` for simple glyph IUP cases at min/default/max axis positions.
- `glyph-outline.json` and path hash comparison for varied simple and composite glyph fixtures.
- Diagnostic snapshot for malformed tuple, missing point count, or invalid delta run.
- Fixture manifest entry naming axes, coordinates, and expected interpolation cases.

## Fallback / Refusal Behavior

- Malformed or unsupported variation data must diagnose and either fall back to default coordinates only when semantically valid, or refuse the glyph route when that would misrepresent the requested instance.
- The scaler must not hide IUP drift with broad numeric tolerances.

## Dashboard Impact

- Expected row: `TrueType gvar IUP interpolation`.
- Expected classification: `tracked-gap`.
- Claim promotion allowed: only for the covered IUP fixture matrix with dumps and diagnostics attached.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:scaler:test --tests '*IUP*' --tests '*Gvar*'
```

## Status Notes

- `proposed`: IUP cases are specified, but no variation dump evidence is attached yet.
- Move to `ready` after composite glyph coverage is available for varied composite cases.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M3`
- `area:font-scaler`
- `claim:tracked-gap`
