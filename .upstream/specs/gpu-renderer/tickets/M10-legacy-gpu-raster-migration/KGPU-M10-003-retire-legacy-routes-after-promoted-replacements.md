---
id: KGPU-M10-003
title: "Retire legacy routes after promoted replacements"
status: proposed
milestone: M10
priority: P1
owner_area: legacy-cleanup
claim_impact: PolicyGated
route_kind: mixed
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M10-002]
legacy_gate: "gpu-raster legacy"
---

# KGPU-M10-003 - Retire legacy routes after promoted replacements

## PM Note

Ce ticket encadre la suppression legacy uniquement après remplacement promu.

## Problem

Legacy code can only be retired when a replacement route has accepted evidence,
activation policy, rollback, and PM update.

## Scope

- Define retirement checklist for promoted replacement slices.
- Add guards against broad deletion.

## Non-Goals

- Do not retire multiple domains by implication.
- Do not remove archived evidence.

## Spec Sources

- `.upstream/specs/gpu-renderer/06-legacy-adapter-cleanup.md`

## Design Sketch

```kotlin
data class LegacyRetirementGate(val legacyRoute: String, val promotedReplacement: String)
```

## Acceptance Criteria

- [ ] Retirement requires accepted replacement ticket.
- [ ] Rollback and PM evidence are linked.
- [ ] Archived plans remain historical only.

## Required Evidence

- Retirement gate report, replacement evidence, and rollback validation.

## Fallback / Refusal Behavior

If any replacement evidence is missing, legacy route remains.

## Dashboard Impact

- Expected row: `gpu-renderer.legacy-retirement`
- Expected classification: `PolicyGated`
- Claim promotion allowed: no without accepted replacement.

## Validation

```bash
rtk git diff --check
```

## Status Notes

- `proposed`: No deletion by catalog creation.

## Linear Labels

- `gpu-renderer`
- `milestone:M10`
- `area:legacy-cleanup`
