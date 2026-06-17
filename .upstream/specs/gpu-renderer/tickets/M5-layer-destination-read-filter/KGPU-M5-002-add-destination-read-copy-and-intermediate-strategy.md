---
id: KGPU-M5-002
title: "Add destination-read copy and intermediate strategy"
status: done
milestone: M5
priority: P0
owner_area: destination-read
claim_impact: TargetNative
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M5-001]
legacy_gate: "blend legacy"
---

# KGPU-M5-002 - Add destination-read copy and intermediate strategy

## PM Note

Ce ticket rend les lectures destination explicites et interdit l’échantillonnage
de l’attachement actif.

## Problem

Destination-dependent blends and filters need accepted copy/intermediate/layer
isolation strategies or stable refusals.

## Scope

- Add `GPUDestinationReadPlan` for copy and intermediate strategies.
- Add active-attachment sampling refusal evidence.

## Non-Goals

- Do not assume framebuffer fetch.
- Do not support all blend modes.

## Spec Sources

- `.upstream/specs/gpu-renderer/20-destination-read-strategy.md`

## Graphite Algorithm References

- [`GFX-DST-USAGE`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-dst-usage) - source [PaintParams.cpp:51](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/PaintParams.cpp:51); Separate destination dependency, destination-read, advanced blend, and renderer-only destination use.
- [`GFX-DST-READ-COPY`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-dst-read-copy) - source [DrawContext.cpp:270](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/DrawContext.cpp:270); Study explicit destination copy insertion before a pass that reads destination pixels.
- [`GFX-DRAWCONTEXT-RECORD`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-drawcontext-record) - source [DrawContext.cpp:155](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/DrawContext.cpp:155); Reference barrier selection before destination-dependent draws are recorded.
- [`GFX-DRAWLIST-LAYER`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-drawlist-layer) - source [DrawListLayer.cpp:48](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/DrawListLayer.cpp:48); Use layered batching constraints for destination-dependent ordering.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
data class DestinationReadEvidence(val strategy: String, val bounds: String)
```

## Acceptance Criteria

- [x] Destination-read bounds and resource binding are dumpable.
- [x] Active-attachment sampling refuses.
- [x] Strategy maps to one route kind.

## Required Evidence

- Copy/intermediate plan, resource, and refusal dumps.

## Fallback / Refusal Behavior

Unsupported destination reads refuse rather than silently changing blend
semantics.

## Dashboard Impact

- Expected row: `gpu-renderer.destination-read.strategy`
- Expected classification: `TargetNative`
- Claim promotion allowed: no for this contract-only gate.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk git diff --check
```

## Status Notes

- `done`: `GPUDestinationReadStrategyPlanner` adds accepted contract-gate
  evidence for
  the `gpu-renderer.destination-read.strategy` row with dumpable
  destination-read bounds, target-copy descriptor, existing-intermediate route,
  binding/layout hashes, pass split/copy-before-sample ordering, budget facts,
  accepted-route diagnostics, material-key exclusion, unsupported strategy
  refusals, strategy/action mismatch refusals, and unsupported-variant refusals.
  The gate records
  `routeKind=GPUNative`, `classification=TargetNative`, `promoted=false`,
  `productActivation=false`, and `materialized=false`; it does not claim
  adapter-backed native destination-read execution, framebuffer fetch,
  input-attachment support, CPU readback fallback, or product activation.
- Evidence: `DestinationReadStrategyGateTest` plus
  `reports/gpu-renderer/2026-06-17-m5-002-destination-read-strategy-gate.md`.
- Dependency note: KGPU-M5-001 is `done` on current `master`; KGPU-M5-002 status
  is no longer dependency-blocked. Support promotion remains blocked on native
  adapter-backed destination-read copy/intermediate execution evidence.

## Linear Labels

- `gpu-renderer`
- `milestone:M5`
- `area:destination-read`
