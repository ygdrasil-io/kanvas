---
id: KGPU-M8-001
title: "Add `DrawVertices` descriptor and route decisions"
status: done
milestone: M8
priority: P1
owner_area: vertices
claim_impact: TargetNative
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M2-002, KGPU-M7-003]
legacy_gate: "vertices legacy"
---

# KGPU-M8-001 - Add `DrawVertices` descriptor and route decisions

## PM Note

Ce ticket donne une forme typée aux vertices avant toute route GPU.

## Problem

Vertices need immutable descriptors for topology, colors, texcoords, indices,
materials, and refusal diagnostics.

## Scope

- Add `GPUVerticesDescriptor` route decision evidence.
- Add refusals for unsupported topology, missing buffers, and blend/color cases.

## Non-Goals

- No 3D engine semantics.
- No CPU-rasterized mesh texture fallback.

## Spec Sources

- `.upstream/specs/gpu-renderer/26-draw-vertices-mesh-pipeline.md`

## Graphite Algorithm References

- [`GFX-VERTICES-STEP`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-vertices-step) - source [VerticesRenderStep.cpp:71](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/render/VerticesRenderStep.cpp:71); Study vertices variants by primitive type, color, and texcoords.
- [`GFX-SHAPE-ROUTING-HEURISTICS`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-shape-routing-heuristics) - source [Device.cpp:1900](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/Device.cpp:1900); Reference draw routing branches for vertices versus shape/image families.
- [`GFX-PAINTPARAMS-TO-KEY`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-paintparams-to-key) - source [PaintParams.cpp:222](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/PaintParams.cpp:222); Use primitive color and final blend lowering for descriptor decisions.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
data class VerticesRouteEvidence(val descriptorDump: String, val routeKind: String)
```

## Acceptance Criteria

- [x] Descriptor and route dumps are deterministic.
- [x] Unsupported vertices cases refuse stably.
- [x] Route kind is explicit.

## Required Evidence

- Descriptor, route, key, and refusal dumps.

## Fallback / Refusal Behavior

Unsupported vertices routes refuse; no rendered texture fallback.

## Dashboard Impact

- Expected row: `gpu-renderer.vertices.descriptor`
- Expected classification: `TargetNative`
- Claim promotion allowed: no until reviewed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk git diff --check
```

## Status Notes

- `done`: KGPU-M2-002 and KGPU-M7-003 are `done` on current `master`.
  This branch adds contract-only `GPUVerticesRouteDecisionPlanner` evidence for
  typed `GPUVertexMode` descriptors, deterministic descriptor/layout/key/route
  dumps, stable refusals for topology, triangle fan, nondeterministic source,
  non-finite positions, vertex/index budgets, attribute/color format,
  texcoord/local-coordinate, primitive blender, primitive-blend destination-read,
  and missing WGSL/layout evidence cases. The evidence row remains
  `gpu-renderer.vertices.descriptor` with `routeKind=GPUNative` or
  `RefuseDiagnostic`, `classification=TargetNative`, and no promotion,
  materialization, vertex/index upload, `DrawVertices` product support,
  primitive blender support, texcoord material support, mesh support, or CPU
  rendered texture fallback. Independent review
  `019ed5c8-898d-7923-83b6-f8c82775d12e` found no P0/P1/P2 blockers, no hidden
  product claim, and no status-count issue.
- Evidence: `VerticesRouteDecisionTest` plus
  `reports/gpu-renderer/2026-06-17-m8-001-vertices-route-decisions.md`.

## Linear Labels

- `gpu-renderer`
- `milestone:M8`
- `area:vertices`
