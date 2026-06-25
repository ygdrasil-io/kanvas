---
id: KGPU-M29-004
title: "KanvasPath — moveTo/lineTo/quadTo/cubicTo/close + fillType"
status: proposed
milestone: M29
priority: P0
owner_area: kanvas-api
claim_impact: ImplementationCandidate
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: false
depends_on: [KGPU-M29-001]
legacy_gate: null
---

# KGPU-M29-004 - KanvasPath — moveTo/lineTo/quadTo/cubicTo/close + fillType

## PM Note

`KanvasPath` est le constructeur de chemins vectoriels natif Kanvas, equivalent
de `SkPath`. Il permet de definir des formes geometriques avec les primitives
standard (moveTo, lineTo, quadTo, cubicTo, close) et un fillType. Ce ticket donne
au PM la creation de formes sans Skia.

## Problem

`KanvasCanvas.drawPath()` needs a path representation. Without `KanvasPath`,
callers cannot define vector shapes for filling, stroking, or clipping through
the native API.

## Scope

- Define `KanvasPath` class with path-building verbs
- Implement `moveTo(x, y)`
- Implement `lineTo(x, y)`
- Implement `quadTo(cpx, cpy, x, y)`
- Implement `cubicTo(cp1x, cp1y, cp2x, cp2y, x, y)`
- Implement `close()`
- Implement `FillType` enum (Winding, EvenOdd, InverseWinding, InverseEvenOdd)
- Emit path verb stream consumable by GPU tessellation

## Non-Goals

- No path simplification or flattening (handled by GPU tessellator)
- No path effects (dash, trim)
- No path ops (union, intersect, diff)
- No path measurement or bounds calculation
- No text-to-path conversion

## Spec Sources

- `.upstream/specs/gpu-renderer/README.md`
- `.upstream/specs/gpu-renderer/19-path-coverage-atlas-strategy.md`
- `.upstream/specs/gpu-renderer/25-path-stroke-geometry-pipeline.md`
- `.upstream/specs/gpu-renderer/tickets/M15-path-fill-stencil-cover/README.md`

## Design Sketch

```kotlin
class KanvasPath {
    fun moveTo(x: Float, y: Float)
    fun lineTo(x: Float, y: Float)
    fun quadTo(cpx: Float, cpy: Float, x: Float, y: Float)
    fun cubicTo(cp1x: Float, cp1y: Float, cp2x: Float, cp2y: Float, x: Float, y: Float)
    fun close()
    var fillType: FillType = FillType.Winding
}

enum class FillType { Winding, EvenOdd, InverseWinding, InverseEvenOdd }
```

## Acceptance Criteria

- [ ] `KanvasPath` compiles with all verb methods
- [ ] `FillType` enum covers the four Winding/EvenOdd variants
- [ ] Path verb stream is serializable to a typed array consumable by GPU
- [ ] Closed and open paths produce correct verb sequences

## Required Evidence

- `KanvasPath.kt` committed
- Path verb dump transcript for a filled triangle, rrect, and cubic curve
- FillType enum committed
- Diagnostic output for empty or unsupported paths

## Fallback / Refusal Behavior

Empty paths emit `empty-path` diagnostic and produce no draw commands. Unsupported
fill types emit `unsupported-fill-type` diagnostic. No CPU tessellation fallback.

## Dashboard Impact

- Expected row: `gpu-renderer.m29.kanvas-path`
- Expected classification: `ImplementationCandidate`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :kanvas:test
rtk ./gradlew --no-daemon :gpu-renderer:test
```

## Status Notes

- `proposed`: Initial ticket.

## Linear Labels

- `gpu-renderer`
- `milestone:M29`
- `area:kanvas`
