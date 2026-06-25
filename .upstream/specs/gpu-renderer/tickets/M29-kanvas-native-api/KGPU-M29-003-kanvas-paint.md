---
id: KGPU-M29-003
title: "KanvasPaint — color, shader, blendMode, colorFilter, stroke"
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

# KGPU-M29-003 - KanvasPaint — color, shader, blendMode, colorFilter, stroke

## PM Note

`KanvasPaint` est le descripteur de style pour toutes les operations de dessin
Kanvas. Il remplace `SkPaint` dans l'API native et definit la couleur, le shader,
le mode de fusion, le filtre de couleur et le style de trait. Ce ticket donne au
PM le controle visuel complet sans Skia.

## Problem

Drawing operations on `KanvasCanvas` need paint descriptors for color, shaders,
blend modes, color filters, and stroke styling. Without `KanvasPaint`, every draw
call would need raw GPU state, making the API unusable.

## Scope

- Define `KanvasPaint` data class with all Skia-equivalent paint fields
- Implement `Color` (RGBA with color space)
- Implement `BlendMode` enum (Clear, Src, Dst, SrcOver, DstOver, etc.)
- Implement `ColorFilter` sealed class (Matrix, Lighting, etc.)
- Implement `Stroke` params (width, cap, join, miterLimit)
- Map `KanvasPaint` fields to GPU render state (uniforms, pipeline keys)

## Non-Goals

- No shader implementations (KGPU-M29-005)
- No runtime effect integration (M21, M7)
- No image filter or mask filter (future milestone)
- No text-specific paint fields

## Spec Sources

- `.upstream/specs/gpu-renderer/README.md`
- `.upstream/specs/gpu-renderer/03-material-key-wgsl.md`
- `.upstream/specs/gpu-renderer/12-blend-color-target-state.md`
- `.upstream/specs/gpu-renderer/31-material-source-paint-pipeline.md`

## Design Sketch

```kotlin
data class KanvasPaint(
    val color: Color = Color.BLACK,
    val shader: KanvasShader? = null,
    val blendMode: BlendMode = BlendMode.SrcOver,
    val colorFilter: ColorFilter? = null,
    val stroke: Stroke? = null,
    val alpha: Float = 1.0f,
    val antiAlias: Boolean = true,
)
```

## Acceptance Criteria

- [ ] `KanvasPaint` compiles with all fields
- [ ] `BlendMode` enum covers the standard Porter-Duff set
- [ ] `ColorFilter` sealed class compiles
- [ ] `Stroke` params compile (width, cap, join, miterLimit)
- [ ] Paint-to-GPU-state mapping produces valid uniform/material keys

## Required Evidence

- `KanvasPaint.kt`, `BlendMode.kt`, `ColorFilter.kt`, `Stroke.kt` committed
- Paint-to-uniform dump transcript for solid color, shader, blend, and stroke paints
- Diagnostic output for unsupported paint combinations

## Fallback / Refusal Behavior

Unsupported blend modes or color filters emit `unsupported-paint-property`
diagnostics. The draw is refused rather than silently degraded. No CPU fallback
to Skia paint interpretation.

## Dashboard Impact

- Expected row: `gpu-renderer.m29.kanvas-paint`
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
