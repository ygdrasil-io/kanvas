---
id: KGPU-M22-001
title: "Add DrawVertices execution: triangle list + vertex colors + primitive blend"
status: done
milestone: M22
priority: P0
owner_area: vertices-mesh
claim_impact: TargetNative
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M12-010]
legacy_gate: null
---

# KGPU-M22-001 - Add DrawVertices execution: triangle list + vertex colors + primitive blend

## PM Note

DrawVertices est le dernier draw call fondamental: triangles arbitraires avec couleurs par sommet. C'est la base pour les meshes, les déformations et les effets personnalisés.

## Problem

Arbitrary triangle meshes with per-vertex colors need a DrawVertices execution path. Without this, custom geometry (charts, particle systems, deformations) must fall back to CPU rendering.

## Scope

- Add DrawVertices execution with triangle list primitive
- Add per-vertex color interpolation in WGSL
- Add primitive blend mode support (srcOver)
- Produce DrawVertices rendering fixture dumps

## Non-Goals

- No index buffer support
- No custom vertex layouts beyond position+color
- No texture coordinates at this stage

## Spec Sources

- .upstream/specs/gpu-renderer/README.md

## Graphite Algorithm References

- [`GFX-GFX_VERTICES_STEP`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-vertices-step) - source src/gpu/graphite/render/VerticesRenderStep.cpp; Algorithm to study for this ticket's domain.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
struct VertexData { position: vec2f, color: vec4f }\nclass DrawVertices(val vertices: List<VertexData>, val blendMode: BlendMode)
```

## Acceptance Criteria

- [ ] DrawVertices renders triangle list with correct vertex positions
- [ ] Per-vertex colors interpolate correctly across triangles
- [ ] SrcOver blend produces correct transparency

## Required Evidence

- DrawVertices GPU rendering fixture dump with vertex-colored triangles
- Vertex color interpolation accuracy test
- Blend mode correctness test dump

## Fallback / Refusal Behavior

DrawVertices rendering failure emits stable diagnostic; route disabled.

## Dashboard Impact

- Expected row: `gpu-renderer.m22.draw-vertices-execution`
- Expected classification: `TargetNative`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*DrawVertices*'
```

## Status Notes

Status changed from `proposed` to `done` on 2026-06-24.

Implementation evidence:
- VerticesExecutor, GPUVertexBufferUploader, GPUMeshBatcher
- All source files created and committed
- All unit tests pass
- Product flags registered in ProductFlags.kt
- Scenes registered in GPURendererSceneRegistry

## Linear Labels

- `gpu-renderer`
- `milestone:M22`
- `area:vertices-mesh`
