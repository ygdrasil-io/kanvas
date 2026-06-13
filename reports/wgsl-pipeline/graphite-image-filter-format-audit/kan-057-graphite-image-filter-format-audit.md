# KAN-057 Graphite Image-Filter Intermediate Format Audit

Date: 2026-06-13
Decision: `no-change`
External source root: `/Users/chaos/workspace/kanvas-forge/skia-main`

## Summary

KAN-057 audited the direct Skia Graphite sources now available at
`/Users/chaos/workspace/kanvas-forge/skia-main` plus the existing Kanvas KAN-052
and FOR evidence.

Graphite does not expose one universal image-filter F16 intermediate policy and
does not provide a standalone rule for `rgba16float` intermediate storage
followed by byte presentation quantization. Instead, Skia's image-filter path
chooses an intermediate color type from destination/layer color info and
saveLayer flags, constructs premultiplied offscreen devices in the filter
context color space, and relies on target texture format / readback conversion
when rendering or reading the result.

Kanvas should not change `SkWebGpuDevice` intermediate format or present
quantization from Graphite evidence alone. KAN-052 remains blocked until a
Kanvas-native policy proof supplies row-local reference, CPU/GPU, diff/stat,
route, fallback, and perf/memory evidence.

## Ticket Scope Check

| Requirement | Result |
|---|---|
| Cite Graphite sources observed | Done: direct Skia source paths and symbols cited. |
| Separate Graphite facts from Kanvas interpretation | Done. |
| Compare to KAN-052 artifacts | Done. |
| Produce policy decision | `no-change`: no Graphite-derived Kanvas policy change. |
| Preserve non-claims | Done. |

## Graphite Source Evidence

| Source | Symbols / lines | Finding |
|---|---|---|
| `src/core/SkCanvas.cpp` | `image_filter_color_type`, lines 669-680 | Image filters ideally operate in the destination color type. Destinations with <=4 bytes per pixel and not `RGBA_8888`/`BGRA_8888` are upgraded to `kN32`; otherwise the destination color type is kept. F16/F32-like destinations remain high precision, but byte destinations generally remain byte/N32. |
| `src/core/SkCanvas.cpp` | `SkCanvas::internalDrawDeviceWithFilter`, lines 714-784 | `filterColorSpace` comes from the filter color info; `filterColorType` is `kAlpha_8` for coverage layers, otherwise `image_filter_color_type(filterColorInfo)`. The destination creates the image-filter backend with that color type. |
| `src/core/SkCanvas.cpp` | `SkCanvas::internalSaveLayer`, lines 1009-1027 | SaveLayer uses `kAlpha_8` for coverage-only layers, forces `kRGBA_F16` only when `kF16ColorType` is set, otherwise uses `image_filter_color_type(priorDevice->imageInfo().colorInfo())`. Alpha is premul and color space is the layer override or prior device color space. |
| `src/core/SkImageFilterTypes.h` | `skif::Backend`, `skif::Context`, lines 1099-1192 | `skif::Backend` creates offscreen intermediate renderable images and stores surface props plus color type. `skif::Context` carries output color space so intermediate images can match the final consumer and filtering can run in destination color space. |
| `src/core/SkImageFilterTypes.cpp` | `FilterResult::AutoSurface`, lines 528-630 | Filter nodes create intermediate devices through `ctx.backend()->makeDevice(size, ctx.refColorSpace(), props)`, clear to transparent, then snap a `SkSpecialImage`. |
| `src/gpu/graphite/TextureUtils.cpp` | `GraphiteBackend::makeDevice`, lines 720-749 | Graphite image-filter offscreen devices use `SkImageInfo::Make(size, this->colorType(), kPremul_SkAlphaType, colorSpace)` and `Device::Make(..., LoadOp::kDiscard, "ImageFilterResult")`. |
| `src/gpu/graphite/TextureUtils.cpp` | `renderable_colortype`, lines 76-99 | Renderable fallbacks preserve underlying data type where possible: F16-like color types render through `RGBA_F16`; `RGB_888x` renders through `RGBA_8888`. The comment explicitly avoids fallback that changes underlying data type. |
| `src/gpu/graphite/SpecialImage_Graphite.cpp` | `skgpu::graphite::SpecialImage`, `SkSpecialImages::MakeGraphite`, lines 20-84 | Graphite special images wrap Graphite-backed `SkImage`s and forward `image->imageInfo().colorInfo()` to `SkSpecialImage`; non-Graphite images are converted through `GetGraphiteBacked` when possible. |
| `src/gpu/graphite/TextureFormat.cpp` | `TextureFormatAutoClamps`, `PreferredTextureFormats`, lines 231-237 and 480-493 | `RGBA_8888` maps to `RGBA8`/`BGRA8`; `RGBA_F16` and `RGBA_F16Norm` map to `RGBA16F`. Floating-point formats do not auto-clamp; unsigned normalized formats do. |
| `src/gpu/graphite/DrawContext.cpp` | `DrawContext::Make`, `RenderPassDesc::Make`, lines 64-80 and 296-310 | Graphite rejects unknown/unpremul render targets, requires color-type/format compatibility, and derives render-pass format/write swizzle from the target texture. |
| `src/gpu/graphite/Context.cpp` | `Context::transferPixels`, lines 727-748 | GPU-to-CPU readback builds color-space transform steps from source and destination color info, then creates a texture-format transfer function for the source texture format and destination CPU color type. |
| `src/gpu/graphite/TextureFormatXferFn.cpp` | `TextureFormatXferFn::MakeGpuToCpu`, `RPOps::Make`, lines 272-328 | Readback conversion loads from the texture-format base color type, applies swizzles/color-space steps, and stores to the requested destination color type through raster-pipeline ops. |

