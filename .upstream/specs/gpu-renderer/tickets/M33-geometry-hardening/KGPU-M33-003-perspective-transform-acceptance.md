---
id: KGPU-M33-003
title: "Perspective transform acceptance for rect/rrect geometry"
<<<<<<< HEAD
status: done
=======
status: proposed
>>>>>>> master
milestone: M33
priority: P1
owner_area: coordinates
claim_impact: TargetNative
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M1-001]
legacy_gate: legacy drawRect
---

# KGPU-M33-003 - Perspective transform acceptance for rect/rrect geometry

## PM Note

Les transformations perspective étaient systématiquement refusées. Ce ticket active l'acceptation conditionnelle pour rect/rrect + solid color.

## Problem

30-coordinate-transform-bounds-policy.md defines GPUPerspectiveTransformPlan and GPUPerspectiveBoundsProof as TargetNative but current route refuses all perspective.

## Scope

- GPUPerspectiveTransformPlan (4x4 matrix classification, finite determinant, w-divide).
- GPUPerspectiveBoundsProof (4-corner projection → conservative device bounds).
- Route: perspective+rect/rrect+solid → GPUNative with homogeneous divide in vertex shader.
- Routes that refuse: path, text, image, filter, layer.

## Non-Goals

- No general 3D clipping.
- No perspective-correct interpolation before consuming route.
- No perspective claim for text/image/filter/layer.

## Spec Sources

- `.upstream/specs/gpu-renderer/30-coordinate-transform-bounds-policy.md` (Perspective Acceptance Policy)

## Graphite Algorithm References

- [`GFX-SHAPE-ROUTING-HEURISTICS`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-shape-routing-heuristics) - source [Device.cpp:1900](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/Device.cpp:1900); Route subruns, vertices, coverage masks, edge-AA quads, simple rect/rrects, and tessellated paths based on transform, style, and capabilities.
- [`GFX-SIMPLE-SHAPE-BOUNDS`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-simple-shape-bounds) - source [Device.cpp:248](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/Device.cpp:248); Identify pixel-aligned rects, simple rect/rrect/line shapes, and inner-fill bounds that justify a second non-AA fill for overdraw reduction.
- [`GFX-DRAWGEOMETRY-ROUTING`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-drawgeometry-routing) - source [Device.cpp:1512](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/Device.cpp:1512); Compute clipped bounds, pick a renderer or atlas, derive paint keys, handle atlas insertion, and split stroke/fill/inner-fill draws.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
data class GPUPerspectiveTransformPlan(
    val matrix: FloatArray, // 4x4
    val finiteDeterminant: Boolean,
    val wDivideSign: Float,
)

data class GPUPerspectiveBoundsProof(
    val projectedBounds: Rect,
    val allCornersFinite: Boolean,
    val behindCamera: Boolean,
)

enum class GPUPerspectiveRouteAcceptance {
    Accepted,
    RefusedAffineOnly,
    RefusedDegenerate,
}
```

## Acceptance Criteria

- [ ] rect+solid+perspective → correct with CPU oracle parity.
- [ ] rrect+solid+perspective → correct with CPU oracle parity.
- [ ] path+perspective → `RefuseDiagnostic`.
- [ ] behind-camera → `RefuseDiagnostic`.
- [ ] near-zero determinant → `RefuseDiagnostic`.

## Required Evidence

- GPUPerspectiveTransformPlan dump.
- GPUPerspectiveBoundsProof dump.
- CPU oracle comparison for perspective rect.
- Refusal fixtures: path+perspective, behind-camera, degenerate projection.

## Fallback / Refusal Behavior

- Affine-only route → `unsupported.transform.perspective_route_rejected.<name>`.
- Behind-camera → `unsupported.transform.perspective_degenerate`.
- No CPU texture fallback.

## Dashboard Impact

- Expected row: `gpu-renderer.coordinates.perspective-acceptance`
- Expected classification: `TargetNative`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*PerspectiveTransform*'
```

## Status Notes

- `proposed`: Initial ticket. Awaiting M33 milestone acceptance.
<<<<<<< HEAD
- `proposed → ready` (2026-06-28): M33-001 tessellation baseline available.
- `ready → review` (2026-06-28): perspective transform plan implemented for rect/rrect + solid color.
- `review → done` (2026-06-28): fixes applied (WGSL syntax, wgsl4k wire, registry, oracle, refusal codes, matrix math, classification, depth limit). Independent review passed.
=======
>>>>>>> master

## Linear Labels

- `gpu-renderer`
- `milestone:M33`
- `area:coordinates`
