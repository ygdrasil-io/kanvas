---
id: KGPU-M22-003
title: "Add mesh batching: sort + merge draw calls by pipeline key"
status: proposed
milestone: M22
priority: P0
owner_area: recording
claim_impact: ImplementationCandidate
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: false
depends_on: [KGPU-M22-001]
legacy_gate: null
---

# KGPU-M22-003 - Add mesh batching: sort + merge draw calls by pipeline key

## PM Note

Le batching de meshes réduit le nombre de draw calls en fusionnant les appels compatibles. C'est une optimisation critique pour les scènes avec beaucoup de meshes.

## Problem

Multiple mesh draws with identical pipeline state should be batched together to reduce draw call count and GPU state changes. Without batching, each mesh incurs its own draw call overhead.

## Scope

- Add mesh draw call sorting by pipeline key
- Add merge logic for compatible draw calls (same pipeline, same textures)
- Add state change detection to trigger batch flushes
- Produce mesh batching telemetry

## Non-Goals

- No mesh instancing
- No indirect or GPU-driven draw call generation

## Spec Sources

- .upstream/specs/gpu-renderer/README.md

## Graphite Algorithm References

- [`GFX-GFX_DRAWLIST_SORT`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-drawlist-sort) - source src/gpu/graphite/DrawList.cpp sort; Algorithm to study for this ticket's domain.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
class MeshBatcher { fun sort(draws: List<MeshDraw>): List<MeshBatch>; fun canMerge(a: MeshDraw, b: MeshDraw): Boolean }
```

## Acceptance Criteria

- [ ] Same-pipeline mesh draws are sorted together
- [ ] Compatible draws are merged into single draw call
- [ ] Pipeline state changes correctly trigger batch flushes
- [ ] Batching does not violate painter order

## Required Evidence

- Mesh batching telemetry: draw count before vs after batching
- Batch merge decision transcript
- Painter order preservation validation

## Fallback / Refusal Behavior

Batching optimization failure falls back to individual draw calls; no correctness loss.

## Dashboard Impact

- Expected row: `gpu-renderer.m22.mesh-batching`
- Expected classification: `ImplementationCandidate`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*MeshBatch*'
```

## Status Notes

- `proposed`: Initial ticket.

## Linear Labels

- `gpu-renderer`
- `milestone:M22`
- `area:recording`
