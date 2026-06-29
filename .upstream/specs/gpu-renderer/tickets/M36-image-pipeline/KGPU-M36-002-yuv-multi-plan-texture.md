---
id: KGPU-M36-002
title: "YUV multi-plan texture route"
status: done
milestone: M36
priority: P0
owner_area: images
claim_impact: TargetNative
route_kind: GPUNative
product_activation: false
release_blocking: false
adapter_required: false
depends_on: [KGPU-M1-001]
legacy_gate: null
---

# KGPU-M36-002 - YUV multi-plan texture route

## PM Note

Les sources YUV multi-plan (JPEG YCbCr, HEIF 4:2:0, AVIF YUV) sont uploadées
en textures GPU séparées et converties en RGB dans WGSL.

## Problem

Multi-plan YUV sources (JPEG YCbCr, HEIF 4:2:0, AVIF YUV) must be uploaded as
separate GPU textures and converted to RGB in WGSL fragment shaders. Without
an explicit YUV multi-plan descriptor and validated converter plan, these
sources cannot participate in the GPU rendering pipeline.

## Scope

- `GPUYUVMultiPlanDescriptor`: color space (BT.601, BT.709, BT.2020),
  chroma subsampling (4:2:0, 4:2:2, 4:4:4), plane count and per-plane
  dimensions, bit depth.
- `GPUYUVPlaneUploadPlan`: per-plane texture upload as r8unorm or r16unorm
  single-channel textures, buffer-to-texture copy plan.
- `GPUYUVToRGBCoverterPlan`: WGSL fragment shader that samples Y, U, V
  planes, applies matrix conversion, and applies transfer function.
- `GPUYUVSamplingPlan`: chroma siting (center, left, top-left), UV plane
  scaling for mismatched resolutions, sampling filter.

## Non-Goals

- Do not implement full-range vs. video-range negotiation before decoding
  is proven.
- Do not handle YUV formats with >3 planes (e.g., alpha planes) in this
  ticket.
- Do not support BT.2020 without accepted spec review.

## Spec Sources

- `.upstream/specs/gpu-renderer/README.md`
- `.upstream/specs/gpu-renderer/22-image-bitmap-codec-pipeline.md`

## Graphite Algorithm References

- [`GFX-TEXTURE-UPLOAD-ROOT`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-texture-upload-root) - source [TextureUtils.cpp:251](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/TextureUtils.cpp:251); Create a texture proxy view, compute mip levels and swizzles, build an upload source, and record a root upload task.
- [`GFX-DAWN-SAMPLER`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-dawn-sampler) - source [DawnSampler.cpp:52](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/dawn/DawnSampler.cpp:52); Translate tile modes to Dawn address modes, filter/mipmap options to sampler filters, and attach immutable YCbCr metadata when present.
- [`GFX-SAMPLER-DESC`](../GRAPHITE-ALGORITHM-REFERENCES.md#gfx-sampler-desc) - source [ResourceTypes.h:238](/Users/chaos/workspace/kanvas-forge/skia-main/src/gpu/graphite/ResourceTypes.h:238); Bit-pack tile modes, filter mode, mipmap mode, immutable sampler info, and external format bits into a hashable sampler descriptor.
- Boundary: Graphite is a working-algorithm reference only; do not port Graphite or Ganesh, and keep Kanvas WebGPU/WGSL acceptance criteria authoritative.

## Design Sketch

```kotlin
data class GPUYUVMultiPlanDescriptor(
    val colorSpace: YUVColorSpace,
    val subsampling: ChromaSubsampling,
    val planeCount: Int,
    val perPlaneDims: List<PlaneDimensions>,
    val bitDepth: Int,
)

data class GPUYUVPlaneUploadPlan(
    val planes: List<PlaneUpload>,
    val format: SingleChannelTextureFormat,
)

data class GPUYUVToRGBCoverterPlan(
    val wgslModule: WgslModuleId,
    val matrixCoefficients: YUVMatrixCoefficients,
    val transferFn: TransferFunction,
)

data class GPUYUVSamplingPlan(
    val chromaSiting: ChromaSiting,
    val uvScale: UVScaleStrategy,
)
```

## Acceptance Criteria

- [ ] JPEG YCbCr decoded to YUV planes and GPU-converted to RGB within
      1-bit tolerance of a CPU reference.
- [ ] At least one 4:2:0 and one 4:4:4 source accepted with evidence.
- [ ] Unsupported chroma siting emits stable diagnostic and refuses
      conversion.
- [ ] BT.2020 routes emit refusal diagnostic unless spec review has
      accepted BT.2020 YUV conversion.
- [ ] Formates with >3 planes are refused with stable reason.

## Required Evidence

- JPEG YCbCr → YUV planes → GPU WGSL conversion → RGB pixel dump (1-bit
  tolerance vs CPU reference).
- 4:2:0 and 4:4:4 fixture dumps with diff/stat artifacts.
- Refusal diagnostic dump for unsupported siting / BT.2020 / >3 planes.
- WGSL module validation passing wgsl4k parser.

## Fallback / Refusal Behavior

- Unsupported color space: `unsupported.image.yuv_color_space`.
- Unvalidated converter WGSL: `unsupported.image.yuv_converter_wgsl_unvalidated`.
- Unsupported chroma siting or plane count: stable diagnostic emitted,
  decode refused.
- No CPU-rendered complete YUV-to-RGB conversion for GPU composition.

## Dashboard Impact

- Expected row: `gpu-renderer.image.yuv-multi-plan`
- Expected classification: `TargetNative`
- Claim promotion allowed: no, unless all Required Evidence is attached and
  validation has passed.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*YUV*'
```

## Status Notes

- `proposed`: Initial ticket.
- `ready` (2026-06-28): promoted — milestone activated, autonomous implementation starting.
- `ready → review` (2026-06-28): implemented. Pending independent review.
- `review → done` (2026-06-29): promoted to done after independent review accepted linked evidence; no hidden product activation.

## Linear Labels

- `gpu-renderer`
- `milestone:M36`
- `area:images`
