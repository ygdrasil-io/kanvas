---
id: KGPU-M33-001
title: "GPU compute tessellation — GPUNative path fill and stroke route"
status: proposed
milestone: M33
priority: P0
owner_area: geometry
claim_impact: TargetNative
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M1-001]
legacy_gate: legacy path fill
---

# KGPU-M33-001 - GPU compute tessellation — GPUNative path fill and stroke route

## PM Note

La tessellation GPU via compute shader remplace le chemin CPUPreparedGPU pour le remplissage et le contour de path. Ce ticket prouve la première route GPUNative pour les paths.

## Problem

Currently all path fill/stroke goes through CPUPreparedGPU (atlas or CPU-prepared geometry). Compute tessellation is spec'd as TargetNative in 25-path-stroke-geometry-pipeline.md but no route exists.

## Scope

- Implement GPUComputeTessellationPlan (dispatch grid, WGSL compute module, output buffer).
- Register GPUComputeTessellationArtifact in CPUPreparedGPUArtifactRegistry.
- Route: PathFill → GPUComputeTessellationPlan when capabilities accept, else CPUPreparedGPU, else RefuseDiagnostic.
- WGSL compute module validated via wgsl4k.
- At least one path fill and one path stroke with CPU oracle parity.

## Non-Goals

- No general compute scheduler.
- No CPU flattening port to compute.
- No MSAA claim from tessellation alone.

## Spec Sources

- `.upstream/specs/gpu-renderer/25-path-stroke-geometry-pipeline.md` (GPU Compute Tessellation)
- `.upstream/specs/gpu-renderer/36-implementation-roadmap.md`
- `.upstream/specs/gpu-renderer/07-validation-conformance.md`

## Graphite Algorithm References

- [`GFX-RENDERER-STRATEGY`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-renderer-strategy) - source [RendererProvider.cpp:80](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/RendererProvider.cpp:80); Choose a path strategy from capabilities, preferring compute when available, then tessellation/small-atlas, then raster atlas.
- [`GFX-SHAPE-ROUTING-HEURISTICS`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-shape-routing-heuristics) - source [Device.cpp:1900](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/Device.cpp:1900); Route subruns, vertices, coverage masks, edge-AA quads, simple rect/rrects, and tessellated paths based on transform, style, and capabilities.
- [`GFX-MSAA-PATH-HEURISTICS`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-msaa-path-heuristics) - source [Device.cpp:2040](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/Device.cpp:2040); Choose tessellated strokes, convex wedges, and switch between stencil wedges and curve+triangle tessellation using verb-count/area heuristics.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
data class GPUComputeTessellationPlan(
    val wgslModule: WGSLComputeModule,
    val dispatchGrid: DispatchGrid,
    val outputBuffer: GPUBufferDescriptor,
    val artifactKey: String,
)

sealed interface GPUComputeTessellationRoute {
    data class Accepted(val artifact: GPUComputeTessellationArtifact) : GPUComputeTessellationRoute
    data class CapabilityUnavailable(val reason: String) : GPUComputeTessellationRoute
    data class Refused(val diagnostic: RefuseDiagnostic) : GPUComputeTessellationRoute
}
```

## Acceptance Criteria

- [ ] GPUComputeTessellationPlan produces valid GPUComputeTessellationArtifact.
- [ ] Compute shader WGSL validates via wgsl4k for at least one path fill.
- [ ] Route fallback: compute unavailable → CPUPreparedGPU → RefuseDiagnostic.
- [ ] CPU oracle parity for path fill and path stroke.

## Required Evidence

- GPUComputeTessellationPlan deterministic dump.
- WGSLComputeModule validation report via wgsl4k.
- GPUComputePipelineKey preimage.
- CPU oracle comparison (checksum or diff) for path fill + path stroke.
- Refusal fixtures: path exceeding vertex budget, compute capabilities absent.

## Fallback / Refusal Behavior

- Compute unavailable → CPUPreparedGPU(PrecomputedGeometryArtifact).
- Vertex budget exceeded → `unsupported.tessellation.vertex_budget_exceeded`.
- WGSL invalid → `unsupported.tessellation.wgsl_validation`.
- No CPU-rendered texture fallback.

## Dashboard Impact

- Expected row: `gpu-renderer.geometry.compute-tessellation`
- Expected classification: `TargetNative`
- Claim promotion allowed: no, unless accepted GPU evidence and CPU oracle parity.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*ComputeTessellation*'
```

## Status Notes

- `proposed`: Initial ticket. Awaiting M33 milestone acceptance.

## Linear Labels

- `gpu-renderer`
- `milestone:M33`
- `area:geometry`
