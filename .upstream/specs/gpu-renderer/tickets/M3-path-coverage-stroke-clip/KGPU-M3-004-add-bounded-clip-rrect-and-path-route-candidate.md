---
id: KGPU-M3-004
title: "Add bounded clip rrect and path route candidate"
status: proposed
milestone: M3
priority: P0
owner_area: clips-atlas
claim_impact: TargetPrepared
route_kind: CPUPreparedGPU
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M3-001]
legacy_gate: "clip legacy"
---

# KGPU-M3-004 - Add bounded clip rrect and path route candidate

## PM Note

Ce ticket rend les clips complexes visibles comme plans ou refus, pas comme
approximations cachées.

## Problem

Clip rrect/path requires ordering, stencil/mask/atlas strategy, and stable
refusals for unsupported stack interactions.

## Scope

- Add bounded rrect/path clip planning and dump evidence.
- Add refusal fixtures for difference, inverse, shader, and over-budget clips.

## Non-Goals

- No arbitrary clip-stack support.
- No CPU-rendered clipped layer fallback.

## Spec Sources

- `.upstream/specs/gpu-renderer/24-clip-stencil-mask-pipeline.md`
- `.upstream/specs/gpu-renderer/19-path-coverage-atlas-strategy.md`

## Design Sketch

```kotlin
data class ComplexClipEvidence(val clipPlan: String, val orderingToken: String)
```

## Acceptance Criteria

- [ ] Clip ordering and bounds are dumpable.
- [ ] Stencil/mask/atlas strategy maps to an explicit route kind.
- [ ] Unsupported interactions refuse.

## Required Evidence

- Clip plan, stencil/mask/atlas, and refusal dumps.
- Route diagnostics and budget facts.

## Fallback / Refusal Behavior

Unsupported clips refuse with `unsupported.clip.*` diagnostics.

## Dashboard Impact

- Expected row: `gpu-renderer.clip.rrect-path`
- Expected classification: `TargetPrepared`
- Claim promotion allowed: no until reviewed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk git diff --check
```

## Status Notes

- `proposed`: Bounded candidate only.

## Linear Labels

- `gpu-renderer`
- `milestone:M3`
- `area:clips`
