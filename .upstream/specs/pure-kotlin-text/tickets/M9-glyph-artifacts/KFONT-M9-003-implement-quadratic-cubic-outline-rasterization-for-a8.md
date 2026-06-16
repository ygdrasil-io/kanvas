---
id: "KFONT-M9-003"
title: "Implement quadratic/cubic outline rasterization for A8"
status: "done"
milestone: "M9"
priority: "P1"
owner_area: "glyph"
claim_impact: "tracked-gap"
depends_on: ["KFONT-M3-001", "KFONT-M9-001", "KFONT-M9-002"]
legacy_gate: null
---

# KFONT-M9-003 - Implement quadratic/cubic outline rasterization for A8

## PM Note

Ce ticket transforme les outlines en masques grayscale reproductibles pour le texte courant.

## Problem

The A8 route needs deterministic CPU coverage masks from quadratic and cubic glyph outlines before atlas artifacts or GPU sampling can be trusted. Current ticket wording does not specify fill rules, curve flattening/rasterization policy, bounds, origin, row stride, coverage hash, or malformed-outline diagnostics. Without these details, A8 evidence could be visual-only and impossible to compare across runs.

## Scope

- Implement pure Kotlin A8 mask generation from `OutlineGlyphPlan` for quadratic and cubic contours.
- Support glyph-space to strike-space transform, fill rule, tight/conservative bounds, origin, padding policy, row stride, and 8-bit coverage output.
- Emit per-glyph `a8-glyph-mask.json` facts and include source mask hashes in `glyph-artifact-plan.json`.
- Add malformed outline, empty outline, unsupported fill rule, and coverage overflow diagnostics.
- Keep the route CPU-prepared; no GPU texture, sampler, or atlas resource allocation happens here.

## Non-Goals

- Do not implement SDF generation; KFONT-M9-004 owns that route.
- Do not implement LCD subpixel masks.
- Do not implement atlas eviction or GPU upload.
- Do not use FreeType or native rasterizers as normative output.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/04-glyph-representation-and-artifacts.md`
- `.upstream/specs/pure-kotlin-text/07-validation-conformance-and-drift.md`
- `.upstream/specs/pure-kotlin-text/08-performance-budgets-and-telemetry.md`

## Design Sketch

```kotlin
data class A8GlyphMaskArtifact(
    val strikeKey: GlyphStrikeKey,
    val glyphBounds: RectI,
    val origin: Vec2I,
    val rowStrideBytes: Int,
    val coverageBytes: ByteArray,
    val coverageHash: StableHash,
    val rasterDiagnostics: List<TextDiagnostic>,
)

interface A8OutlineRasterizer {
    fun rasterize(plan: OutlineGlyphPlan, key: GlyphStrikeKey): A8GlyphMaskArtifact
}
```

## Acceptance Criteria

- [x] Quadratic and cubic outline fixtures produce stable bounds, origin, row stride, coverage bytes hash, and dump output.
- [x] The rasterizer handles empty glyphs and `.notdef` without crashing and records the selected fallback/refusal path.
- [x] Malformed contours emit `text.glyph.A8-generation-failed` or a narrower reason with glyph ID and strike key hash.
- [x] A8 artifacts contain no GPU resource handles and can be embedded in `GlyphAtlasArtifact` input.
- [x] External rasterizer comparisons, if present, are labeled `drift-only`.

## Required Evidence

- `a8-glyph-mask.json` fixtures for simple quadratic glyph, cubic glyph, composite-derived outline, empty `.notdef` glyph, malformed contour, and unsupported fill-rule refusal.
- CPU oracle hash for coverage bytes and source outline path hash.
- Diagnostic snapshot for malformed outline and unsupported fill-rule refusal.

## Fallback / Refusal Behavior

- If A8 generation fails, the planner may try outline fallback only when style and route policy allow it and the decision trace records the failure.
- Unsupported LCD or color-specific requests must not be coerced into A8 without explicit monochrome fallback policy.
- Malformed glyphs route to `.notdef` or refusal with stable diagnostics, never to host rasterization.

## Dashboard Impact

- Expected row: `A8 outline rasterization`.
- Expected classification: `tracked-gap`.
- Claim promotion allowed: no, unless A8 mask fixtures and CPU oracle hashes are attached.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:glyph:test --tests '*A8*'
rtk ./gradlew --no-daemon :font:glyph:test
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_font_fixture_assets.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_claim_dashboard.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk env PYTHONDONTWRITEBYTECODE=1 python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
```

## Status Notes

- `done`: Deterministic A8 rasterization now covers quadratic/cubic outlines, composite-derived outlines, empty `.notdef`, malformed contours, unsupported fill-rule refusal, and coverage-overflow refusal with stable CPU-only evidence in `a8-glyph-mask.json`.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M9`
- `area:glyph`
- `claim:tracked-gap`
