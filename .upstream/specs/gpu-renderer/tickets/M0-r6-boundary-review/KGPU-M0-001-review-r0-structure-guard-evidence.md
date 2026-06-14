---
id: KGPU-M0-001
title: "Review R0 structure guard evidence"
status: review
milestone: M0
priority: P0
owner_area: validation
claim_impact: ImplementationCandidate
route_kind: CPUReferenceOnly
product_activation: false
release_blocking: false
adapter_required: false
depends_on: []
legacy_gate: null
---

# KGPU-M0-001 - Review R0 structure guard evidence

## PM Note

Ce ticket vérifie que la structure du nouveau renderer GPU est protégée avant
de considérer les preuves comme acceptées.

## Problem

R0 is reported as integrated, but a ticket catalog must not convert that report
into `done` without independent review.

## Scope

- Review layout, package-boundary, forbidden-import, placeholder, and `Nothing`
  return-type guards.
- Review deterministic first-slice ownership and alias dumps.
- Confirm no Skia-like, Ganesh, Graphite, browser-only, or validation leakage.

## Non-Goals

- Do not review route execution.
- Do not claim product support.

## Spec Sources

- `.upstream/specs/gpu-renderer/35-package-class-layout.md`
- `.upstream/specs/gpu-renderer/36-implementation-roadmap.md`
- `reports/gpu-renderer/2026-06-14-implementation-roadmap-progress.md`

## Design Sketch

```kotlin
data class StructureReviewEvidence(val checks: List<String>, val violations: List<String>)
```

## Acceptance Criteria

- [ ] `:gpu-renderer:check` evidence is linked.
- [ ] Boundary checks reject forbidden imports and package cycles.
- [ ] R0 remains `review` until the reviewer accepts the linked evidence.

## Required Evidence

- R0 progress row.
- Layout and package-boundary test output.
- First-slice ownership dump.

## Fallback / Refusal Behavior

Any missing structure guard keeps the ticket in `review` or `blocked`; it must
not be hidden by product route evidence.

## Dashboard Impact

- Expected row: `gpu-renderer.r0.structure-guard-review`
- Expected classification: `ImplementationCandidate`
- Claim promotion allowed: no.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk git diff --check
```

## Status Notes

- `review`: R0 evidence exists and requires independent acceptance.

## Linear Labels

- `gpu-renderer`
- `milestone:M0`
- `area:validation`