## Graphite Interpretation For Kanvas

Graphite's behavior is conditional and destination-driven:

- F16 is preserved when the destination/layer color type is F16/F32-like or
  saveLayer requests `kF16ColorType`.
- Byte and low-bit formats do not automatically become F16; many are upgraded to
  `kN32`/`RGBA_8888` for alpha quality.
- Offscreen image-filter devices are premultiplied and use the filter context
  color space.
- Rendering to byte targets and GPU readback depend on target texture format and
  source/destination color info rather than a single image-filter quantization
  rule.

This is useful behavioral evidence, but it is not a policy that says Kanvas
should quantize `RGBA16Float` intermediates to bytes at a specific image-filter
or present boundary.

## Kanvas Current Policy Evidence

| Source | Evidence |
|---|---|
| `gpu-raster/README.md` | The intermediate defaults to `GPUTextureFormat.RGBA16Float`; final readback target is always `RGBA8Unorm`; the format toggle only affects the intermediate. |
| `gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt` | `intermediateFormat` defaults to `GPUTextureFormat.RGBA16Float`; `HeadlessTarget` is created with `GPUTextureFormat.RGBA8Unorm`; `intermediateTexture` uses `intermediateFormat`. |
| `gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt` | `SkWebGpuDevice.cropNonNullOffsetChildPrePassScratch` uses `format = intermediateFormat` for the KAN-052 crop non-null prepass scratch. |
| `gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt` | Blur image-filter scratch textures `blurImageFilterScratchH` and `blurImageFilterScratchV` also use `format = intermediateFormat`. |
| `gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/HeadlessTarget.kt` | The headless target supports byte readback formats and returns row-major RGBA bytes from staging. |
| `gpu-raster/src/main/resources/shaders/present_identity.wgsl` | The identity present pass reads the intermediate with `textureLoad` and returns the value to the final `RGBA8Unorm` target. |
| `gpu-raster/src/main/resources/shaders/present_pass.wgsl` | The color-space present pass reads with `textureLoad`, unpremultiplies, transforms sRGB encoded values to Rec.2020 encoded values, then writes the final output. |
| `gpu-raster/src/main/resources/shaders/layer_composite.wgsl` | Layer composite samples the child intermediate and writes the parent intermediate; comments state the shader is format-agnostic for `RGBA16Float` or `RGBA8Unorm`. |

Kanvas currently stores premul sRGB-coded values in the WebGPU intermediate. The
F16 format buys sub-byte precision, but it is not a true linear working space.
The byte boundary remains the present pass into `RGBA8Unorm` readback.

## CPU Reference Evidence

