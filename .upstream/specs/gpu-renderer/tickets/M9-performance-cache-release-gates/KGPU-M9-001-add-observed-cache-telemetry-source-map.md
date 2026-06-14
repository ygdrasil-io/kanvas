---
id: KGPU-M9-001
title: "Add observed cache telemetry source map"
status: done
milestone: M9
priority: P0
owner_area: telemetry-cache
claim_impact: PolicyGated
route_kind: CPUReferenceOnly
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M1-001]
legacy_gate: "cache reporting-only"
---

# KGPU-M9-001 - Add observed cache telemetry source map

## PM Note

Ce ticket sépare les compteurs observés des ledgers dérivés.

## Problem

Cache readiness cannot move from derived or observed-partial counters.

## Scope

- Map telemetry counters to source artifacts.
- Classify observed, observed-partial, derived, unavailable, and reporting-only.

## Non-Goals

- Do not create release-blocking gates.
- Do not synthesize counters from comments or reports.

## Spec Sources

- `.upstream/specs/gpu-renderer/13-performance-telemetry-cache-gates.md`

## Graphite Algorithm References

- [`GFX-RESOURCE-CACHE-MRU`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-resource-cache-mru) - source [ResourceCache.cpp:163](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/ResourceCache.cpp:163); Study keyed cache reuse, MRU behavior, returned resources, and purge telemetry.
- [`GFX-PIPELINE-MANAGER`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-pipeline-manager) - source [PipelineManager.cpp:38](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/PipelineManager.cpp:38); Reference pipeline cache hit/miss and in-flight creation task telemetry.
- [`GFX-DRAWPASS-PREPARE`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-drawpass-prepare) - source [DrawPass.cpp:40](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/DrawPass.cpp:40); Use per-pipeline draw-area traces as cache/source-map evidence.
- [`GFX-DRAWLIST-SORT`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-drawlist-sort) - source [DrawList.cpp:90](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/DrawList.cpp:90); Capture state-change and pipeline draw-area data for performance dashboards.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
data class CacheTelemetrySource(val counter: String, val classification: String)
```

## Acceptance Criteria

- [ ] Every counter has a named source.
- [ ] Derived counters cannot count as observed readiness.
- [ ] Unavailable counters remain visible.

## Required Evidence

- Source-map report, PM manifest row, and validator output.

## Fallback / Refusal Behavior

Missing observed source keeps the gate reporting-only.

## Dashboard Impact

- Expected row: `gpu-renderer.cache-telemetry-source-map`
- Expected classification: `PolicyGated`
- Claim promotion allowed: no.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk git diff --check
```

## Status Notes

- `done`: `GPUCacheTelemetrySourceMapTest` records source-map
  classification for observed, observed-partial, derived, unavailable, and
  reporting-only counters. `GPUCacheTelemetrySourceMapper` ties each counter to
  a named source artifact and leaves `readinessDelta=0.0`,
  `releaseBlocking=false`, and `productRouteActivated=false`. Independent
  review `019ec866-9980-7fe1-bc04-a9806b1d30c3` accepted the evidence as
  source-map/reporting only and confirmed it does not promote cache,
  performance, release-gate, or product-route support.

## Linear Labels

- `gpu-renderer`
- `milestone:M9`
- `area:telemetry`
