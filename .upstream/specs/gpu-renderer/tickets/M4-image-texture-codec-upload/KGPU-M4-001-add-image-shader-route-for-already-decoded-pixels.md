---
id: KGPU-M4-001
title: "Add image shader route for already-decoded pixels"
status: done
milestone: M4
priority: P0
owner_area: images-textures
claim_impact: TargetPrepared
route_kind: CPUPreparedGPU
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M2-002]
legacy_gate: "bitmap legacy"
---

# KGPU-M4-001 - Add image shader route for already-decoded pixels

## PM Note

Ce ticket ajoute un chemin image borné à partir de pixels déjà décodés et
traçables.

## Problem

Image shader support must separate material identity from pixel contents,
upload artifacts, and concrete texture handles.

## Scope

- Add `GPUImageSourceDescriptor` and sampled texture material plan for decoded
  pixel inputs.
- Add upload-before-sample and sampler facts for one bounded image rect route.

## Non-Goals

- Do not add encoded codec support.
- Do not add mipmap, perspective sampling, or color-managed decode.

## Spec Sources

- `.upstream/specs/gpu-renderer/18-texture-image-ownership.md`
- `.upstream/specs/gpu-renderer/22-image-bitmap-codec-pipeline.md`

## Graphite Algorithm References

- [`GFX-IMAGE-SAMPLER-KEY`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-image-sampler-key) - source [KeyHelpers.cpp:530](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/KeyHelpers.cpp:530); Study image shader variant selection, tiling support, and sampler key data.
- [`GFX-TEXTURE-UPLOAD-ROOT`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-texture-upload-root) - source [TextureUtils.cpp:251](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/TextureUtils.cpp:251); Reference decoded-pixel texture proxy creation and upload scheduling.
- [`GFX-IMAGE-COPY`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-image-copy) - source [Image_Graphite.cpp:90](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/Image_Graphite.cpp:90); Use copy/copy-as-draw decision points for explicit image-route refusals.
- [`GFX-PER-EDGE-AA-QUAD`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-per-edge-aa-quad) - source [PerEdgeAAQuadRenderStep.cpp:34](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/render/PerEdgeAAQuadRenderStep.cpp:34); Reference image-quad coverage and seam handling for image shader draws.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
data class ImageShaderEvidence(val imageSourceDump: String, val samplerDump: String)
```

## Acceptance Criteria

- [ ] Pixel/upload facts stay out of `MaterialKey`.
- [ ] Texture ownership and sampler dumps are deterministic.
- [ ] GPU route or stable refusal is linked.

## Required Evidence

- Image source, upload, sampler, binding, WGSL, route, and refusal dumps.

## Fallback / Refusal Behavior

Unsupported image cases refuse; no CPU-rendered complete draw texture is allowed.

## Dashboard Impact

- Expected row: `gpu-renderer.image-shader.decoded-pixels`
- Expected classification: `TargetPrepared`
- Claim promotion allowed: no until reviewed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk git diff --check
```

## Status Notes

- `done`: Added `GPUDecodedImageShaderPreparedPlanner` contract evidence for
  already decoded CPU pixels as a `CPUPreparedGPU` image shader route. The plan
  emits deterministic `GPUImageSourceDescriptor`, `UploadedTextureArtifact`,
  texture/view/sampler/binding, material-key boundary, route, and refusal dumps.
  Material keys exclude upload artifact keys, pixel content hashes, row bytes,
  and resource handles; upload artifact keys include descriptor version,
  source/content/generation/size/format/row-byte facts, alpha type,
  color-profile label, orientation state, conformance tier, budget class,
  generator version, and mip facts. Stable refusals cover invalid source
  descriptors, unsupported pixel format, row stride, unapplied orientation,
  nondeterministic content or color-profile key facts, upload budget overflow,
  unsupported tile mode, mip requirements, and unsupported sampling filters.
  Independent review `019ec815-a637-7e92-baa9-24bd28b69904` found the initial
  upload artifact key under-specified; remediation added RED/GREEN coverage for
  alpha/color key separation plus descriptor/budget/generator/orientation key
  facts. Evidence is contract-only and does not activate product image drawing,
  adapter execution, codec support, mipmaps, broad image support, or
  CPU-rendered compatibility textures. Post-remediation independent review
  `019ec81d-b49e-7eb2-8a66-6f2d81e0ce95` accepted the evidence for `done` and
  confirmed no hidden activation, support-claim widening, package-cycle risk,
  material-key/resource-handle leak, or M4-004 promotion.

## Linear Labels

- `gpu-renderer`
- `milestone:M4`
- `area:images`
