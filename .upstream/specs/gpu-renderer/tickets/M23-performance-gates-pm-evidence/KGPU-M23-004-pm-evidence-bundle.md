---
id: KGPU-M23-004
title: "Add final PM evidence bundle: all families activated, gates green, rollback tested"
status: proposed
milestone: M23
priority: P0
owner_area: validation-pm
claim_impact: PolicyGated
route_kind: CPUReferenceOnly
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M13-004, KGPU-M14-003, KGPU-M15-004, KGPU-M16-004, KGPU-M17-005, KGPU-M18-005, KGPU-M19-004, KGPU-M20-005, KGPU-M21-004, KGPU-M22-004]
legacy_gate: pipelinePmBundle
---

# KGPU-M23-004 - Add final PM evidence bundle: all families activated, gates green, rollback tested

## PM Note

Le bundle PM final est la preuve que toutes les familles sont activées, les gates de performance sont vertes, et le rollback est testé pour chaque route.

## Problem

Final PM evidence bundle must prove all draw families are activated, performance gates are green, and rollback is tested for all routes. This is the capstone evidence for the M12-M23 production activation wave.

## Scope

- Add final PM evidence bundle aggregating all milestone evidence
- Prove all families activated with parity evidence
- Prove all performance gates are green
- Prove rollback tested for all routes

## Non-Goals

- Not a release-blocking gate on non-Apple platforms
- No cross-platform evidence requirements

## Spec Sources

- .upstream/specs/gpu-renderer/README.md

## Graphite Algorithm References

- [`GFX-GFX_DRAWGEOMETRY_ROUTING`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-drawgeometry-routing) - source dm/DM.cpp; Algorithm to study for this ticket's domain.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
data class FinalPMEvidence(val familyActivations: List<FamilyActivation>, val gateResults: List<GateResult>, val rollbackTests: List<RollbackTest>)
```

## Acceptance Criteria

- [ ] All draw families have activation evidence in PM bundle
- [ ] All performance gates show green status
- [ ] Rollback tested and verified for every activated route
- [ ] PM bundle passes validation

## Required Evidence

- PM bundle validation report
- Per-family activation evidence summary
- Gate status: all green
- Rollback test transcript for all routes

## Fallback / Refusal Behavior

Missing evidence keeps affected families in proposed or review status; PM bundle not approved.

## Dashboard Impact

- Expected row: `gpu-renderer.m23.pm-evidence-bundle`
- Expected classification: `PolicyGated`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon pipelinePmBundle
```

## Status Notes

- `proposed`: Initial ticket.

## Linear Labels

- `gpu-renderer`
- `milestone:M23`
- `area:validation-pm`
