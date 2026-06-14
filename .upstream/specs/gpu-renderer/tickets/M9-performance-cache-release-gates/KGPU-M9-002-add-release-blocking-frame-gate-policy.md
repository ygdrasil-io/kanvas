---
id: KGPU-M9-002
title: "Add release-blocking frame gate policy"
status: proposed
milestone: M9
priority: P0
owner_area: performance
claim_impact: PolicyGated
route_kind: CPUReferenceOnly
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M9-001]
legacy_gate: "frame reporting-only"
---

# KGPU-M9-002 - Add release-blocking frame gate policy

## PM Note

Ce ticket définit quand les timings deviennent bloquants pour la release.

## Problem

Frame timing needs warmup, variance, owned hardware, quarantine, and rebaseline
policy before release-blocking status.

## Scope

- Define candidate and release-blocking frame gate states.
- Add negative fixture and quarantine policy.

## Non-Goals

- Do not make current timing release-blocking.
- Do not conflate correctness with performance.

## Spec Sources

- `.upstream/specs/gpu-renderer/13-performance-telemetry-cache-gates.md`

## Graphite Algorithm References

- [`GFX-DRAWLIST-SORT`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-drawlist-sort) - source [DrawList.cpp:90](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/DrawList.cpp:90); Use draw sorting and state-change counts for frame-gate thresholds.
- [`GFX-DRAW-WRITER`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-draw-writer) - source [DrawWriter.cpp:32](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/DrawWriter.cpp:32); Reference command coalescing and buffer binding changes for frame budget evidence.
- [`GFX-RENDERPASS-TASK`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-renderpass-task) - source [RenderPassTask.cpp:128](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/task/RenderPassTask.cpp:128); Study render-pass attachment and preparation costs as release gates.
- [`GFX-RESOURCE-CACHE-MRU`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-resource-cache-mru) - source [ResourceCache.cpp:163](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/ResourceCache.cpp:163); Tie cache churn/purge observations to release-blocking policy.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
data class FrameGatePolicy(val releaseBlocking: Boolean, val quarantineReasons: List<String>)
```

## Acceptance Criteria

- [ ] Gate state and variance policy are explicit.
- [ ] Reporting-only lanes remain non-blocking.
- [ ] Negative threshold fixture exists.

## Required Evidence

- Gate policy report, raw sample provenance, and quarantine fixture.

## Fallback / Refusal Behavior

Unstable or unowned measurements stay reporting-only.

## Dashboard Impact

- Expected row: `gpu-renderer.frame-gate-policy`
- Expected classification: `PolicyGated`
- Claim promotion allowed: no without accepted gate policy.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk git diff --check
```

## Status Notes

- `proposed`: Policy only.

## Linear Labels

- `gpu-renderer`
- `milestone:M9`
- `area:performance`
