---
id: KGPU-M11-001
title: "Add WGPU module, pipeline, and bind group layout caches"
status: done
milestone: M11
priority: P0
owner_area: execution-cache
claim_impact: ImplementationCandidate
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M0-005, KGPU-M9-001]
legacy_gate: "cache reporting-only"
---

# KGPU-M11-001 - Add WGPU module, pipeline, and bind group layout caches

## PM Note

Ce ticket transforme les caches aujourd'hui observés en caches d'exécution
réels, sans promettre de performance produit.

## Problem

The current WGPU helper can create shader modules, layouts, pipelines, uniform
buffers, and bind groups for a smoke route, but it recreates those objects per
call. Existing M9 telemetry names cache facts as observational or
reporting-only. The Graphite/Dawn gap remains open until the execution path has
renderer-owned caches backed by real facade events and stable diagnostics.

## Scope

- Add a planning ticket for `WGSL` module, render-pipeline, pipeline-layout,
  and bind group layout caches in the execution/materialization path.
- Require cache keys to come from `GPURenderPipelineKey`, `WGSL` module identity,
  bind group layout identity, target state, and device-generation facts.
- Require real hit/miss/create/fail/evict telemetry tied to WGPU runtime events,
  not synthetic report ledgers.
- Keep cache ownership behind `GPUResourceProvider` and execution scopes.

## Non-Goals

- Do not claim release-blocking performance readiness.
- Do not cache concrete handles in `MaterialKey` or durable recording keys.
- Do not add a generic multi-backend abstraction or port Dawn C++ classes.

## Spec Sources

- `.upstream/specs/gpu-renderer/04-pipeline-key-cache-resources.md`
- `.upstream/specs/gpu-renderer/10-gpu-execution-context-submission.md`
- `.upstream/specs/gpu-renderer/13-performance-telemetry-cache-gates.md`
- `.upstream/specs/gpu-renderer/37-draw-packet-command-stream.md`

## Graphite Algorithm References

- [`GFX-PIPELINE-MANAGER`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-pipeline-manager) - Study pipeline creation, cache lookup, and async task boundaries as an algorithm reference only.
- [`GFX-RESOURCE-KEYED-CACHE`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-resource-keyed-cache) - Reference descriptor-keyed cache ownership without adopting Graphite resources.
- [`GFX-DRAWPASS-PREPARE`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-drawpass-prepare) - Reference pre-submit resource preparation and refusal points.
- Boundary: references are for algorithm study only; do not port Graphite,
  Ganesh, Dawn C++, or SkSL.

## Design Sketch

```kotlin
data class WGPUExecutionCachePlan(
    val deviceGeneration: String,
    val moduleKey: String,
    val pipelineKey: String,
    val bindGroupLayoutKey: String,
    val telemetryCounters: List<String>,
)
```

## Acceptance Criteria

- [ ] `WGSL` module cache entries are keyed by validated module identity and
      device-generation facts, not source object identity.
- [ ] Render-pipeline cache entries are keyed by `GPURenderPipelineKey` plus
      layout and target-state facts required for WGPU validity.
- [ ] Bind group layout cache entries are keyed by `WGSLBindingLayout` identity
      and refuse incompatible dynamic-offset or resource binding shapes.
- [ ] Cache hit, miss, create, failure, stale-generation, and eviction facts are
      emitted from real materialization events.
- [ ] Missing adapter, device-loss, stale generation, or pipeline creation
      failure produces stable diagnostics without product route activation.

## Required Evidence

- Deterministic cache-key preimage dumps for module, layout, and pipeline.
- Adapter-backed hit/miss/create/fail telemetry fixture.
- Device-loss or stale-generation refusal fixture.
- `rtk ./gradlew --no-daemon :gpu-renderer:check` once implemented.

## Fallback / Refusal Behavior

Cache failure refuses the affected materialization or rebuilds only when the
same route and device generation remain valid. It must not silently submit a
different pipeline or CPU-render a compatibility texture.

## Dashboard Impact

- Expected row: `gpu-renderer.execution.cache.materialization`
- Expected classification: `ImplementationCandidate`
- Claim promotion allowed: no; this ticket creates execution-cache evidence
  only.

## Validation

```bash
rtk git diff --check
```

## Status Notes

- `done`: Added execution-owned WGPU module, bind-group-layout,
  pipeline-layout, and render-pipeline caches for the runtime helper. Evidence
  includes deterministic preimage dumps, adapter-backed hit/miss/create/failure
  telemetry, stale-generation/eviction contract fixtures, disposer-backed
  eviction, full `:gpu-renderer:check`, and independent review acceptance with
  no remaining P0/P1/P2 blockers. No product route activation is claimed.

## Linear Labels

- `gpu-renderer`
- `milestone:M11`
- `area:execution-cache`
