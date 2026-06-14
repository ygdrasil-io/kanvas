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

Ce ticket explique en français simple pourquoi le PM doit suivre ce livrable.

## Problem

Describe the target gap and why support cannot be promoted yet.

## Scope

- Deliver one primary capability.
- Emit stable diagnostics.
- Produce deterministic evidence.

## Non-Goals

- Do not promote support without evidence.
- Do not add native dependencies for normative behavior.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`

## Design Sketch

```kotlin
data class TicketPlan(
    val input: TicketInput,
    val diagnostics: List<RouteDiagnostic>,
)
```

## Acceptance Criteria

- [ ] Criteria are observable and testable.

## Required Evidence

- Deterministic dump or fixture evidence.
- Stable diagnostic snapshot.

## Fallback / Refusal Behavior

- Unsupported routes emit a stable diagnostic.
- Silent fallback to platform/native/font engine behavior is not allowed.

## Dashboard Impact

- Expected row:
- Expected classification:
- Claim promotion allowed: no, unless all Required Evidence is attached.

## Validation

```bash
rtk git diff --check
```

## Status Notes

- `proposed`: Initial ticket.

## Linear Labels

- `pure-kotlin-font`
