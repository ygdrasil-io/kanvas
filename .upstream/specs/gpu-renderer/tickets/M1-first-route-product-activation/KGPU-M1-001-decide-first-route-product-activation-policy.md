---
id: KGPU-M1-001
title: "Decide first-route product activation policy"
status: done
milestone: M1
priority: P0
owner_area: product-validation
claim_impact: PolicyGated
route_kind: CPUReferenceOnly
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M0-007]
legacy_gate: pipelinePmBundle
---

# KGPU-M1-001 - Decide first-route product activation policy

## PM Note

Ce ticket décide si la preuve `FillRect` reste diagnostic ou peut devenir une
route produit contrôlée.

## Problem

R6 evidence can pass in an opt-in adapter-backed lane, but product activation
requires a separate policy decision and rollback plan.

## Scope

- Define activation criteria for the first solid `FillRect` route.
- Require reviewed non-skipped adapter-backed evidence.
- Define release/product decision fields and rollback preconditions.
- Produce a policy decision record only; this ticket does not execute or
  promote the native route itself.

## Non-Goals

- Do not activate the route in this ticket.
- Do not expand beyond the first solid `FillRect` path.

## Spec Sources

- `.upstream/specs/gpu-renderer/36-implementation-roadmap.md`
- `reports/gpu-renderer/2026-06-14-r6-promotion-readiness-boundary.md`

## Graphite Algorithm References

- [`GFX-DRAWCONTEXT-RECORD`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-drawcontext-record) - source [DrawContext.cpp:155](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/DrawContext.cpp:155); Study native draw admission and barrier classification for activation policy.
- [`GFX-DRAWCONTEXT-FLUSH`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-drawcontext-flush) - source [DrawContext.cpp:213](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/DrawContext.cpp:213); Use pass extraction and task insertion to define when an activated route is observable.
- [`GFX-DRAW-ORDER`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-draw-order) - source [DrawOrder.h:52](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/DrawOrder.h:52); Reference painter-order/depth/stencil constraints that activation must not violate.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
data class FirstRouteActivationDecision(val approved: Boolean, val evidenceRefs: List<String>)
```

## Acceptance Criteria

- [x] Policy states R6 evidence alone does not activate product routing.
- [x] Required evidence includes reviewed executed diagnostic evidence.
- [x] Rollback and legacy preservation are named before activation.

## Required Evidence

- Activation decision record.
- Boundary report showing prior activation is false.
- Reviewed executed PM evidence summary.

## Fallback / Refusal Behavior

If any required evidence is missing or skipped, the route remains diagnostic and
legacy behavior stays default.

## Dashboard Impact

- Expected row: `gpu-renderer.first-route-activation-policy`
- Expected classification: `PolicyGated`
- Claim promotion allowed: controlled M1 activation-candidate planning only;
  no product route activation.

## Validation

```bash
rtk ./gradlew --no-daemon validateGpuRendererR6AdapterBackedPromotionReadinessBoundary
rtk ./gradlew --no-daemon pipelinePmBundle --dry-run
rtk git diff --check
```

## Status Notes

- `done`: The 2026-06-14 product/release decision accepts launching the
  controlled M1 promotion path in the current state. Independent review is
  sufficient for this policy gate and must be repeated for each future
  milestone. Product route activation remains `false`, readiness delta remains
  `0.0`, and the next concrete gate is KGPU-M1-002 PM activation-candidate
  packaging.

## Linear Labels

- `gpu-renderer`
- `milestone:M1`
- `area:product-validation`
