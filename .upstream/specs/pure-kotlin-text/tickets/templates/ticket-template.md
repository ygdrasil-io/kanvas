---
id: KFONT-MX-000
title: "Ticket title"
status: proposed
milestone: MX
priority: P1
owner_area: owner-area
claim_impact: tracked-gap
depends_on: []
legacy_gate: null
---

# KFONT-MX-000 - Ticket title

## PM Note

Phrase simple en français expliquant pourquoi le PM doit suivre ce livrable.
Ne pas seulement traduire ou répéter le titre anglais.

## Problem

Describe the concrete target gap, current failure mode, and why support or gate
retirement cannot be promoted yet.

## Scope

- List exact deliverables for one primary capability.
- Name the target contracts, diagnostics, fixtures, dumps, reports, or adapter
  routes affected by this ticket.
- Keep scope bounded to the owning milestone and dependencies.

## Non-Goals

- Do not promote support without evidence.
- Do not add native dependencies for normative behavior.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`

## Design Sketch

```kotlin
data class DomainSpecificEvidence(
    val subjectId: DomainSubjectID,
    val dumpRefs: List<String>,
    val diagnostics: List<RouteDiagnostic>,
)
```

## Acceptance Criteria

- [ ] Criteria are observable, testable, and specific to this ticket's domain.
- [ ] Unsupported or gated behavior has a stable diagnostic and does not become
      a support claim.

## Required Evidence

- Exact deterministic dumps, fixtures, diagnostics, reports, and dashboard
  diffs required before promotion.
- Repeated-run evidence when performance or telemetry is involved.

## Fallback / Refusal Behavior

- Unsupported routes emit a stable diagnostic.
- Silent fallback to platform/native/font engine behavior is not allowed.
- Legacy gates remain visible until the ticket's Required Evidence is linked.

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

- `pure-kotlin-font`
