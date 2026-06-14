---
id: "KFONT-M9-002"
title: "Promote `GlyphArtifactPlan` route taxonomy"
status: "proposed"
milestone: "M9"
priority: "P0"
owner_area: "glyph"
claim_impact: "tracked-gap"
depends_on: ["KFONT-M9-001"]
legacy_gate: null
---

# KFONT-M9-002 - Promote `GlyphArtifactPlan` route taxonomy

## PM Note

Ce ticket rend visible pourquoi chaque glyph passe par outline, A8, SDF, couleur, bitmap, SVG ou refus.

## Problem

The glyph stack needs a typed representation decision before masks, atlases, color glyphs, or GPU routes can be validated. A generic artifact request hides whether a glyph should become an outline plan, A8 mask, SDF mask, COLR plan, PNG bitmap, SVG plan, or stable refusal. Without the taxonomy, unsupported routes can collapse into a silent monochrome fallback or a misleading GPU claim.

## Scope

- Define `GlyphArtifactPlan` variants for `OutlineGlyphPlan`, `A8MaskPlan`, `SDFMaskPlan`, `ColorGlyphPlanRef`, `BitmapGlyphPlanRef`, `SVGGlyphPlanRef`, and `UnsupportedGlyphPlan`.
- Define policy inputs: `ShapedGlyphRun`, `GlyphStrikeKey`, text style preferences, transform class, atlas budget, SDF eligibility, color glyph availability, emoji sequence facts, and renderer capability summary.
- Record selection order and rejected alternatives with stable reason codes.
- Emit `glyph-artifact-plan.json` with selected route, fallback chain, key hashes, bounds placeholders, and diagnostics.
- Keep the taxonomy renderer-neutral; no WebGPU resources or pipeline objects belong in this module.

## Non-Goals

- Do not implement COLR, bitmap, SVG, or emoji payload parsing; M10 owns those plan internals.
- Do not implement GPU route selection or resource binding; M11 owns renderer handoff.
- Do not use CPU-rendered full text textures as a representation.
- Do not claim LCD support.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/04-glyph-representation-and-artifacts.md`
- `.upstream/specs/pure-kotlin-text/05-color-fonts-bitmap-svg-emoji.md`
- `.upstream/specs/pure-kotlin-text/06-gpu-renderer-handoff.md`
- `.upstream/specs/pure-kotlin-text/07-validation-conformance-and-drift.md`

## Design Sketch

```kotlin
sealed interface GlyphArtifactPlan {
    val strikeKey: GlyphStrikeKey
    val sourceRun: GlyphRunDescriptorRef
    val diagnostics: List<TextDiagnostic>

    data class Outline(val plan: OutlineGlyphPlan) : GlyphArtifactPlan
    data class A8Mask(val plan: A8GlyphMaskPlan) : GlyphArtifactPlan
    data class SDFMask(val plan: SDFGlyphMaskPlan) : GlyphArtifactPlan
    data class Color(val planRef: ColorGlyphPlanRef) : GlyphArtifactPlan
    data class Bitmap(val planRef: BitmapGlyphPlanRef) : GlyphArtifactPlan
    data class SVG(val planRef: SVGGlyphPlanRef) : GlyphArtifactPlan
    data class Unsupported(val reason: TextDiagnostic) : GlyphArtifactPlan
}

data class GlyphArtifactDecisionTrace(
    val selected: GlyphRepresentationRoute,
    val rejected: List<RouteRejection>,
    val fallbackPolicy: GlyphFallbackPolicy,
)
```

## Acceptance Criteria

- [ ] Every glyph in a `ShapedGlyphRun` receives exactly one accepted plan or one explicit `UnsupportedGlyphPlan`.
- [ ] The dump records rejected alternatives and fallback policy; it never hides a color/SVG/SDF failure behind an unmarked A8 plan.
- [ ] SDF and A8 routes carry `CPUPreparedGPU` artifact intent without allocating GPU resources.
- [ ] LCD requests produce `text.glyph.LCD-future-research` and remain outside target support.
- [ ] Color, bitmap, and SVG route placeholders are compatible with M10 plan IDs and M11 artifact registry names.

## Required Evidence

- `glyph-artifact-plan.json` fixture with outline, A8, SDF, color placeholder, bitmap placeholder, SVG placeholder, and unsupported routes.
- Route decision trace showing selected and rejected alternatives for a color glyph with monochrome fallback.
- Diagnostic fixture for unsupported LCD, missing outline, unsupported SDF transform, and atlas budget refusal.

## Fallback / Refusal Behavior

- Fallback is allowed only when the selected plan explicitly records the rejected route and accepted substitute.
- Missing glyphs route to `.notdef` only with `text.glyph.missing` diagnostics and source glyph facts.
- Unsupported routes remain `tracked-gap` or the narrower claim category until implementation evidence exists.

## Dashboard Impact

- Expected row: `GlyphArtifactPlan route taxonomy`.
- Expected classification: `tracked-gap`.
- Claim promotion allowed: no, unless route taxonomy fixtures and refusal diagnostics are attached.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:glyph:test --tests '*GlyphArtifactPlan*'
```

## Status Notes

- `proposed`: Provides the route vocabulary consumed by A8/SDF, M10 color plans, and M11 GPU handoff.
- Move to `ready` only after route names and fallback trace schema are reviewed.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M9`
- `area:glyph`
- `claim:tracked-gap`
