---
id: "KFONT-M10-004"
title: "Implement COLRv1 transform/composite/clip operation group"
status: "done"
milestone: "M10"
priority: "P0"
owner_area: "color"
claim_impact: "tracked-gap"
depends_on: ["KFONT-M10-002", "KFONT-M10-003"]
legacy_gate: ["coloremoji_blendmodes"]
---

# KFONT-M10-004 - Implement COLRv1 transform/composite/clip operation group

## PM Note

Ce ticket garde le gate `coloremoji_blendmodes` ouvert tant que les composites couleur ne sont pas prouvés.

## Problem

COLRv1 transforms, clips, and composites decide whether color emoji and blend-mode GMs can be promoted. The missing gap is a renderer-neutral plan for `PaintTransform`, translate/scale/rotate/skew variants, `PaintComposite`, and `PaintClipBox`, including bounds, blend/composite facts, destination-read needs, and diagnostics. Without this, Kanvas could claim color emoji support without proving the operations that make blend-heavy glyphs correct.

## Scope

- Implement COLRv1 transform nodes: transform, translate, scale, rotate, and skew with deterministic matrix facts.
- Implement `PaintComposite` and `PaintClipBox` plan nodes with blend/composite mode, source/destination child refs, clip bounds, and layer/destination-read hints.
- Compute conservative bounds through nested transform, clip, gradient, solid, glyph, and colr-glyph nodes.
- Emit `colrv1-paint-graph.json` and `color-glyph-composite-plan.json` with ordered operations and renderer handoff facts.
- Add diagnostics for unsupported composite modes, clip budget overflow, singular transforms, and malformed transform payloads.

## Non-Goals

- Do not implement GPU blend, layer, or destination-read execution; M11/gpu-renderer own route proof.
- Do not retire `coloremoji_blendmodes` without CPU color plan evidence and GPU composite evidence.
- Do not implement general image or SVG compositing.
- Do not silently flatten composites into a CPU-rendered full text texture.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/05-color-fonts-bitmap-svg-emoji.md`
- `.upstream/specs/pure-kotlin-text/06-gpu-renderer-handoff.md`
- `.upstream/specs/gpu-renderer/21-text-glyph-pipeline.md`
- `.upstream/specs/pure-kotlin-text/09-migration-from-current-font-pack.md`

## Design Sketch

```kotlin
sealed interface COLRv1CompositeOp : COLRv1PaintOp {
    data class Transform(
        override val nodeId: PaintNodeId,
        val matrix: Matrix3x3,
        val child: PaintNodeId,
        val transformedBounds: RectF,
    ) : COLRv1CompositeOp

    data class Composite(
        override val nodeId: PaintNodeId,
        val mode: ColorCompositeMode,
        val source: PaintNodeId,
        val destination: PaintNodeId,
        val destinationRead: DestinationReadRequirement,
    ) : COLRv1CompositeOp

    data class ClipBox(
        override val nodeId: PaintNodeId,
        val clipBounds: RectF,
        val child: PaintNodeId,
    ) : COLRv1CompositeOp
}
```

## Acceptance Criteria

- [x] Fixtures cover transform, translate, scale, rotate, skew, composite, and clip nodes as separate operation cases.
- [x] Composite nodes record blend/composite mode and whether a future renderer route needs destination-read or layer isolation.
- [x] Singular transforms, unsupported composite modes, and clip budget overflow emit specific `text.color.*` diagnostics.
- [x] Bounds are deterministic after nested transform and clip operations.
- [x] `coloremoji_blendmodes` remains open until GPU route evidence proves the promoted composite modes.

## Required Evidence

- `reports/font/fixtures/expected/color/color-glyph-plan.json` bundle cases for translate, generic transform, classified scale/rotate/skew, composite+clip, singular-transform fallback, unsupported-composite fallback, and clip-budget fallback.
- `reports/font/fixtures/expected/color/colrv1-paint-graph.json` fixtures for transform, composite, and clip graphs plus refusal diagnostics.
- `reports/font/fixtures/expected/color/color-glyph-composite-plan.json` showing destination-read/layer hints for the composite glyph handoff.
- `reports/pure-kotlin-text/2026-06-17-kfont-m10-004-colrv1-transform-composite-clip.md` and `reports/pure-kotlin-text/coverage-ticket-matrix.md` preserving `coloremoji_blendmodes` as an open gate until renderer evidence exists.

## Fallback / Refusal Behavior

- Unsupported composite behavior refuses the color glyph route or falls back to monochrome only when the fallback policy explicitly permits it.
- The renderer must later refuse unsupported composite lowering rather than CPU-rendering full text to a texture.
- Legacy gate `coloremoji_blendmodes` remains open until implementation evidence, diagnostics, and GPU dashboard updates are linked.

## Dashboard Impact

- Expected row: `COLRv1 transform/composite/clip operations`.
- Expected classification: `tracked-gap`.
- Claim promotion allowed: no, unless operation fixtures and downstream GPU evidence are linked.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:glyph:test --tests org.graphiks.kanvas.glyph.color.ColorGlyphSurfaceTest.buildsCOLRV1TransformCompositeClipPlansAndDeterministicPaintGraphDumps --tests org.graphiks.kanvas.glyph.color.ColorGlyphSurfaceTest.refusesCOLRV1SingularTransformUnsupportedCompositeModeAndClipBudgetOverflowWhenFallbackAllowed
rtk python3 scripts/validate_font_fixture_assets.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk python3 scripts/validate_pure_kotlin_text_dump_index.py
```

## Status Notes

- `done`: Fresh deterministic CPU planning evidence covers bounded transform/composite/clip cases, with checked-in plan/graph/composite dumps and validation; the legacy gate `coloremoji_blendmodes` remains open until CPU oracle and GPU route evidence land.
- This ticket does not claim dedicated `PaintScale` / `PaintRotate` / `PaintSkew` parse-format support beyond classified generic `PaintTransform` matrices.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M10`
- `area:color`
- `claim:tracked-gap`
- `legacy:coloremoji_blendmodes`
