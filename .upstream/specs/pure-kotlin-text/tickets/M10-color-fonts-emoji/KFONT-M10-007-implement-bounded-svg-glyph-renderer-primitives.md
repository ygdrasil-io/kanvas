---
id: "KFONT-M10-007"
title: "Implement bounded SVG glyph renderer primitives"
status: "proposed"
milestone: "M10"
priority: "P1"
owner_area: "color"
claim_impact: "tracked-gap"
depends_on: ["KFONT-M2-001", "KFONT-M9-002"]
legacy_gate: null
---

# KFONT-M10-007 - Implement bounded SVG glyph renderer primitives

## PM Note

Ce ticket cible un sous-ensemble SVG de glyphes, pas un moteur SVG général.

## Problem

SVG-in-OpenType glyphs are mandatory for the complete target, but the support must be pure Kotlin, glyph-scoped, static, and bounded. The missing slice is a `SVGGlyphPlan` for supported primitives: root/viewBox, groups, paths, basic shapes, transforms, fill/stroke, opacity, gradients, clip paths, `defs`/`symbol`, and bounded `use` references. Without a concrete plan, SVG support could accidentally depend on native SVG engines or unbounded document behavior.

## Scope

- Parse glyph-scoped SVG document records from M2 table facts and select the record for a glyph ID.
- Convert supported static SVG primitives into `SVGGlyphPlan` value objects with path/material refs, transforms, gradients, clips, bounds, color inheritance, and diagnostics.
- Enforce limits for document bytes, generated path commands, gradient stops, `use` recursion depth, and primitive count.
- Emit `svg-glyph-plan.json` with source document hash, viewBox, glyph bounds, primitive list, and renderer-neutral route facts.
- Preserve monochrome fallback only when a valid outline exists and style permits it.

## Non-Goals

- Do not implement scripts, animation, filters, `foreignObject`, external resources, network fetches, embedded text layout, or dynamic CSS selectors.
- Do not build a general SVG renderer for application content.
- Do not allocate GPU resources or lower to WGSL.
- Do not use a native SVG renderer as normative behavior.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/05-color-fonts-bitmap-svg-emoji.md`
- `.upstream/specs/pure-kotlin-text/06-gpu-renderer-handoff.md`
- `.upstream/specs/gpu-renderer/21-text-glyph-pipeline.md`
- `.upstream/specs/pure-kotlin-text/07-validation-conformance-and-drift.md`

## Design Sketch

```kotlin
data class SVGGlyphPlan(
    val glyphId: GlyphId,
    val typefaceId: TypefaceID,
    val documentHash: StableHash,
    val viewBox: RectF,
    val primitives: List<SVGGlyphPrimitive>,
    val bounds: RectF,
    val resourceLimits: SVGGlyphResourceLimits,
    val diagnostics: List<TextDiagnostic>,
)

sealed interface SVGGlyphPrimitive {
    data class Path(val pathDataHash: StableHash, val paint: SVGPaintSpec, val transform: Matrix3x3) : SVGGlyphPrimitive
    data class Clip(val clipId: String, val childRefs: List<Int>, val bounds: RectF) : SVGGlyphPrimitive
    data class Gradient(val gradientId: String, val stops: List<ResolvedGradientStop>) : SVGGlyphPrimitive
}
```

## Acceptance Criteria

- [ ] Fixtures cover static path, basic shape, transform, fill, stroke, opacity, linear/radial gradient, clip path, `defs`/`symbol`, and bounded `use`.
- [ ] `svg-glyph-plan.json` records document hash, viewBox, bounds, primitive count, limit decisions, and diagnostics.
- [ ] Unsupported dynamic features refuse with `text.SVG.feature-unsupported` or a narrower SVG diagnostic.
- [ ] `use` recursion is bounded and deterministic.
- [ ] The plan is glyph-scoped and contains no network, native renderer, or GPU handle references.

## Required Evidence

- `svg-glyph-plan.json` fixtures for supported path, gradient, transform, clip, and bounded `use` cases.
- Bounds dump for each positive SVG fixture.
- Limit diagnostics for excessive path commands, gradient stops, or `use` recursion depth.

## Fallback / Refusal Behavior

- Unsupported SVG primitives refuse the SVG route for the glyph and may fall back to monochrome outline only when policy allows.
- External resources, scripts, animation, and network references always refuse; they are not dependency gates.
- Silent native SVG fallback is not allowed.

## Dashboard Impact

- Expected row: `SVG glyph renderer primitives`.
- Expected classification: `tracked-gap`.
- Claim promotion allowed: no, unless supported primitive fixtures and refusal diagnostics are attached.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:glyph:test --tests '*SVGGlyph*'
```

## Status Notes

- `proposed`: Establishes the accepted pure Kotlin SVG glyph subset before SVG refusal fixtures are finalized.
- Move to `ready` only after primitive list, resource limits, and plan schema are reviewed.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M10`
- `area:color`
- `claim:tracked-gap`
