---
id: KGPU-M0-007
title: "Review R6 PM evidence and promotion boundary"
status: review
milestone: M0
priority: P0
owner_area: validation-pm
claim_impact: PolicyGated
route_kind: CPUReferenceOnly
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M0-001, KGPU-M0-002, KGPU-M0-003, KGPU-M0-004, KGPU-M0-005, KGPU-M0-006]
legacy_gate: pipelinePmBundle
---

# KGPU-M0-007 - Review R6 PM evidence and promotion boundary

## PM Note

Ce ticket garde la preuve R6 visible pour revue, sans la transformer en support
produit.

## Problem

R6 includes root refusal-first and opt-in adapter-backed evidence lanes. The
boundary must be reviewed before any activation or readiness claim.

## Scope

- Review root refusal-first PM bundle.
- Review opt-in executed adapter-backed evidence lane when available.
- Confirm root `pipelinePmBundle` isolation and `promotion-boundary-held`.
- Confirm activation requires a later explicit product/release decision.

## Non-Goals

- Do not mark R6 done.
- Do not move readiness.
- Do not make adapter-backed evidence a root PM dependency.

## Spec Sources

- `.upstream/specs/gpu-renderer/07-validation-conformance.md`
- `.upstream/specs/gpu-renderer/13-performance-telemetry-cache-gates.md`
- `reports/gpu-renderer/2026-06-14-r6-promotion-readiness-boundary.md`

## Graphite Algorithm References

- [`GFX-RECORDER-SNAP`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-recorder-snap) - source [Recorder.cpp:198](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/Recorder.cpp:198); Use snapshot success/failure cleanup as PM evidence for readiness boundaries.
- [`GFX-DRAWPASS-PREPARE`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-drawpass-prepare) - source [DrawPass.cpp:40](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/DrawPass.cpp:40); Reference pipeline/texture validation as the line before claiming promoted support.
- [`GFX-RESOURCE-CACHE-MRU`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-resource-cache-mru) - source [ResourceCache.cpp:163](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/ResourceCache.cpp:163); Use cache reuse/purge telemetry as release-readiness vocabulary.
- [`GFX-DRAWLIST-SORT`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-drawlist-sort) - source [DrawList.cpp:90](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/DrawList.cpp:90); Reference batch/state-change evidence without treating review status as done.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
data class PromotionBoundaryReview(val rootStatus: String, val executedStatus: String, val activated: Boolean)
```

## Acceptance Criteria

- [ ] Root bundle remains `Incomplete` unless activation policy changes.
- [ ] Executed diagnostic evidence remains opt-in and adapter-backed.
- [ ] Product route activated remains `false`.
- [ ] Readiness delta remains `0.0`.

## Required Evidence

- R6 progress row.
- Root PM validator output.
- Executed PM validator output when adapter evidence exists.
- Promotion boundary validator report.

## Fallback / Refusal Behavior

Missing evidence keeps the gate incomplete; it must not be replaced by a
synthetic pass, skipped adapter marker, or raw product-support wording.

## Dashboard Impact

- Expected row: `gpu-renderer.r6-promotion-boundary-review`
- Expected classification: `PolicyGated`
- Claim promotion allowed: no.

## Validation

```bash
rtk ./gradlew --no-daemon validateGpuRendererR6AdapterBackedPromotionReadinessBoundary
rtk python3 scripts/validate_gpu_renderer_r6_promotion_readiness_boundary.py . --require-executed-summary
rtk git diff --check
```

## Status Notes

- `review`: R6 boundary evidence exists and requires independent acceptance.

## Linear Labels

- `gpu-renderer`
- `milestone:M0`
- `area:pm-evidence`
