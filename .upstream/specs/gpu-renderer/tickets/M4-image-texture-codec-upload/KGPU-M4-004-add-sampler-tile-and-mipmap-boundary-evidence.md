---
id: KGPU-M4-004
title: "Add sampler tile and mipmap boundary evidence"
status: blocked
milestone: M4
priority: P1
owner_area: textures-samplers
claim_impact: TargetNative
route_kind: GPUNative
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

## Graphite Algorithm References

- [`GFX-IMAGE-SAMPLER-KEY`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-image-sampler-key) - source [KeyHelpers.cpp:530](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/KeyHelpers.cpp:530); Study hardware tiling substitution and image shader variant selection.
- [`GFX-SAMPLER-DESC`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-sampler-desc) - source [ResourceTypes.h:238](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/ResourceTypes.h:238); Reference compact sampler/tile/mipmap descriptor keying.
- [`GFX-DAWN-SAMPLER`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-dawn-sampler) - source [DawnSampler.cpp:52](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/dawn/DawnSampler.cpp:52); Use WebGPU/Dawn sampler translation for Kanvas sampler mapping and refusals.
- [`GFX-MIPMAP-GENERATION`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-mipmap-generation) - source [TextureUtils.cpp:553](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/TextureUtils.cpp:553); Reference mipmap generation and degrade/refusal policy when mipmaps are absent.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

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

- `blocked`: Boundary evidence before expansion is gated on native sampler
  proof. This ticket is `TargetNative`/`GPUNative` and adapter-required;
  promotion needs real WebGPU/adapter sampler evidence for tile/filter/mipmap
  mapping, behavior-affecting key boundaries, unsupported
  cubic/aniso/perspective diagnostics, and reference or readback artifacts. The
  M4-001 prepared decoded-pixel route and M4-002 uploaded-artifact ownership
  gate do not by themselves prove native sampler support or mipmap behavior.

## Linear Labels

- `gpu-renderer`
- `milestone:M4`
- `area:textures`
