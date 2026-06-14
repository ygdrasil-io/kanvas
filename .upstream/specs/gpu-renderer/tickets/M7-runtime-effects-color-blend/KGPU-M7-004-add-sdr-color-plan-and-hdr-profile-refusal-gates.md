---
id: KGPU-M7-004
title: "Add SDR color plan and HDR profile refusal gates"
status: proposed
milestone: M7
priority: P1
owner_area: color
claim_impact: DependencyGated
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: false
depends_on: [KGPU-M2-002]
legacy_gate: "color legacy"
---

# KGPU-M7-004 - Add SDR color plan and HDR profile refusal gates

## PM Note

Ce ticket sépare le comportement SDR borné des futurs profils/HDR.

## Problem

Color behavior affects material, image, layer, blend, and store semantics and
must not be silently reinterpreted.

## Scope

- Add SDR color value/spec and store plan evidence.
- Add refusals for HDR, gainmap, ICC/CICP/profile-dependent behavior.

## Non-Goals

- Do not add broad color management.
- Do not add HDR support.

## Spec Sources

- `.upstream/specs/gpu-renderer/29-color-management-pipeline.md`

## Design Sketch

```kotlin
data class ColorPlanEvidence(val valueSpec: String, val storePlan: String)
```

## Acceptance Criteria

- [ ] SDR color facts are dumpable.
- [ ] Unsupported profile/HDR cases refuse.
- [ ] Material and pipeline keys include only behavior-affecting color facts.

## Required Evidence

- Color plan and refusal dumps.

## Fallback / Refusal Behavior

Unsupported color/profile cases refuse; no silent reinterpretation.

## Dashboard Impact

- Expected row: `gpu-renderer.color-sdr-boundary`
- Expected classification: `DependencyGated`
- Claim promotion allowed: no until reviewed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk git diff --check
```

## Status Notes

- `proposed`: SDR boundary first.

## Linear Labels

- `gpu-renderer`
- `milestone:M7`
- `area:color`
