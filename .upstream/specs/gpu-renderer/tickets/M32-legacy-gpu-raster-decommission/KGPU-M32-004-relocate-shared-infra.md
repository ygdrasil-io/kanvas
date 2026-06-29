---
id: KGPU-M32-004
title: "Legacy decommission: relocate shared WGSL / conformance / runtime-shader / gate infra out of :gpu-raster"
status: proposed
milestone: M32
priority: P0
owner_area: legacy-cleanup
claim_impact: PolicyGated
route_kind: CPUReferenceOnly
product_activation: false
release_blocking: true
adapter_required: false
depends_on: [KGPU-M32-003]
legacy_gate: "gpu-raster legacy"
---

# KGPU-M32-004 - Relocate shared infra out of :gpu-raster

## Problem

The `:gpu-raster` module currently hosts shared WGSL, conformance,
runtime-shader, and gate infrastructure that must be available after the
legacy gpu-raster device is removed. These shared components need to be
relocated to independent modules before KGPU-M32-005 can remove
`:gpu-raster`.

## Scope

- Identify every shared WGSL, conformance, runtime-shader, and gate
  source that currently lives under `:gpu-raster` and is referenced
  from outside the gpu-raster device.
- Create or designate target modules for each shared component.
- Move sources and update all import/reference paths.
- Verify that the gpu-raster device tests continue to pass with the
  relocated sources (no back-link breakage).
- Verify that non-gpu-raster consumers compile and pass tests after
  relocation.

## Non-Goals

- Do not remove `:gpu-raster` module include (KGPU-M32-005 owns that).
- Do not modify the gpu-raster device behavior.
- Do not add new GPU routes.

## Spec Sources

- `.upstream/specs/gpu-renderer/tickets/M32-legacy-gpu-raster-decommission/README.md`

## Acceptance Criteria

- [ ] Every shared WGSL, conformance, runtime-shader, and gate source
  relocated out of `:gpu-raster`.
- [ ] All import/reference paths updated across the project.
- [ ] gpu-raster device tests continue to pass.
- [ ] Non-gpu-raster consumers compile and pass tests.

## Required Evidence

- Module dependency graph showing no shared infra left in `:gpu-raster`.
- Full test suite pass after relocation.

## Fallback / Refusal Behavior

- If a shared component cannot be relocated without breaking the
  gpu-raster device, the decommission is blocked until a safe
  relocation path is found.

## Dashboard Impact

- Expected row: `gpu-renderer.m32.relocate-shared-infra`
- Expected classification: `PolicyGated`

## Validation

```bash
rtk git diff --check && rtk ./gradlew --no-daemon :gpu-raster:test
```

## Status Notes

- `proposed`: Ticket created from M32 README scaffold.

## Linear Labels

- `gpu-renderer`
- `milestone:M32`
- `area:legacy-cleanup`
- `legacy-gate:gpu-raster`
