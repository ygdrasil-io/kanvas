---
id: KGPU-M1-001
title: "Decide first-route product activation policy"
status: proposed
milestone: M1
priority: P0
owner_area: product-validation
claim_impact: PolicyGated
route_kind: GPUNative
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

## Non-Goals

- Do not activate the route in this ticket.
- Do not expand beyond the first solid `FillRect` path.

## Spec Sources

- `.upstream/specs/gpu-renderer/36-implementation-roadmap.md`
- `reports/gpu-renderer/2026-06-14-r6-promotion-readiness-boundary.md`

## Design Sketch

```kotlin
data class FirstRouteActivationDecision(val approved: Boolean, val evidenceRefs: List<String>)
```

## Acceptance Criteria

- [ ] Policy states R6 evidence alone does not activate product routing.
- [ ] Required evidence includes non-skipped adapter-backed execution and readback.
- [ ] Rollback and legacy preservation are named before activation.

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
- Claim promotion allowed: no until accepted decision is linked.

## Validation

```bash
rtk ./gradlew --no-daemon validateGpuRendererR6AdapterBackedPromotionReadinessBoundary
rtk ./gradlew --no-daemon pipelinePmBundle --dry-run
rtk git diff --check
```

## Status Notes

- `proposed`: Awaiting review of M0 evidence and activation criteria.

## Linear Labels

- `gpu-renderer`
- `milestone:M1`
- `area:product-validation`
