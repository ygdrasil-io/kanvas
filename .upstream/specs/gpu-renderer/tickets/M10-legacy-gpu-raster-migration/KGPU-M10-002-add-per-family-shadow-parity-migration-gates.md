---
id: KGPU-M10-002
title: "Add per-family shadow parity migration gates"
status: proposed
milestone: M10
priority: P0
owner_area: migration-validation
claim_impact: PolicyGated
route_kind: mixed
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M10-001]
legacy_gate: "gpu-raster legacy"
---

# KGPU-M10-002 - Add per-family shadow parity migration gates

## PM Note

Ce ticket exige une preuve shadow par famille avant tout changement par défaut.

## Problem

Migration without per-family parity can overclaim broad replacement of
`gpu-raster`.

## Scope

- Add per-family shadow parity gate requirements.
- Define evidence needed before default route switch.

## Non-Goals

- Do not switch defaults.
- Do not combine unrelated families.

## Spec Sources

- `.upstream/specs/gpu-renderer/06-legacy-adapter-cleanup.md`
- `.upstream/specs/gpu-renderer/07-validation-conformance.md`

## Design Sketch

```kotlin
data class ShadowParityGate(val family: String, val evidenceRefs: List<String>)
```

## Acceptance Criteria

- [ ] Each family has its own parity evidence requirements.
- [ ] Missing parity keeps legacy default active.
- [ ] Rollback is named.

## Required Evidence

- Shadow route tests, before/after dumps, and PM rows per family.

## Fallback / Refusal Behavior

Families without accepted parity stay legacy-default or refused by policy.

## Dashboard Impact

- Expected row: `gpu-renderer.shadow-parity-gates`
- Expected classification: `PolicyGated`
- Claim promotion allowed: no without accepted family evidence.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-raster:test --tests '*GpuRendererShadow*'
rtk git diff --check
```

## Status Notes

- `proposed`: Migration gate definition.

## Linear Labels

- `gpu-renderer`
- `milestone:M10`
- `area:migration`
