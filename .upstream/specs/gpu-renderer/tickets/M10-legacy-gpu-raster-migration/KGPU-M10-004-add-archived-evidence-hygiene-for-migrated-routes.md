---
id: KGPU-M10-004
title: "Add archived evidence hygiene for migrated routes"
status: proposed
milestone: M10
priority: P1
owner_area: docs-evidence
claim_impact: RefuseRequired
route_kind: mixed
product_activation: false
release_blocking: false
adapter_required: false
depends_on: [KGPU-M10-001]
legacy_gate: archives
---

# KGPU-M10-004 - Add archived evidence hygiene for migrated routes

## PM Note

Ce ticket évite de réactiver des plans archivés comme backlog actif.

## Problem

Archived migration plans and old snapshots are historical evidence only and
must not become active acceptance criteria during migration.

## Scope

- Add migration docs rule for archived evidence.
- Add checks or review criteria for active vs historical references.

## Non-Goals

- Do not rewrite archives.
- Do not weaken current target docs.

## Spec Sources

- `AGENTS.md`
- `.upstream/specs/gpu-renderer/32-target-authority-taxonomy-diagnostics.md`

## Design Sketch

```kotlin
data class EvidenceReferencePolicy(val path: String, val role: String)
```

## Acceptance Criteria

- [ ] Active tickets cite active specs for acceptance criteria.
- [ ] Archive references are labeled historical.
- [ ] No archived checkbox is used as active backlog.

## Required Evidence

- Reference hygiene report or review checklist.

## Fallback / Refusal Behavior

Tickets relying on archived acceptance criteria remain blocked.

## Dashboard Impact

- Expected row: `gpu-renderer.archive-hygiene`
- Expected classification: `RefuseRequired`
- Claim promotion allowed: no.

## Validation

```bash
rtk git diff --check
```

## Status Notes

- `proposed`: Documentation hygiene ticket.

## Linear Labels

- `gpu-renderer`
- `milestone:M10`
- `area:evidence`
