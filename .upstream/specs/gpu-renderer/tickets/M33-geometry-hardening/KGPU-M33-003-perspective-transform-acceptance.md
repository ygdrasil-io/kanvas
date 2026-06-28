---
id: KGPU-M33-003
title: "Perspective transform acceptance for rect/rrect geometry"
status: proposed
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

- `GFX-TRANSFORM` from `GRAPHITE-ALGORITHM-REFERENCES.md` — matrix classification and bounds projection. Algorithm reference only.

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

## Linear Labels

- `gpu-renderer`
- `milestone:M33`
- `area:coordinates`
