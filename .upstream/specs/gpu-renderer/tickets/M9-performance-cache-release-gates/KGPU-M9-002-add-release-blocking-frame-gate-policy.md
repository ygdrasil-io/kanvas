---
id: KGPU-M9-002
title: "Add release-blocking frame gate policy"
status: done
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

- [x] Gate state and variance policy are explicit.
- [x] Reporting-only lanes remain non-blocking.
- [x] Negative threshold fixture exists.

## Required Evidence

- [x] Gate policy report.
- [x] Owned-adapter raw sample provenance.
- [x] Quarantine and negative-threshold fixtures.

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

- `done`: `GPUFrameGatePolicyTest` covers explicit
  candidate/release-blocking states, warmup and variance policy,
  reporting-only lanes, quarantine, skipped-lane diagnostics, and a negative
  threshold fixture that fails closed without moving `readinessDelta`,
  `releaseBlocking`, or product activation.
  `reports/gpu-renderer-scenes/frame-samples/frame-gate-blocker-board/frame-samples.json`
  provides owned WebGPU offscreen raw samples with 60 samples, 3 warmup frames,
  57 stable frames, adapter `Apple M2 Max`, metric source
  `wall-clock-offscreen-render-readback`, and SHA-256
  `sha256:aacd64f3f65ae87feeaca7600434e2425ff44a7d3e0ddde5a1c66de57021530a`.
  The stable coefficient of variation is `0.1472`, so the observed lane stays
  candidate/non-release-blocking. Independent review
  `019ed26f-3531-7fd0-8e5d-61f9a15d5a9a` accepted the evidence for `done`
  with no blocking findings and confirmed no product activation,
  release-blocking gate, readiness delta, or M9-003 implementation.

## Linear Labels

- `gpu-renderer`
- `milestone:M9`
- `area:performance`
