---
id: KGPU-M36-001
title: "HEIF/AVIF gate promotion"
<<<<<<< HEAD
status: blocked
=======
status: proposed
>>>>>>> master
milestone: M36
priority: P0
owner_area: images
claim_impact: DependencyGated
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: false
depends_on: [KGPU-M1-001]
legacy_gate: null
---

# KGPU-M36-001 - HEIF/AVIF gate promotion

## PM Note

HEIF et AVIF sont dependency-gated. Ce ticket définit les critères de
promotion et les contrats de codec.

## Problem

HEIF and AVIF support cannot be claimed until KanvasImageCodec registry
entries are accepted and the decoders pass still-decode conformance.
Without explicit gate criteria, the dependency boundary is ambiguous and
unsupported profiles could be silently treated as supported.

## Scope

- `GPUHEIFCodecDescriptor`: container types (still, sequence, grid, tiled),
  codec ID, capability flags, approved profiles.
- `GPUAVIFCodecDescriptor`: AV1 still, animated, HDR, gain map, alpha channel,
  codec ID, capability flags, approved profiles.
- `GPUISOBMFFParsePlan`: ISOBMFF box hierarchy parser, item reference
  resolution, decoder configuration record extraction.

Promotion when:
- A `KanvasImageCodec` with codec ID is registered.
- Capability report shows `still_decode` tier.
- Conformance evidence reaches beta+ level.
- Valid HEIF still and AVIF still with GPU evidence.

## Non-Goals

- Do not accept any patent-encumbered profile without explicit legal review.
- Do not ship codec binaries or link against platform-specific libraries.
- Do not claim animated HEIF/AVIF or sequence decoding in this ticket.

## Spec Sources

- `.upstream/specs/gpu-renderer/README.md`
- `.upstream/specs/gpu-renderer/22-image-bitmap-codec-pipeline.md`

## Graphite Algorithm References

- [`GFX-TEXTURE-UPLOAD-ROOT`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-texture-upload-root) - source [TextureUtils.cpp:251](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/TextureUtils.cpp:251); Create a texture proxy view, compute mip levels and swizzles, build an upload source, and record a root upload task before rendering tasks consume the texture.
- [`GFX-IMAGE-COPY`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-image-copy) - source [Image_Graphite.cpp:90](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/Image_Graphite.cpp:90); Reject non-copyable sources, choose copy-as-draw if direct copy is unsupported, allocate a destination proxy, record a copy task.
- [`GFX-UPLOAD-TASK`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-upload-task) - source [UploadTask.cpp:309](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/task/UploadTask.cpp:309); Instantiate upload targets during resource preparation, copy buffer-to-texture commands, crop replay-target uploads.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
data class GPUHEIFCodecDescriptor(
    val codecId: String,
    val containerTypes: Set<HEIFContainerType>,
    val approvedProfiles: Set<String>,
)

data class GPUAVIFCodecDescriptor(
    val codecId: String,
    val decodeModes: Set<AVIFDecodeMode>,
    val approvedProfiles: Set<String>,
)

data class GPUISOBMFFParsePlan(
    val boxHierarchy: ISOBMFFBoxNode,
    val itemRefs: List<ISOBMFFItemRef>,
    val decoderConfig: DecoderConfigurationRecord,
)
```

## Acceptance Criteria

- [ ] `GPUHEIFCodecDescriptor` and `GPUAVIFCodecDescriptor` contracts are
      defined and accepted.
- [ ] `GPUISOBMFFParsePlan` parses box hierarchy, item refs, and decoder config.
- [ ] Gate criteria document links promotion to KanvasImageCodec registry entry.
- [ ] Unregistered HEIF/AVIF codec IDs emit stable diagnostics.

## Required Evidence

- `GPUHEIFCodecDescriptor` and `GPUAVIFCodecDescriptor` contract dumps.
- `GPUISOBMFFParsePlan` fixture output for valid HEIF still and AVIF still.
- Capability report showing `still_decode` tier for registered codecs.
- Conformance evidence at beta+ level.

## Fallback / Refusal Behavior

- Unregistered codec ID: `unsupported.image.heif_codec_unregistered` /
  `unsupported.image.avif_codec_unregistered`.
- Unapproved profile: diagnostic emitted, decode refused.
- No silent fallback to CPU-rendered complete image decode.

## Dashboard Impact

- Expected row: `gpu-renderer.image.heif-avif`
- Expected classification: `DependencyGated`
- Claim promotion allowed: no, unless KanvasImageCodec registry entry is
  accepted and all Required Evidence is linked.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*HEIF*'
```

## Status Notes

- `proposed`: Initial ticket.
<<<<<<< HEAD
- `blocked` (2026-06-28): Blocked on KanvasImageCodec registry entry for HEIF/AVIF codec support
=======
>>>>>>> master

## Linear Labels

- `gpu-renderer`
- `milestone:M36`
- `area:images`
