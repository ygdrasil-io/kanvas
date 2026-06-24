---
id: KGPU-M12-006
title: "Add GPU texture upload from decoded pixels with format validation"
status: done
milestone: M12
priority: P0
owner_area: codec-upload
claim_impact: TargetNative
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M12-005]
legacy_gate: null
---

# KGPU-M12-006 - Add GPU texture upload from decoded pixels with format validation

## PM Note

L'upload des pixels décodés vers la texture GPU doit être validé format par format. Une texture mal uploadée corrompt tout le pipeline image.

## Problem

Decoded RGBA8Unorm pixels must be uploaded to GPU textures through staging buffers with format validation before image shaders can sample them.

## Scope

- Add GPU texture creation from decoded RGBA8Unorm pixels
- Add staging buffer upload path with format validation
- Add texture readiness check before bind
- Produce upload trace for test images

## Non-Goals

- No mipmap generation
- No texture compression
- No YUV or external format uploads

## Spec Sources

- .upstream/specs/gpu-renderer/README.md

## Graphite Algorithm References

- [`GFX-GFX_TEXTURE_UPLOAD_ROOT`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-texture-upload-root) - source src/gpu/graphite/TextureProxy.cpp; Algorithm to study for this ticket's domain.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
class GpuTextureUpload(val source: DecodedPixels, val stagingBuffer: StagingBuffer, val texture: GpuTexture)
```

## Acceptance Criteria

- [ ] RGBA8Unorm pixels upload correctly to GPU texture
- [ ] Format mismatch is detected before upload and emits diagnostic
- [ ] Uploaded texture is bindable and sampleable in test shader

## Required Evidence

- GPU texture upload trace for PNG/JPEG test images
- Format validation diagnostic transcript
- Texture readback or sample verification

## Fallback / Refusal Behavior

Failed uploads emit stable diagnostic; texture binding refused until upload succeeds.

## Dashboard Impact

- Expected row: `gpu-renderer.m12.codec.texture-upload`
- Expected classification: `TargetNative`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*ImageUpload*'
```

## Status Notes

- `proposed`: Initial ticket.

## Linear Labels

- `gpu-renderer`
- `milestone:M12`
- `area:codec-upload`
