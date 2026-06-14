---
id: "KFONT-M3-004"
title: "Add vertical metric coverage"
status: "proposed"
milestone: "M3"
priority: "P1"
owner_area: "font-scaler"
claim_impact: "tracked-gap"
depends_on: ["KFONT-M2-004", "KFONT-M3-003"]
legacy_gate: null
---

# KFONT-M3-004 - Add vertical metric coverage

## PM Note

Ce ticket rend les métriques verticales visibles avant de promettre un rendu de texte vertical.

## Problem

The font target owns vertical metrics from `vhea`, `vmtx`, `VVAR`, and related metric facts when present. M3 needs coverage for extracting and dumping those metrics, but must not imply full vertical shaping or paragraph layout. Without explicit diagnostics, missing or malformed vertical tables can be confused with supported vertical text behavior.

## Scope

- Parse and expose vertical advances, top side bearings, vertical origin facts, and vertical font metrics from `vhea` and `vmtx`.
- Apply `VVAR` deltas for variable fonts when available.
- Emit diagnostics for absent vertical metric tables, malformed vertical metrics, and unavailable variation metrics.
- Include vertical fields in `glyph-metrics.json` with clear `present`, `fallback`, and `diagnostic` states.
- Document when horizontal metrics are used only as a fallback fact, not as a vertical text support claim.

## Non-Goals

- Do not implement vertical shaping, vertical glyph substitution, line layout, or paragraph behavior.
- Do not claim GPU text support or vertical atlas behavior.
- Do not invent vertical metrics when tables are absent.
- Do not make platform engine metrics normative.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/01-font-source-sfnt-and-scalers.md`
- `.upstream/specs/pure-kotlin-text/04-glyph-representation-and-artifacts.md`
- `.upstream/specs/pure-kotlin-text/07-validation-conformance-and-drift.md`
- `.upstream/specs/pure-kotlin-text/09-migration-from-current-font-pack.md`

## Design Sketch

```kotlin
data class VerticalMetricFacts(
    val glyphId: GlyphID,
    val verticalAdvance: Int?,
    val topSideBearing: Int?,
    val verticalOriginY: Int?,
    val source: MetricSource,
    val diagnostics: List<SerializedFontDiagnostic>,
)

class VerticalMetricResolver {
    fun resolve(typeface: TypefaceID, glyphId: GlyphID, position: VariationPosition): VerticalMetricFacts
}
```

## Acceptance Criteria

- [ ] Fixtures with `vhea` and `vmtx` produce vertical advances and bearings in `glyph-metrics.json`.
- [ ] Fixtures without vertical tables show an explicit absent/fallback diagnostic and do not claim vertical layout support.
- [ ] `VVAR` deltas apply at varied coordinates when fixture data exists.
- [ ] Malformed vertical metrics emit precise diagnostics without crashing horizontal outline scaling.
- [ ] Dashboard classification remains `tracked-gap` until vertical metric dumps and diagnostics are attached.

## Required Evidence

- `glyph-metrics.json` for a fixture with `vhea`/`vmtx`.
- `glyph-metrics.json` for a fixture without vertical tables showing explicit diagnostic/fallback state.
- Variation metric evidence for `VVAR` when available.
- Diagnostic snapshot for malformed `vhea`, `vmtx`, or `VVAR`.

## Fallback / Refusal Behavior

- Missing vertical tables may expose horizontal metrics as a fallback fact only with a diagnostic; it is not a vertical text support claim.
- Malformed vertical metrics must fail closed for vertical metric coverage while preserving safe horizontal scaler behavior.

## Dashboard Impact

- Expected row: `TrueType vertical metric facts`.
- Expected classification: `tracked-gap`.
- Claim promotion allowed: only for metric extraction, not vertical shaping or layout.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:scaler:test --tests '*VerticalMetric*' --tests '*VVAR*'
```

## Status Notes

- `proposed`: Vertical metric coverage is specified, but no metrics dump evidence is attached yet.
- Move to `ready` after OpenType table fact dumps expose `vhea`, `vmtx`, and variation metric tables.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M3`
- `area:font-scaler`
- `claim:tracked-gap`
