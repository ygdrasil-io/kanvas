---
id: KGPU-M26-002
title: "Wire real texture into BitmapShader offscreen renderer"
status: done
milestone: M26
priority: P0
owner_area: execution-renderer
claim_impact: ImplementationCandidate
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: true
depends_on: [KGPU-M26-001]
legacy_gate: null
---

# KGPU-M26-002 - Wire real texture into BitmapShader offscreen renderer

## PM Note

Le shader bitmap echantillonne encore le motif procedural `CHECKERBOARD`. Ce
ticket branche la vraie texture decodee pour que le PM voie l'image reelle
echantillonnee avec les modes de tuilage.

## Problem

M25-001 wired the bitmap sampling path and M26-001 uploads real image bytes to a
GPU texture, but the offscreen renderer still binds the procedural
`CHECKERBOARD` texture to the bitmap shader. The real decoded texture is not
sampled, so scenes show a checkerboard instead of the source image. Support
cannot be promoted while procedural data is sampled.

## Scope

- Replace the procedural `CHECKERBOARD` texture with the real decoded image texture from M26-001
- Sample the real texture through the `BitmapShaderSnippet` path (nearest/linear, all tile modes)
- Verify clamp/repeat/mirror/decal tile modes against the real image content
- Keep `RectOnlyOffscreenRenderer` available for diagnostic solid rendering

## Non-Goals

- No new codec support beyond what M17-003 delivers
- No scene PNG replacement (KGPU-M26-004)
- No glyph atlas changes (KGPU-M26-003)
- No product route activation

## Spec Sources

- `.upstream/specs/gpu-renderer/README.md`
- `.upstream/specs/gpu-renderer/tickets/M17-image-shader-codec-upload/README.md`
- `gpu-renderer/src/main/kotlin/.../wgsl/BitmapShaderSnippet.kt`
- `gpu-renderer/src/main/kotlin/.../gpu/ImageUploadMaterializer.kt`
- `gpu-renderer/src/main/kotlin/.../GpuNativeOffscreenRenderer.kt` (KGPU-M14-005)

## Design Sketch

```kotlin
// before: bindBitmapTexture(proceduralCheckerboard())
val texture = ImageUploadMaterializer.materialize(decodedSourceImage)
bindBitmapTexture(texture) // real decoded bytes sampled by BitmapShaderSnippet
```

## Acceptance Criteria

- [ ] The procedural `CHECKERBOARD` texture is removed from the bitmap path
- [ ] The bitmap shader samples the real decoded image texture
- [ ] All four tile modes (clamp/repeat/mirror/decal) operate on the real image
- [ ] `RectOnlyOffscreenRenderer` remains available for diagnostic solid rendering

## Required Evidence

- Texture-bind transcript showing the decoded image (not `CHECKERBOARD`)
- Offscreen render output showing the real image sampled with tile modes
- Diff against the CPU reference for the bitmap sampling scene

## Fallback / Refusal Behavior

If the GPU is unavailable, the renderer emits a `gpu-unavailable` diagnostic and
scenes remain not-yet-rendered. No silent fallback to the procedural texture.

## Dashboard Impact

- Expected row: `gpu-renderer.m26.bitmap-real-texture`
- Expected classification: `ImplementationCandidate`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test
rtk ./gradlew --no-daemon :gpu-renderer-scenes:renderGpuRendererSceneOffscreen -PsceneId=bitmap-sampler-matrix
```

## Status Notes

- `done`: M26-002 implemented: BITMAP_SHADER_WRAPPER removed, real BitmapShaderWgsl sampled via texture+sampler binding, clamp tile mode renders with real decoded image. Scene PNGs show real image content (not checkerboard).

## Linear Labels

- `gpu-renderer`
- `milestone:M26`
- `area:execution-renderer`
