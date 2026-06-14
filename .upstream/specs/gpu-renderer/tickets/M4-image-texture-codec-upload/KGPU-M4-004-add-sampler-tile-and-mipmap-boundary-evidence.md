---
id: KGPU-M4-004
title: "Add sampler tile and mipmap boundary evidence"
status: proposed
milestone: M4
priority: P1
owner_area: textures-samplers
claim_impact: TargetNative
route_kind: mixed
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M4-001]
legacy_gate: null
---

# KGPU-M4-004 - Add sampler tile and mipmap boundary evidence

## PM Note

Ce ticket rend visibles les limites de sampling plutôt que de les approximer.

## Problem

Tile, filter, mipmap, and sampler policy affect behavior and must appear in
keys, dumps, or refusals.

## Scope

- Add sampler/tile/mipmap plan dumps for image routes.
- Add refusals for unsupported mipmap, cubic/aniso, and perspective cases.

## Non-Goals

- Do not add broad tile-mode or mipmap support.
- Do not add color-managed decode.

## Spec Sources

- `.upstream/specs/gpu-renderer/18-texture-image-ownership.md`
- `.upstream/specs/gpu-renderer/22-image-bitmap-codec-pipeline.md`

## Design Sketch

```kotlin
data class SamplerBoundaryEvidence(val tileMode: String, val mipPolicy: String)
```

## Acceptance Criteria

- [ ] Sampler facts are deterministic and dumpable.
- [ ] Unsupported sampling modes refuse stably.
- [ ] Pipeline keys include behavior-affecting sampler facts only.

## Required Evidence

- Sampler, tile, mip, key, and refusal dumps.

## Fallback / Refusal Behavior

Unsupported sampling modes refuse instead of silently downgrading.

## Dashboard Impact

- Expected row: `gpu-renderer.sampler-boundary`
- Expected classification: `TargetNative`
- Claim promotion allowed: no until reviewed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk git diff --check
```

## Status Notes

- `proposed`: Boundary evidence before expansion.

## Linear Labels

- `gpu-renderer`
- `milestone:M4`
- `area:textures`
