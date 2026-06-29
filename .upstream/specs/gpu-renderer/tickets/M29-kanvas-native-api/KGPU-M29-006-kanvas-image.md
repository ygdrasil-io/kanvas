---
id: KGPU-M29-006
title: "KanvasImage — decode PNG/JPEG/WebP to GPU texture"
status: done
milestone: M29
priority: P0
owner_area: kanvas-api
claim_impact: ImplementationCandidate
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: false
depends_on: [KGPU-M12-010]
legacy_gate: null
---

# KGPU-M29-006 - KanvasImage — decode PNG/JPEG/WebP to GPU texture

## PM Note

`KanvasImage` integre le pipeline de decodage d'images (PNG, JPEG, WebP) de
`:codec:core` vers une texture GPU utilisable par `KanvasShader` et `drawImage`.
Ce ticket connecte les codecs Kanvas a l'API native pour que le PM voie des
images reelles dans le rendu Kanvas.

## Problem

`KanvasCanvas.drawImage()` and `KanvasShader.Bitmap` need a GPU texture created
from decoded image data. The codec pipeline (M12) produces decoded pixels but
has no Kanvas-native API integration. `KanvasImage` bridges image decoding to
texture upload through the native API.

## Scope

- Define `KanvasImage` as a GPU texture wrapper with codec provenance
- Implement `KanvasImage.decode(bytes, codecHint)` for PNG/JPEG/WebP
- Implement GPU texture upload from decoded pixel data
- Expose width, height, and color type metadata
- Use M12 codec pipeline for actual decoding

## Non-Goals

- No new codec implementations (M12 owns codecs)
- No animated image support (future milestone)
- No SVG or vector image support
- No mipmap generation or texture filtering configuration
- No LazyImage or deferred decode (future milestone)

## Spec Sources

- `.upstream/specs/gpu-renderer/README.md`
- `.upstream/specs/gpu-renderer/18-texture-image-ownership.md`
- `.upstream/specs/gpu-renderer/22-image-bitmap-codec-pipeline.md`
- `.upstream/specs/gpu-renderer/tickets/M12-dependencies/README.md`
- `.upstream/specs/gpu-renderer/tickets/M17-image-shader-codec-upload/README.md`

## Design Sketch

```kotlin
class KanvasImage private constructor(
    val width: Int,
    val height: Int,
    val colorType: ColorType,
    internal val gpuTexture: GPUTexture,
) {
    companion object {
        fun decode(bytes: ByteArray, codecHint: CodecHint? = null): KanvasImage
    }
}

enum class ColorType { RGBA_8888, BGRA_8888, Alpha_8, Gray_8 }
enum class CodecHint { PNG, JPEG, WebP }
```

## Acceptance Criteria

- [ ] `KanvasImage.decode()` accepts raw bytes for PNG, JPEG, and WebP
- [ ] GPU texture is created with correct dimensions and format
- [ ] `KanvasImage` exposes width, height, and color type
- [ ] Invalid or corrupt image data emits stable diagnostics

## Required Evidence

- `KanvasImage.kt` committed
- Texture creation transcript for PNG, JPEG, and WebP decodes
- Image metadata dump (dimensions, color type) for each format
- Diagnostic output for corrupt/invalid image bytes

## Fallback / Refusal Behavior

Unsupported or corrupt image data emits `unsupported-image-format` or
`image-decode-failed` diagnostics. No CPU-decoded texture placeholder. No
silent fallback to a solid-color rect.

## Dashboard Impact

- Expected row: `gpu-renderer.m29.kanvas-image`
- Expected classification: `ImplementationCandidate`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :kanvas:test
rtk ./gradlew --no-daemon :codec:core:test
rtk ./gradlew --no-daemon :gpu-renderer:test
```

## Status Notes

- `proposed`: Initial ticket.
- `done`: KanvasImage with decode(bytes, mimeType), lower() returns GPUImageSourceDescriptor.

## Linear Labels

- `gpu-renderer`
- `milestone:M29`
- `area:kanvas`
