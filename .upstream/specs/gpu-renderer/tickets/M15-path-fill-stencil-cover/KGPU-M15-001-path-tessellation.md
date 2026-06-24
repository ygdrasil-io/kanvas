---
id: KGPU-M15-001
title: "Add path tessellation: flatten + fan triangulation -> WebGPU vertex buffer"
status: proposed
milestone: M15
priority: P0
owner_area: geometry-passes
claim_impact: TargetNative
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M12-010]
legacy_gate: null
---

# KGPU-M15-001 - Add path tessellation: flatten + fan triangulation -> WebGPU vertex buffer

## PM Note

La tessellation de chemin est le coeur du rendu vectoriel GPU. Sans un flatten fiable et une triangulation en éventail, aucun chemin arbitraire ne peut être rempli sur GPU.

## Problem

Arbitrary paths need to be tessellated into GPU-renderable triangles through curve flattening and fan triangulation. Without tessellation, only simple rect/rrect primitives can be rendered on GPU.

## Scope

- Add curve flattening with adaptive tolerance for quad/cubic/conic beziers
- Add fan triangulation from flattened contours
- Add WebGPU vertex buffer creation from tessellated output
- Produce tessellation output dumps for validation shapes

## Non-Goals

- No GPU compute tessellation
- No path atlas generation
- 256-edge budget per path

## Spec Sources

- .upstream/specs/gpu-renderer/README.md

## Graphite Algorithm References

- [`GFX-GFX_TESSELLATE_WEDGES`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-tessellate-wedges) - source src/gpu/tessellate/GrPathTessellation.cpp; Algorithm to study for this ticket's domain.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
class PathTessellator { fun flatten(path: Path, tolerance: Float): List<Point>; fun triangulate(flattened: List<Point>): TriangleList }
```

## Acceptance Criteria

- [ ] Curve flattening produces vertex count within budget for test paths
- [ ] Fan triangulation produces valid non-overlapping triangles
- [ ] Vertex buffer is correctly uploaded and bindable in WebGPU

## Required Evidence

- Tessellation output dumps for circle, star, and arbitrary path shapes
- Vertex count telemetry per path complexity
- WebGPU vertex buffer validation transcript

## Fallback / Refusal Behavior

Paths exceeding 256-edge budget emit stable diagnostic and refuse GPU path route.

## Dashboard Impact

- Expected row: `gpu-renderer.m15.path-tessellation`
- Expected classification: `TargetNative`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*PathTessellat*'
```

## Status Notes

- `proposed`: Initial ticket.

## Linear Labels

- `gpu-renderer`
- `milestone:M15`
- `area:geometry-passes`