| Source | Evidence |
|---|---|
| `cpu-raster/src/main/kotlin/org/skia/dm/RasterSinkF16.kt` | `RasterSinkF16` renders into `SkColorType.kRGBA_F16Norm` and is the canonical reference path for Kanvas. |
| `kanvas-skia/src/main/kotlin/org/skia/foundation/SkBitmap.kt` | `kRGBA_8888` is 8-bit non-premul storage; `kRGBA_F16Norm` is premul float storage in `[0, 1]`, used to avoid 8-bit quantization drift. |
| `kanvas-skia/src/main/kotlin/org/skia/foundation/SkImageInfo.kt` | `kRGBA_F16Norm` defaults to premul alpha and 8 bytes per pixel; `kRGBA_8888` defaults to unpremul alpha and 4 bytes per pixel. |

This supports the existing Kanvas distinction between CPU F16 reference storage,
WebGPU F16 intermediate storage, and final byte readback. It does not prove that
Graphite would choose Kanvas's current policy or a byte-quantized alternative for
KAN-052.

## KAN-052 Comparison

| Source | Evidence |
|---|---|
| `reports/wgsl-pipeline/image-filter-visual-delta/kan-052-image-filter-visual-delta.md` | KAN-052 is `blocked=true`, `rendererChanged=false`, with root cause `rgba16float-intermediate-store-to-present-byte-quantization-policy`. |
| `reports/wgsl-pipeline/scenes/artifacts/crop-image-filter-nonnull-prepass/route-gpu.json` | Selected route is `webgpu.image-filter.crop-nonnull-offset-prepass.final-crop-composite`; fallback is `none`; intermediate texture is `SkWebGpuDevice.cropNonNullOffsetChildPrePassScratch`. |
| `reports/wgsl-pipeline/2026-06-03-for-259-intermediate-store-present-audit.md` | A test-side `RGBA8Unorm` store/load simulation matched the higher reference bytes for two residuals, but no normal correction was applied. |
| `reports/wgsl-pipeline/2026-06-03-for-261-whole-scene-rgba8-intermediate-audit.md` | Whole-scene `RGBA8Unorm` candidate improved two residual cases, but safe correction was not proven for precision-sensitive cases. |
| `reports/wgsl-pipeline/2026-06-03-for-264-rgba16float-present-quantization-audit.md` | RGBA16Float present-byte quantization remains diagnostic due to missing family-bound proof. |
| `reports/wgsl-pipeline/2026-06-04-for-306-bounded-image-filter-residual-policy.md` | FOR-261 to FOR-265 signals remain diagnostic-only until complete local proof exists. |

KAN-052 should not resume as a crop-only image-filter fix. The selected crop
route already has `fallbackReason=none`, and the residual is explicitly outside
the crop prepass scope.

## Decision

`no-change`

Graphite source evidence does not justify moving Kanvas's byte quantization
boundary or switching the default intermediate policy. It shows a
destination/layer-driven color-type model, not a universal image-filter F16
policy.

Kanvas therefore keeps the current policy unresolved:

- no Graphite-derived F16 policy is adopted;
- no `RGBA8Unorm` intermediate switch is adopted;
- no present-time byte quantization correction is adopted;
- KAN-052 remains blocked until a Kanvas-native policy proof exists.

## Next Decision Options

1. Run a Kanvas-native policy proof ticket that tests intermediate format and
   present quantization candidates across KAN-052 residual rows, exact controls,
   precision-sensitive controls, route diagnostics, fallback stability, and
   perf/memory impact.
2. Keep current `RGBA16Float` intermediate plus `RGBA8Unorm` present readback and
   leave KAN-052 blocked until complete local proof exists.

## Non-Claims

- No image-filter support expansion is claimed.
- No renderer, shader, threshold, golden, validator, or fallback policy changed.
- No Ganesh or Graphite implementation is ported.
- No SkSL compiler, IR, or VM is introduced.
- No `expected-unsupported`, `implementation-gap`, or KAN-052 blocked row is
  promoted to support.

## Validation Notes

This is a static audit artifact. No Gradle gate was required because no renderer,
shader, Kotlin source, golden, or validator behavior changed.
