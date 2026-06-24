---
id: KGPU-M26-001
title: "Upload PNG/JPEG to GPU texture via ImageUploadMaterializer"
status: proposed
milestone: M26
priority: P0
owner_area: execution-renderer
claim_impact: ImplementationCandidate
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M25-001, KGPU-M17-003]
legacy_gate: null
---

# KGPU-M26-001 - Upload PNG/JPEG to GPU texture via ImageUploadMaterializer

## PM Note

Le chemin bitmap echantillonne encore une texture procedurale. Ce ticket
uploade de vrais octets PNG/JPEG vers une texture GPU pour que le PM voie une
vraie image source.

## Problem

After M25-001, BitmapRect samples a procedural texture because no real decoded
image bytes are uploaded. M17-003 delivered `ImageUploadMaterializer`
(staging buffer -> texture) but it is not wired into the offscreen renderer, so
real PNG/JPEG content never reaches the GPU. Support cannot be promoted while
only procedural textures are sampled.

## Scope

- Wire `ImageUploadMaterializer` (staging buffer -> texture) into the offscreen renderer
- Decode PNG/JPEG bytes and stage them for upload to a GPU texture
- Bind the uploaded texture for the bitmap sampling path (KGPU-M25-001)
- Keep `RectOnlyOffscreenRenderer` available for diagnostic solid rendering

## Non-Goals

- No new codec support beyond what M17-003 delivers
- No bitmap scene PNG replacement (KGPU-M26-002/004)
- No glyph atlas upload (KGPU-M26-003)
- No product route activation

## Spec Sources

- `.upstream/specs/gpu-renderer/README.md`
- `.upstream/specs/gpu-renderer/tickets/M17-image-shader-codec-upload/README.md`
- `gpu-renderer/src/main/kotlin/.../gpu/ImageUploadMaterializer.kt`
- `gpu-renderer/src/main/kotlin/.../GpuNativeOffscreenRenderer.kt` (KGPU-M14-005)

## Design Sketch

```kotlin
val decoded = ImageDecoder.decode(pngOrJpegBytes)
val texture = ImageUploadMaterializer.materialize(decoded) // staging buffer -> texture
bindBitmapTexture(texture)
```

## Acceptance Criteria

- [ ] `ImageUploadMaterializer` is wired into the offscreen renderer
- [ ] PNG/JPEG bytes decode and stage to a GPU texture
- [ ] The uploaded texture binds to the bitmap sampling path
- [ ] `RectOnlyOffscreenRenderer` remains available for diagnostic solid rendering

## Required Evidence

- Staging-buffer-to-texture upload transcript (dimensions, format, byte count)
- Decode log for a representative PNG and JPEG fixture
- Texture-bind confirmation for the bitmap path

## Fallback / Refusal Behavior

If the GPU is unavailable, the renderer emits a `gpu-unavailable` diagnostic and
scenes remain not-yet-rendered. Decode failure emits a stable diagnostic; no
silent fallback to a procedural texture.

## Dashboard Impact

- Expected row: `gpu-renderer.m26.image-upload-texture`
- Expected classification: `ImplementationCandidate`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test
```

## Status Notes

- `proposed`: Initial ticket.

## Linear Labels

- `gpu-renderer`
- `milestone:M26`
- `area:execution-renderer`
