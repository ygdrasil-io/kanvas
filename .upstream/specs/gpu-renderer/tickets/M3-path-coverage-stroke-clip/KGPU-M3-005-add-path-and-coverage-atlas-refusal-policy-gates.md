---
id: KGPU-M3-005
title: "Add path and coverage atlas refusal policy gates"
status: proposed
milestone: M3
priority: P1
owner_area: atlas-policy
claim_impact: RefuseRequired
route_kind: RefuseDiagnostic
product_activation: false
release_blocking: false
adapter_required: false
depends_on: [KGPU-M3-001]
legacy_gate: null
---

# KGPU-M3-005 - Add path and coverage atlas refusal policy gates

## PM Note

Ce ticket garde les atlas non prêts comme refus explicites plutôt que promesses
de support.

## Problem

Atlas routes need memory, eviction, generation, mutation, and synchronization
policies before support can be claimed.

## Scope

- Add refusal gates for path and coverage atlas routes lacking policy evidence.
- Define required facts before future promotion.

## Non-Goals

- Do not implement atlas generation.
- Do not promote atlas-backed route support.

## Spec Sources

- `.upstream/specs/gpu-renderer/19-path-coverage-atlas-strategy.md`

## Design Sketch

```kotlin
data class AtlasPolicyRefusal(val reason: String, val requiredFacts: List<String>)
```

## Acceptance Criteria

- [ ] Missing atlas policies refuse with stable diagnostics.
- [ ] Required facts for future promotion are listed.
- [ ] PM output cannot count selector-only atlas evidence as support.

## Required Evidence

- Refusal matrix and diagnostic dumps.
- Dashboard row showing `RefuseRequired`.

## Fallback / Refusal Behavior

Atlas routes refuse until policy, budgets, and synchronization evidence exist.

## Dashboard Impact

- Expected row: `gpu-renderer.atlas-policy-refusal`
- Expected classification: `RefuseRequired`
- Claim promotion allowed: no.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk git diff --check
```

## Status Notes

- `proposed`: Refusal policy ticket.

## Linear Labels

- `gpu-renderer`
- `milestone:M3`
- `area:atlas`
