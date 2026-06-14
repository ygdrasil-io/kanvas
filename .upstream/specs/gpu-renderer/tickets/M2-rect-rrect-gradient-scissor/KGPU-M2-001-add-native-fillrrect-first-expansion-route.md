---
id: KGPU-M2-001
title: "Add native `FillRRect` first expansion route"
status: done
milestone: M2
priority: P0
owner_area: geometry-passes
claim_impact: TargetNative
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M0-007]
legacy_gate: null
---

# KGPU-M2-001 - Add native `FillRRect` first expansion route

## PM Note

Ce ticket étend la première route rect vers les rectangles arrondis sans élargir
le scope produit.

## Problem

RRect fill is a target native route, but it needs its own geometry, coverage,
WGSL/key/payload, resource, and refusal evidence.

## Scope

- Add `FillRRect` normalized command and route fixtures.
- Add accepted solid rrect route-candidate evidence and refusals for
  unsupported per-corner radii, transforms, clips, blends, and target formats.

## Non-Goals

- Do not add path-backed rrects or strokes.
- Do not activate product routing.

## Spec Sources

- `.upstream/specs/gpu-renderer/09-draw-family-support-matrix.md`
- `.upstream/specs/gpu-renderer/14-first-slice-contract.md`
- `.upstream/specs/gpu-renderer/25-path-stroke-geometry-pipeline.md`

## Graphite Algorithm References

- [`GFX-SIMPLE-SHAPE-BOUNDS`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-simple-shape-bounds) - source [Device.cpp:248](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/Device.cpp:248); Use simple-shape and inner-fill classification for the native FillRRect expansion route.
- [`GFX-ANALYTIC-RRECT-STEP`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-analytic-rrect-step) - source [AnalyticRRectRenderStep.cpp:40](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/render/AnalyticRRectRenderStep.cpp:40); Study analytic rrect instance encoding and coverage math for WGSL route design.
- [`GFX-SHAPE-ROUTING-HEURISTICS`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-shape-routing-heuristics) - source [Device.cpp:1900](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/Device.cpp:1900); Reference how simple rrects are routed before falling back to path techniques.
- [`GFX-DRAWLIST-SORT`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-drawlist-sort) - source [DrawList.cpp:90](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/DrawList.cpp:90); Use batching/state-change evidence when promoting the expansion route.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
data class GPURRectRouteEvidence(val commandDump: String, val routeDump: String)
```

## Acceptance Criteria

- [ ] Accepted rrect route dump includes command, analysis, material, pass, and telemetry.
- [ ] Degenerate and unsupported rrect cases refuse deterministically.
- [ ] RRect key/payload behavior mirrors first-route boundaries.

## Required Evidence

- RRect accepted/refused dumps, including deterministic per-corner radii facts.
- WGSL/reflection and pipeline-key evidence if module shape changes.
- Adapter-backed or explicit skipped GPU evidence.

## Fallback / Refusal Behavior

Unsupported rrect variants emit stable diagnostics and must not fall back to a
CPU-rendered texture.

## Dashboard Impact

- Expected row: `gpu-renderer.rrect.native-route`
- Expected classification: `TargetNative`
- Claim promotion allowed: no until evidence is reviewed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk ./gradlew --no-daemon :gpu-raster:test --tests '*GpuRenderer*'
rtk git diff --check
```

## Status Notes

- `done`: Isolated `:gpu-renderer` command, analysis, native route-candidate,
  pass, recording, and ownership-fixture evidence exists for solid
  `FillRRect`. Graphite-alignment remediation now keeps per-corner x/y radii
  facts and explicit unproven scale/affine transform refusals.
  `M2SimpleSceneEvidenceTest` adds the closeout scene line
  `rrect:accepted routeCandidate=native.fill_rrect.solid` plus explicit
  `gpu-lane:explicit-skipped` evidence. Product activation remains false.
  Independent review `019ec7aa-f95b-7f40-9f40-1bf80d87d2b9` accepted the
  skipped-GPU lane evidence and confirmed no `gpu-raster` product route was
  enabled.

## Linear Labels

- `gpu-renderer`
- `milestone:M2`
- `area:geometry`
