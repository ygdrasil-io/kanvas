---
id: KGPU-M5-003
title: "Add simple filter render node route"
status: blocked
milestone: M5
priority: P0
owner_area: filters
claim_impact: TargetNative
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M5-001]
legacy_gate: "filter legacy"
---

# KGPU-M5-003 - Add simple filter render node route

## PM Note

Ce ticket ajoute un premier nœud de filtre GPU borné avec ses intermédiaires.

## Problem

Filters require graph, bounds, crop, intermediate, resource, and route evidence
before any support claim.

## Scope

- Add one simple filter node route.
- Add intermediate texture ownership and bounds diagnostics.

## Non-Goals

- Do not add arbitrary filter DAGs.
- Do not add runtime-effect filters unless descriptor route is accepted.

## Spec Sources

- `.upstream/specs/gpu-renderer/23-filter-effect-pipeline.md`

## Graphite Algorithm References

- [`GFX-FILTER-BACKEND`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-filter-backend) - source [TextureUtils.cpp:720](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/TextureUtils.cpp:720); Study scratch-device and special-image hooks used by filter execution.
- [`GFX-FILTER-RESOLVE`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-filter-resolve) - source [SkImageFilterTypes.cpp:1334](/Users/chaos/workspace/kanvas-forge/skia-main/src/core/SkImageFilterTypes.cpp:1334); Reference resolve-versus-deferred shader decisions for simple filter nodes.
- [`GFX-SPECIAL-IMAGE-LAYER`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-special-image-layer) - source [SpecialImage_Graphite.cpp:20](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/SpecialImage_Graphite.cpp:20); Use special image subset ownership as intermediate evidence vocabulary.
- [`GFX-SPECIAL-DRAW`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-special-draw) - source [Device.cpp:2180](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/Device.cpp:2180); Reference how snapped filter/layer results are drawn back into a device.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
data class FilterNodeEvidence(val nodePlan: String, val intermediatePlan: String)
```

## Acceptance Criteria

- [ ] Filter graph and node plan dumps are linked.
- [ ] Intermediate ownership is explicit.
- [ ] Unsupported nodes refuse.

## Required Evidence

- Filter graph, node, bounds, intermediate, route, and refusal dumps.

## Fallback / Refusal Behavior

Unsupported filters refuse; no CPU-rendered filter texture fallback.

## Dashboard Impact

- Expected row: `gpu-renderer.filter.simple-node`
- Expected classification: `TargetNative`
- Claim promotion allowed: no until reviewed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk git diff --check
```

## Status Notes

- `blocked`: Depends on KGPU-M5-001 and requires accepted saveLayer/offscreen
  target ownership plus native WebGPU/adapter evidence for a bounded filter
  render node, provider-owned intermediate texture ownership, bounds/crop/tile
  diagnostics, read/write aliasing refusal, WGSL/render-node binding
  validation, and CPU/GPU/reference comparison before any simple filter
  `GPUNative` route claim. Runtime-effect filters remain gated by M7
  descriptors.

## Linear Labels

- `gpu-renderer`
- `milestone:M5`
- `area:filters`
