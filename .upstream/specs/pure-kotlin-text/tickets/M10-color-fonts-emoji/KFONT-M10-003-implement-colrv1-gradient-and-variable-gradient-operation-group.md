---
id: "KFONT-M10-003"
title: "Implement COLRv1 gradient and variable-gradient operation group"
status: "done"
milestone: "M10"
priority: "P1"
owner_area: "color"
claim_impact: "tracked-gap"
depends_on: ["KFONT-M10-002"]
legacy_gate: null
---

# KFONT-M10-003 - Implement COLRv1 gradient and variable-gradient operation group

## PM Note

Ce ticket sépare les gradients COLRv1 des autres opérations pour prouver précisément les couleurs variables.

## Problem

COLRv1 gradients affect rendering semantics, bounds, color interpolation, variation data, and later GPU material planning. The missing slice is support for `PaintLinearGradient`, `PaintRadialGradient`, `PaintSweepGradient`, and variable gradient stops, with dumps that expose stop ordering, palette resolution, coordinate spaces, and unsupported variation cases.

## Scope

- Implement COLRv1 linear, radial, and sweep gradient paint nodes with deterministic stop ordering and color resolution.
- Support variable gradient stops when the required variation data is available and diagnosed when it is absent or malformed.
- Record gradient geometry, color line facts, extend mode, palette references, variation coordinates, and bounds in `colrv1-paint-graph.json`.
- Add budget checks for maximum gradient stops and malformed coordinate payloads.
- Emit renderer-neutral `ColorGlyphPlan` facts for later M11 lowering; no GPU material route is claimed here.

## Non-Goals

- Do not implement transforms, composites, clips, layers, or destination-read behavior in this ticket.
- Do not implement a general gradient renderer outside glyph-scoped COLRv1 plans.
- Do not create WGSL or renderer material keys.
- Do not accept external engine gradient output as normative proof.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/05-color-fonts-bitmap-svg-emoji.md`
- `.upstream/specs/pure-kotlin-text/07-validation-conformance-and-drift.md`
- `.upstream/specs/pure-kotlin-text/08-performance-budgets-and-telemetry.md`

## Design Sketch

```kotlin
data class COLRv1GradientPaintOp(
    val nodeId: PaintNodeId,
    val kind: GradientKind,
    val colorLine: List<ResolvedGradientStop>,
    val geometry: GradientGeometry,
    val extendMode: GradientExtendMode,
    val variationStatus: VariationGradientStatus,
    val bounds: RectF,
)

data class ResolvedGradientStop(
    val offset: Float,
    val color: ResolvedGlyphColor,
    val paletteEntry: PaletteEntryRef?,
    val variationDelta: ColorStopVariationDelta?,
)
```

## Acceptance Criteria

- [x] Fixtures cover linear, radial, and sweep gradients with stable stop order and resolved colors.
- [x] Variable gradient stops include variation coordinates and deltas in the dump when supported.
- [x] Missing or malformed variation data emits `text.color.COLRv1-paint-unsupported` or a narrower variable-gradient diagnostic.
- [x] Gradient stop budget overflow emits `text.color.COLRv1-budget-exceeded`.
- [x] The color glyph plan remains renderer-neutral and does not create WGSL, material keys, or GPU resources.

## Required Evidence

- `colrv1-paint-graph.json` fixtures for linear, radial, sweep, and variable-stop gradients.
- `color-glyph-plan.json` fixture showing gradient nodes embedded in a glyph plan.
- Refusal fixtures for malformed gradient coordinates, excessive stop count, and missing variation data.

## Fallback / Refusal Behavior

- Unsupported gradient nodes refuse the color route unless monochrome fallback is explicitly accepted by the style and route policy.
- Variable-gradient failure must not be downgraded to a non-variable gradient without a diagnostic.
- Gradient evidence remains `tracked-gap` until operation-specific fixtures exist.

## Dashboard Impact

- Expected row: `COLRv1 gradient operation group`.
- Expected classification: `tracked-gap`.
- Claim promotion allowed: no, unless gradient and variable-gradient evidence is attached.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:glyph:test --tests '*COLRv1*Gradient*'
```

## Status Notes

- `done`: `COLRV1ColorGlyphPlanner` now records linear/radial/sweep geometry, resolved gradient stops, variation coordinates for supported variable stops, and stable refusal diagnostics for missing variation data, malformed coordinates, and gradient-stop budget overflow.
- Remaining non-claims stay explicit: this ticket does not claim `PaintVarLinearGradient` / `PaintVarRadialGradient` / `PaintVarSweepGradient` geometry support, transform/composite/clip execution, bitmap/SVG routing, emoji planning, WGSL generation, or GPU material execution.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M10`
- `area:color`
- `claim:tracked-gap`
