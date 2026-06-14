---
id: KGPU-M8-001
title: "Add `DrawVertices` descriptor and route decisions"
status: proposed
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

## Design Sketch

```kotlin
data class VerticesRouteEvidence(val descriptorDump: String, val routeKind: String)
```

## Acceptance Criteria

- [ ] Descriptor and route dumps are deterministic.
- [ ] Unsupported vertices cases refuse stably.
- [ ] Route kind is explicit.

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

- `proposed`: Descriptor route first.

## Linear Labels

- `gpu-renderer`
- `milestone:M8`
- `area:vertices`
