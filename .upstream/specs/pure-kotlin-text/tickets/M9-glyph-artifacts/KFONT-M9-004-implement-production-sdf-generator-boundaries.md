---
id: "KFONT-M9-004"
title: "Implement production SDF generator boundaries"
status: "done"
milestone: "M9"
priority: "P0"
owner_area: "glyph"
claim_impact: "tracked-gap"
depends_on: ["KFONT-M9-001", "KFONT-M9-002", "KFONT-M9-003"]
legacy_gate: ["dftext"]
---

# KFONT-M9-004 - Implement production SDF generator boundaries

## PM Note

Ce ticket borne le support SDF avant de rouvrir les claims historiques `dftext`.

## Problem

Distance-field text cannot be promoted from smoke evidence. The SDF route needs an explicit mathematical contract, eligibility policy, key fields, padding, normalization, quantization, and refusal behavior. Without this ticket, `dftext` could be retired using an unbounded generator, a transform-unsafe SDF, or an artifact that the GPU later samples with incompatible parameters.

## Scope

- Generate SDF artifacts only from accepted closed outline paths in glyph space.
- Use the normalized contract `clamp(0.5 + signedDistance / (2 * spreadPx), 0, 1)` with positive distance inside filled contours and R8Unorm storage.
- Record spread, source resolution, padding, bounds, quantization, output hash, and SDF eligibility in `sdf-glyph-artifact.json`.
- Enforce transform eligibility for finite identity, translate, scale, and accepted affine transforms without perspective.
- Emit refusals for non-closed outlines, hairline stroke, unsupported color glyphs, perspective transforms, unsupported LCD, and generation budget overflow.

## Non-Goals

- Do not implement LCD SDF or perspective SDF support.
- Do not accept bitmap alpha as SDF source without a future promotion fixture.
- Do not implement GPU sampling or WGSL SDF reconstruction; M11 owns renderer validation.
- Do not retire `dftext` without CPU artifact evidence, GPU route evidence, diagnostics, and dashboard updates.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/04-glyph-representation-and-artifacts.md`
- `.upstream/specs/pure-kotlin-text/06-gpu-renderer-handoff.md`
- `.upstream/specs/pure-kotlin-text/08-performance-budgets-and-telemetry.md`
- `.upstream/specs/pure-kotlin-text/09-migration-from-current-font-pack.md`

## Design Sketch

```kotlin
data class SDFGlyphArtifact(
    val strikeKey: GlyphStrikeKey,
    val sourceOutlineHash: StableHash,
    val spreadPx: Float,
    val sourceResolution: SDFSourceResolution,
    val atlasPaddingPx: Int,
    val bounds: RectI,
    val format: GlyphMaskFormat = GlyphMaskFormat.R8Unorm,
    val normalizedDistanceHash: StableHash,
    val eligibility: SDFEligibility,
    val diagnostics: List<TextDiagnostic>,
)

interface SDFGlyphGenerator {
    fun generate(plan: OutlineGlyphPlan, params: SDFGenerationParams): SDFGlyphArtifact
}
```

## Acceptance Criteria

- [x] SDF dumps include spread, padding, normalization formula version, quantization policy, source bounds, and output hash.
- [x] `GlyphStrikeKey` changes when spread, source resolution, variation, palette, transform bucket, or renderer descriptor version changes.
- [x] Non-closed geometry, perspective transforms, and LCD requests refuse with route-specific diagnostics; color glyph sources remain outside the SDF producer path and are kept as explicit non-claims until M10 payload work and M11 handoff evidence land.
- [x] Fallback order is A8, outline, then refusal, and every fallback decision appears in `glyph-artifact-plan.json`.
- [x] `dftext` remains open until this CPU artifact evidence is paired with M11 GPU sampling evidence.

## Required Evidence

- `sdf-glyph-artifact.json` fixture for a closed outline with default spread and one non-default spread.
- `sdf-normalization-dump.json` or equivalent section showing signed-distance samples around the 0.5 edge.
- Refusal fixtures for perspective transform, non-closed contour, unsupported color glyph, hairline stroke, and LCD request.
- Dashboard evidence keeping `dftext` classified as open until GPU handoff evidence exists.

## Fallback / Refusal Behavior

- Unsafe SDF requests fall back to A8 or outline only when route policy says the substitute preserves semantics.
- Generation failure emits `text.glyph.SDF-generation-failed`; transform refusal emits `text.glyph.SDF-transform-unsupported`.
- Legacy gate `dftext` remains open until implementation evidence, diagnostics, GPU sampling, and dashboard updates are linked.

## Dashboard Impact

- Expected row: `SDF glyph artifact route / dftext`.
- Expected classification: `tracked-gap`.
- Claim promotion allowed: no, unless CPU SDF evidence is combined with M11 GPU route evidence.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:glyph:test --tests '*SDF*'
rtk ./gradlew --no-daemon :font:glyph:test
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_font_fixture_assets.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_claim_dashboard.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
```

## Status Notes

- `done`: The CPU SDF producer now enforces closed contours for SDF generation, records spread/source-resolution/padding/hash evidence in checked-in `sdf-glyph-artifact.json`, preserves stable transform/LCD refusal diagnostics through route planning, and keeps atlas/GPU/`dftext` promotion as explicit remaining non-claims.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M9`
- `area:glyph`
- `claim:tracked-gap`
- `legacy:dftext`
