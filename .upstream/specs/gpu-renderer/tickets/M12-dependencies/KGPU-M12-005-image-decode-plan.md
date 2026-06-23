---
id: KGPU-M12-005
title: "Add GPU image decode plan: codec selection -> decode -> RGBA8Unorm pixels"
status: done
milestone: M12
priority: P0
owner_area: codec-decode
claim_impact: TargetNative
route_kind: CPUReferenceOnly
product_activation: false
release_blocking: false
adapter_required: false
depends_on: []
legacy_gate: null
---

# KGPU-M12-005 - Add GPU image decode plan: codec selection -> decode -> RGBA8Unorm pixels

## PM Note

Le plan de décodage image est la porte d'entrée pour toutes les textures GPU. Sans sélection de codec fiable, les images PNG/JPEG/WebP/GIF ne pourront pas devenir des textures.

## Problem

GPU image rendering requires a reliable decode pipeline that selects the correct codec, decodes to RGBA8Unorm pixels, and validates the output before texture upload.

## Scope

- Add codec selection based on image format detection (PNG/JPEG/WebP/GIF)
- Add decode-to-RGBA8Unorm path with pixel format validation
- Add decode error diagnostics with stable refusal messages
- Produce decode contract fixture dumps

## Non-Goals

- No HEIF/AVIF decode (dependency-gated)
- No HDR or wide-gamut decode
- No animated image decode

## Spec Sources

- .upstream/specs/gpu-renderer/README.md

## Graphite Algorithm References

- [`GFX-GFX_IMAGE_COPY`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-image-copy) - source src/gpu/graphite/ImageUtils.cpp; Algorithm to study for this ticket's domain.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
class ImageDecodePlan(val codec: Codec, val decodedPixels: RGBA8UnormPixels)
```

## Acceptance Criteria

- [ ] PNG/JPEG/WebP/GIF images decode to RGBA8Unorm pixels correctly
- [ ] Decode errors produce stable diagnostics without crashing
- [ ] Decoded pixel dimensions match source image metadata

## Required Evidence

- Decode output for PNG, JPEG, WebP, GIF test images
- Error diagnostic transcript for unsupported formats
- Pixel dimension validation transcript

## Fallback / Refusal Behavior

Unsupported formats emit stable diagnostic; image shader route refuses with clear error.

## Dashboard Impact

- Expected row: `gpu-renderer.m12.codec.decode-plan`
- Expected classification: `TargetNative`
- Claim promotion allowed: no, unless all Required Evidence is attached and validation has passed.

## Validation

```bash
rtk ./gradlew --no-daemon :codec:test
```

## Status Notes

- `proposed`: Initial ticket.

## Linear Labels

- `gpu-renderer`
- `milestone:M12`
- `area:codec-decode`
