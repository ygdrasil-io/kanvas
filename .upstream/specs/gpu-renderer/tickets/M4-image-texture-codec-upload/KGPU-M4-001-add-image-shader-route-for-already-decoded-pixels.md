---
id: KGPU-M4-001
title: "Add image shader route for already-decoded pixels"
status: proposed
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

- `proposed`: Decoded-pixel source only.

## Linear Labels

- `gpu-renderer`
- `milestone:M4`
- `area:images`
