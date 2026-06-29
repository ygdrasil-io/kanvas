---
id: KGPU-M32-006
title: "Legacy decommission: final decommission validation + evidence bundle + PR"
status: done
milestone: M32
priority: P0
owner_area: legacy-cleanup
claim_impact: PolicyGated
route_kind: CPUReferenceOnly
product_activation: false
release_blocking: true
adapter_required: false
depends_on: [KGPU-M32-005]
legacy_gate: "gpu-raster legacy"
---

# KGPU-M32-006 - Final decommission validation + evidence bundle + PR

## Problem

After KGPU-M32-005 removes the legacy device, rollback branch, and
module include, the decommission is not complete until a final
validation pass confirms that no broken references remain, all 12
families have port-or-refuse closure, and the retirement gate accepts
every family with real evidence.

## Scope

- Run the full validation suite: all gpu-renderer tests, all gpu-raster
  tests (pre-removal snapshot), and any downstream module tests.
- Assemble the evidence bundle: per-family port-or-refuse decision
  matrix status, retirement-gate authorization results, shared-infra
  relocation confirmation, module-removal diff/stat artifact.
- Deliver the final PR with the evidence bundle and request independent
  review.

## Non-Goals

- Do not add new GPU routes.
- Do not modify port-or-refuse decisions already made in KGPU-M32-001.

## Spec Sources

- `.upstream/specs/gpu-renderer/tickets/M32-legacy-gpu-raster-decommission/README.md`

## Acceptance Criteria

- [ ] Full test suite passes with no new failures.
- [ ] Evidence bundle complete: 12-family decision matrix, retirement
  gate results, relocation confirmation, removal diff.
- [ ] PR delivered and ready for independent review.

## Required Evidence

- Test suite pass report.
- Evidence bundle at `reports/gpu-renderer/m32-decommission-evidence/`.
- PR link with full diff.

## Fallback / Refusal Behavior

- If any family fails retirement-gate validation, the decommission
  is blocked and the family ticket reopens.

## Dashboard Impact

- Expected row: `gpu-renderer.m32.final-decommission-validation`
- Expected classification: `PolicyGated`

## Validation

```bash
rtk git diff --check && rtk ./gradlew --no-daemon :gpu-raster:test :gpu-renderer:test
```

## Status Notes

- `done`: gpu-raster module removed (03d12fdc), tests pass (589 green, HEAD c50fec44), evidence bundle at `reports/gpu-renderer/2026-06-26-m32-*.md`. PR delivered and independent review completed.

## Linear Labels

- `gpu-renderer`
- `milestone:M32`
- `area:legacy-cleanup`
- `legacy-gate:gpu-raster`
