---
id: KGPU-MX-000
title: "Ticket title"
status: proposed
milestone: MX
priority: P1
owner_area: owner-area
claim_impact: ImplementationCandidate
route_kind: CPUReferenceOnly
product_activation: false
release_blocking: false
adapter_required: false
depends_on: []
legacy_gate: null
---

# KGPU-MX-000 - Ticket title

## PM Note

Phrase simple en français expliquant pourquoi le PM doit suivre ce livrable.
Ne pas seulement traduire ou répéter le titre anglais.

## Problem

Describe the concrete target gap, current failure mode, and why support,
activation, or gate retirement cannot be promoted yet.

## Scope

- List exact deliverables for one primary capability.
- Name the target contracts, diagnostics, fixtures, dumps, reports, adapter
  routes, or PM bundle rows affected by this ticket.
- Keep scope bounded to the owning milestone and dependencies.

## Non-Goals

- Do not promote support without accepted evidence.
- Do not activate product routing unless this ticket explicitly owns that
  decision and validation.
- Do not add hidden CPU-rendered texture compatibility.

## Spec Sources

- `.upstream/specs/gpu-renderer/README.md`
- `.upstream/specs/gpu-renderer/36-implementation-roadmap.md`

## Design Sketch

```kotlin
data class GPURendererTicketEvidence(
    val routeKind: String,
    val dumpRefs: List<String>,
    val diagnostics: List<String>,
)
```

## Acceptance Criteria

- [ ] Criteria are observable, testable, and specific to this ticket's route or
      gate.
- [ ] Unsupported or gated behavior has a stable diagnostic and does not become
      a support claim.

## Required Evidence

- Exact deterministic dumps, fixtures, diagnostics, reports, artifacts, or PM
  bundle entries required before promotion.

## Fallback / Refusal Behavior

- Unsupported routes emit stable diagnostics.
- Silent fallback to CPU-rendered complete draw/layer/filter/text texture
  compatibility is not allowed.
- Legacy gates remain visible until this ticket's Required Evidence is linked
  and reviewed.

## Dashboard Impact

- Expected row:
- Expected classification:
- Claim promotion allowed: no, unless all Required Evidence is attached and
  validation has passed.

## Validation

```bash
rtk git diff --check
```

## Status Notes

- `proposed`: Initial ticket.

## Linear Labels

- `gpu-renderer`
