---
id: "KFONT-M3-003"
title: "Add phantom point and advance delta support"
status: "review"
milestone: "M3"
priority: "P0"
owner_area: "font-scaler"
claim_impact: "tracked-gap"
depends_on: ["KFONT-M3-002"]
legacy_gate: null
---

# KFONT-M3-003 - Add phantom point and advance delta support

## PM Note

Ce ticket aligne les contours variables et les avances, pour éviter des glyphes justes mais mal espacés.

## Problem

TrueType variation support affects more than visible contour points. Phantom points and advance deltas can change horizontal and vertical advances through `gvar`, `HVAR`, `VVAR`, and `MVAR`. Without this support, varied outlines may be correct while glyph metrics and text spacing remain wrong.

## Scope

- Model left side bearing, right side bearing, top origin, and bottom origin phantom points for TrueType glyphs.
- Apply `gvar` phantom point deltas where they affect advances.
- Apply `HVAR`, `VVAR`, and `MVAR` metrics deltas when available and diagnosed when unavailable.
- Emit variation-adjusted `glyph-metrics.json` for min/default/max axis positions.
- Keep variation coordinates in scaler and cache-relevant identity preimages.

## Non-Goals

- Do not implement paragraph layout or full line breaking.
- Do not implement CFF2 metrics variation.
- Do not claim vertical text layout beyond font metric extraction and scaler facts.
- Do not implement a hinting VM.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/01-font-source-sfnt-and-scalers.md`
- `.upstream/specs/pure-kotlin-text/04-glyph-representation-and-artifacts.md`
- `.upstream/specs/pure-kotlin-text/07-validation-conformance-and-drift.md`
- `.upstream/specs/pure-kotlin-text/09-migration-from-current-font-pack.md`

## Design Sketch

```kotlin
data class PhantomPointSet(
    val leftSideBearing: Point26Dot6,
    val rightSideBearing: Point26Dot6,
    val topOrigin: Point26Dot6?,
    val bottomOrigin: Point26Dot6?,
)

data class AdvanceDeltaResult(
    val glyphId: GlyphID,
    val variationPosition: VariationPosition,
    val horizontalAdvance: Int,
    val verticalAdvance: Int?,
    val diagnostics: List<SerializedFontDiagnostic>,
)

class TrueTypeAdvanceDeltaResolver {
    fun resolve(glyph: GlyfGlyph, position: VariationPosition): AdvanceDeltaResult
}
```

## Acceptance Criteria

- [x] Horizontal advance changes caused by phantom points are reflected in `glyph-metrics.json`.
- [ ] `HVAR`, `VVAR`, and `MVAR` data are applied when fixtures provide them and diagnosed when required data is malformed or unavailable.
- [x] Variation coordinates are included in relevant scaler identity or cache keys.
- [x] Default coordinates match base `hmtx`/`vmtx` metrics when no deltas apply.
- [x] Missing variation metrics emit `font.metrics-variation-unavailable` or `font.variation-data-malformed` as appropriate.

## Required Evidence

- `glyph-metrics.json` for base/default and varied min/max positions showing advances, side bearings, phantom points, and applied deltas.
- `variation-deltas.json` entries that include phantom point deltas.
- Diagnostic snapshot for unavailable or malformed `HVAR`, `VVAR`, or `MVAR` data.
- Determinism diff for repeated metric dump generation.

## Fallback / Refusal Behavior

- If metrics variation data is unavailable but default metrics are semantically valid, emit a visible diagnostic and keep the requested scope classified according to the missing blocker.
- If default fallback would misrepresent the requested variation instance, refuse the glyph metrics route.

## Dashboard Impact

- Expected row: `TrueType phantom points and advance deltas`.
- Expected classification: `tracked-gap`.
- Claim promotion allowed: only for the covered variation metric slice with dumps and diagnostics attached.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:scaler:test --tests '*PhantomPoint*' --tests '*AdvanceDelta*' --tests '*HVAR*'
```

## Status Notes

- `review`: bounded horizontal phantom-point `gvar` metrics evidence is attached in
  `reports/font/fixtures/expected/scaler/truetype-gvar-iup.json` and summarized in
  `reports/pure-kotlin-text/2026-06-16-kfont-m3-003-phantom-metrics.md`.
- Remaining gate before `done`: parse and apply `HVAR`/`VVAR`/`MVAR` deltas when present, and attach a malformed-table diagnostic snapshot instead of the current unimplemented-table warning.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M3`
- `area:font-scaler`
- `claim:tracked-gap`
