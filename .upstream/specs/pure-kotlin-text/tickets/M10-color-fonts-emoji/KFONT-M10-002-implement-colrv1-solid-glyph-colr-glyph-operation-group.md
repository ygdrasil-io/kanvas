---
id: "KFONT-M10-002"
title: "Implement COLRv1 solid/glyph/colr-glyph operation group"
status: "done"
milestone: "M10"
priority: "P0"
owner_area: "color"
claim_impact: "tracked-gap"
depends_on: ["KFONT-M10-001"]
legacy_gate: null
---

# KFONT-M10-002 - Implement COLRv1 solid/glyph/colr-glyph operation group

## PM Note

Ce ticket couvre le premier noyau COLRv1: couleurs solides, glyphes référencés et graphes COLR imbriqués.

## Problem

COLRv1 support must be promoted by paint operation groups, not by a single vague "paint graph" claim. The first missing slice is the solid/glyph/colr-glyph group: `PaintSolid`, `PaintVarSolid`, `PaintGlyph`, and `PaintColrGlyph`. These operations establish graph traversal, palette/variation color resolution, nested glyph references, bounds propagation, and refusal behavior for unsupported recursion.

## Scope

- Implement COLRv1 paint graph nodes for `PaintSolid`, `PaintVarSolid`, `PaintGlyph`, and `PaintColrGlyph`.
- Resolve palette colors, variable color deltas when variation data is available, glyph references, nested COLR glyph references, graph node IDs, and bounds.
- Enforce recursion depth and operation-count budgets for this operation group.
- Emit `colrv1-paint-graph.json` and `color-glyph-plan.json` sections for accepted and refused graph walks.
- Add diagnostics for missing glyph references, unsupported variable color data, recursion budget overflow, and malformed graph offsets.

## Non-Goals

- Do not implement gradients, transforms, composites, clips, or cycle fixture expansion in this ticket.
- Do not render the graph to pixels or GPU primitives.
- Do not use Skia output as normative proof.
- Do not silently substitute COLRv0 behavior for COLRv1 graph failures.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/05-color-fonts-bitmap-svg-emoji.md`
- `.upstream/specs/pure-kotlin-text/04-glyph-representation-and-artifacts.md`
- `.upstream/specs/pure-kotlin-text/07-validation-conformance-and-drift.md`

## Design Sketch

```kotlin
sealed interface COLRv1PaintOp {
    val nodeId: PaintNodeId

    data class Solid(
        override val nodeId: PaintNodeId,
        val color: ResolvedGlyphColor,
    ) : COLRv1PaintOp

    data class Glyph(
        override val nodeId: PaintNodeId,
        val glyphId: GlyphId,
        val child: PaintNodeId,
        val glyphBounds: RectF,
    ) : COLRv1PaintOp

    data class ColrGlyph(
        override val nodeId: PaintNodeId,
        val baseGlyphId: GlyphId,
        val childGraph: PaintNodeId,
    ) : COLRv1PaintOp
}
```

## Acceptance Criteria

- [x] Fixtures cover `PaintSolid`, `PaintVarSolid`, `PaintGlyph`, and `PaintColrGlyph` as separate graph nodes with stable node IDs.
- [x] Bounds are computed for referenced glyph nodes and included in the color glyph plan.
- [x] Recursion depth and operation budget overflow emit `text.color.COLRv1-budget-exceeded`.
- [x] Missing or malformed graph offsets emit `text.color.COLR-malformed` with glyph ID and node ID.
- [x] The dump distinguishes accepted solid/glyph operations from unimplemented gradient/transform/composite operations.

## Required Evidence

- `colrv1-paint-graph.json` fixture for an accepted `PaintColrGlyph` -> `PaintGlyph` -> `PaintSolid` walk with stable node IDs, artifact refs, bounds, and resolved palette color.
- `color-glyph-plan.json` bundle fixture linking the existing COLRv0 case and the new COLRv1 accepted/refused cases to typed `ColorGlyphPlan` evidence and stable refusal diagnostics.
- Refusal fixture for unsupported `PaintVarSolid` without variation support while preserving explicit monochrome outline fallback when allowed.

## Fallback / Refusal Behavior

- Unsupported or malformed COLRv1 nodes refuse the color route for the affected glyph range.
- Monochrome fallback is allowed only when `ColorGlyphFallbackPolicy` records the rejected COLRv1 route and accepted outline substitute.
- The ticket remains `tracked-gap` until operation-specific fixtures are reviewed.

## Dashboard Impact

- Expected row: `COLRv1 solid/glyph operation group`.
- Expected classification: `tracked-gap`.
- Claim promotion allowed: no, unless the operation group has paint graph dumps and refusal fixtures.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:glyph:test --tests org.graphiks.kanvas.glyph.color.ColorGlyphSurfaceTest
rtk python3 scripts/validate_font_fixture_assets.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk python3 scripts/validate_pure_kotlin_text_dump_index.py
```

## Status Notes

- `proposed`: First COLRv1 operation group; later tickets add gradients, transforms, composites, clips, and fixtures.
- Move to `ready` only after operation IDs, budget fields, and fallback policy are reviewed.
- `done`: `COLRV1ColorGlyphPlanner` now promotes the solid/glyph/colr-glyph operation group into deterministic `ColorGlyphPlan` and `COLRV1PaintGraphEvidence` dumps with stable node IDs, bounds propagation, palette resolution, explicit `PaintVarSolid` refusal diagnostics, and outline fallback evidence; gradients, transforms, composites, clips, cycle fixtures, bitmap/SVG routes, emoji planning, and GPU execution remain separate non-claims.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M10`
- `area:color`
- `claim:tracked-gap`
