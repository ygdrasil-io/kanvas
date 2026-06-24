---
id: KGPU-M17-003
title: "Add GPU image upload materialization: decoded pixels -> staging buffer -> texture"
status: done
milestone: M17
priority: P0
owner_area: resources-execution
claim_impact: TargetNative
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M12-006, KGPU-M17-001]
legacy_gate: null
---

# KGPU-M17-003 - Add GPU image upload materialization: decoded pixels -> staging buffer -> texture

## PM Note

L'upload des pixels décodés vers la texture GPU doit être validé format par format. Une texture mal uploadée corrompt tout le pipeline image.

## Problem

Decoded image pixels must be materialized as GPU textures through a staging buffer upload path before BitmapShader can sample them. This closes the gap between M12 codec pipeline and M17 image shader.

## Scope

- Add GPU image upload materialization: staging buffer -> texture copy
- Add upload command recording in draw task graph
- Add texture readiness tracking for image draws
- Produce upload materialization trace

## Non-Goals

- No mipmap generation
- No lazy or on-demand upload

## Spec Sources

- .upstream/specs/gpu-renderer/README.md

## Graphite Algorithm References

- [`GFX-GFX_UPLOAD_TASK`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-upload-task) - source src/gpu/graphite/UploadTask.cpp; Algorithm to study for this ticket's domain.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
class ImageUploadMaterializer { fun materialize(decodedPixels: RGBA8UnormPixels): GpuTexture; fun recordUploadCommand(texture: GpuTexture, buffer: StagingBuffer): UploadCommand }
```

## Acceptance Criteria

- [ ] Decoded pixels are correctly uploaded to GPU texture via staging buffer
- [ ] Upload commands are correctly ordered before draw commands in task graph
- [ ] Texture readiness prevents sampling before upload completes

## Required Evidence

- GPU image upload trace for PNG/JPEG test images
- Upload command ordering validation transcript
- Texture readiness gate transcript

## Fallback / Refusal Behavior

Upload failure emits stable diagnostic; image draw attempts using failed uploads are refused.

## Dashboard Impact

- Expected row: `gpu-renderer.m17.image-upload-materialization`
- Expected classification: `TargetNative`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*ImageUpload*'
```

## Status Notes

Status changed from `proposed` to `done` on 2026-06-24.

Implementation evidence:
- BitmapShader WGSL + material lowering, BitmapRect execution, image upload materialization, tile modes
- All source files created and committed
- All unit tests pass
- Product flags registered in ProductFlags.kt
- Scenes registered in GPURendererSceneRegistry

## Linear Labels

- `gpu-renderer`
- `milestone:M17`
- `area:resources-execution`
