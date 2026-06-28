---
id: KGPU-M33-002
title: "Advanced stroke expansion — complex dash and path-effect chain"
status: review
milestone: M33
priority: P1
owner_area: geometry
claim_impact: TargetNative
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M1-001]
legacy_gate: legacy stroke
---

# KGPU-M33-002 - Advanced stroke expansion — complex dash and path-effect chain

## PM Note

Les strokes complexes (dash patterns > 2 éléments, chaînes de path effects) étaient refusés. Ce ticket active leur acceptation conditionnelle ou leur refus stable avec diagnostics.

## Problem

GPUStrokeExpansionPlan currently refuses complex dashes and path-effect chains. 25-path-stroke-geometry-pipeline.md defines GPUComplexDashPlan, GPUPathEffectChainPlan, GPUStrokeStyleCompositionPlan as TargetNative without implementation.

## Scope

- GPUComplexDashPlan (dash array, phase, classification simple/complex).
- GPUPathEffectChainPlan (ordered chain: corner, discrete, sum, compose).
- GPUStrokeStyleCompositionPlan (width+cap+join+miter+dash+path-effect → executable plan).
- Routes: simple dash → GPUNative, complex dash → GPUNative or CPUPreparedGPU, path-effect chain → CPUPreparedGPU or RefuseDiagnostic.

## Non-Goals

- Not all Skia path effects.
- No full CPU stroke rendering into texture.

## Spec Sources

- `.upstream/specs/gpu-renderer/25-path-stroke-geometry-pipeline.md` (Advanced Stroke Expansion)
- `.upstream/specs/gpu-renderer/36-implementation-roadmap.md`

## Graphite Algorithm References

- [`GFX-TESSELLATE-STROKES`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-tessellate-strokes) - source [TessellateStrokesRenderStep.cpp:91](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/render/TessellateStrokesRenderStep.cpp:91); Tessellate stroked paths with cap/join handling, cusp handling for quads/conics/cubics, transform scale, and stroke-width uniforms.
- [`GFX-SHAPE-ROUTING-HEURISTICS`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-shape-routing-heuristics) - source [Device.cpp:1900](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/Device.cpp:1900); Route subruns, vertices, coverage masks, edge-AA quads, simple rect/rrects, and tessellated paths based on transform, style, and capabilities.
- [`GFX-RENDERER-STRATEGY`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-renderer-strategy) - source [RendererProvider.cpp:80](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/RendererProvider.cpp:80); Choose a path strategy from capabilities, preferring compute when available, then tessellation/small-atlas, then raster atlas.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
data class GPUComplexDashPlan(
    val dashArray: FloatArray,
    val dashPhase: Float,
    val classification: GPUDashClassification,
)

enum class GPUDashClassification {
    SimpleRepeat,
    ComplexPattern,
    UnsupportedLength,
}

data class GPUPathEffectChainPlan(
    val effects: List<GPUPathEffectDescriptor>,
    val maxDepthReached: Boolean,
)

data class GPUStrokeStyleCompositionPlan(
    val width: Float,
    val cap: GPUStrokeCap,
    val join: GPUStrokeJoin,
    val miterLimit: Float,
    val dashPlan: GPUComplexDashPlan?,
    val pathEffectChain: GPUPathEffectChainPlan?,
)
```

## Acceptance Criteria

- [ ] 4-element dash with phase offset accepted or refused with stable reason.
- [ ] Corner+discrete path-effect chain accepted or refused.
- [ ] Dash+path-effect combination produces correct stroke (CPU oracle parity).

## Required Evidence

- GPUComplexDashPlan dumps (simple + complex).
- GPUPathEffectChainPlan dump (2-effect chain).
- GPUStrokeStyleCompositionPlan dump.
- CPU oracle comparison for complex dash.
- Refusal fixtures: dash length exceeded, path-effect depth exceeded.

## Fallback / Refusal Behavior

- Dash length exceeded → `unsupported.stroke.dash_pattern_length`.
- Path-effect depth exceeded → `unsupported.stroke.path_effect_chain_depth`.
- No CPU texture fallback.

## Dashboard Impact

- Expected row: `gpu-renderer.geometry.advanced-stroke`
- Expected classification: `TargetNative`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*AdvancedStroke*'
```

## Status Notes

- `proposed`: Initial ticket. Awaiting M33 milestone acceptance.
- `proposed → ready` (2026-06-28): M33-001 tessellation baseline available for reuse.
- `ready → review` (2026-06-28): dash expansion, path effect chain, advanced stroke plan implemented.

## Linear Labels

- `gpu-renderer`
- `milestone:M33`
- `area:geometry`
