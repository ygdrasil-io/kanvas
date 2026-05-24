package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Port of Skia's `gm/texelsubset.cpp`.
 *
 * Upstream defines a family of 8 GMs — each a `TexelSubset : GpuGM` — that
 * directly exercise `GrTextureEffect::MakeSubset` (and `GrTextureEffect::Make`
 * for a reference draw) across every combination of:
 *
 *  - filter: `kNearest` / `kLinear`
 *  - mipmap mode: `kNone` / `kNearest` / `kLinear`
 *  - scale direction: downscale (`up=false`) / upscale (`up=true`)
 *
 * The 8 registered GMs (see `DEF_GM` macros in the source):
 *
 * | class (this file)                        | GM name                                   |
 * |------------------------------------------|-------------------------------------------|
 * | [TexelSubsetNearestNoneDownGM]           | `texel_subset_nearest_down`               |
 * | [TexelSubsetNearestNoneUpGM]             | `texel_subset_nearest_up`                 |
 * | [TexelSubsetLinearNoneDownGM]            | `texel_subset_linear_down`                |
 * | [TexelSubsetLinearNoneUpGM]              | `texel_subset_linear_up`                  |
 * | [TexelSubsetNearestMipNearestDownGM]     | `texel_subset_nearest_mipmap_nearest_down`|
 * | [TexelSubsetLinearMipNearestDownGM]      | `texel_subset_linear_mipmap_nearest_down` |
 * | [TexelSubsetNearestMipLinearDownGM]      | `texel_subset_nearest_mipmap_linear_down` |
 * | [TexelSubsetLinearMipLinearDownGM]       | `texel_subset_linear_mipmap_linear_down`  |
 *
 * ## Canvas size
 *
 * From `getISize()` in the C++ source, with
 * `kN = GrSamplerState::kWrapModeCount = 4`,
 * `kImageSize = {128, 88}`, `kDrawPad = 10`, `kTestPad = 10`:
 *
 * ```
 * w = kTestPad + 2*kN*(kImageSize.width  + 2*kDrawPad + kTestPad) = 1274
 * h = kTestPad + 2*kN*(kImageSize.height + 2*kDrawPad + kTestPad) =  954
 * ```
 *
 * The factor of 2 in `2*kN` comes from two texture matrices × 4 wrap-mode
 * rows (the tm loop runs inside the my loop accumulating y, giving 2×4 = 8
 * rows), and kN columns × 2 draws per column (subset-effect + reference).
 *
 * ## Classification: INTRACTABLE.GPU_ONLY
 *
 * The entire `onDraw` is gated behind a `GpuGM` context:
 * ```cpp
 * auto sdc = skgpu::ganesh::TopDeviceSurfaceDrawContext(canvas);
 * if (!sdc) { *errorMsg = kErrorMsg_DrawSkippedGpuOnly; return DrawResult::kSkip; }
 * ```
 *
 * All drawing goes through:
 *
 * | C++ symbol                                   | Role                                        |
 * |----------------------------------------------|---------------------------------------------|
 * | `GpuGM`                                      | Base class — only invoked on Ganesh context |
 * | `skgpu::ganesh::TopDeviceSurfaceDrawContext`  | Ganesh surface introspection                |
 * | `GrMakeCachedBitmapProxyView`                | Upload bitmap to a GPU texture proxy        |
 * | `GrTextureEffect::MakeSubset`                | Texel-subset fragment processor             |
 * | `GrTextureEffect::Make`                      | Plain texture fragment processor            |
 * | `GrSamplerState` (Wrap / Filter / MipmapMode)| GPU sampler state                           |
 * | `sk_gpu_test::test_ops::MakeRect`            | Test-only GPU draw op                       |
 * | `SurfaceDrawContext::addDrawOp`              | Ganesh draw-op submission                   |
 *
 * None of these exist in the `:kanvas-skia` CPU-raster / WebGPU pipeline.
 * All `onDraw` bodies therefore call `TODO("STUB.GR_TEXTURE_EFFECT_MAKE_SUBSET")`.
 *
 * Upstream file: `gm/texelsubset.cpp`
 *
 * C++ `onDraw` outline:
 * ```cpp
 * auto sdc = skgpu::ganesh::TopDeviceSurfaceDrawContext(canvas);
 * if (!sdc) { *errorMsg = kErrorMsg_DrawSkippedGpuOnly; return DrawResult::kSkip; }
 *
 * auto view = std::get<0>(GrMakeCachedBitmapProxyView(rContext, GrMippedBitmap(fBitmap), ...));
 * // Build texelSubset rect, two texture matrices, extract subset bitmap+view
 * for (int tm = 0; tm < textureMatrices.size(); ++tm) {
 *   for (int my = 0; my < GrSamplerState::kWrapModeCount; ++my) {
 *     for (int mx = 0; mx < GrSamplerState::kWrapModeCount; ++mx) {
 *       GrSamplerState sampler(wmx, wmy, fFilter, fMipmapMode);
 *       // Draw 1: GrTextureEffect::MakeSubset + test_ops::MakeRect -> sdc->addDrawOp
 *       // Draw 2: GrTextureEffect::Make (reference, no subset) -> sdc->addDrawOp
 *     }
 *   }
 * }
 * return DrawResult::kOk;
 * ```
 */

private val kTexelSubsetSize = SkISize.Make(1274, 954)

/**
 * `texel_subset_nearest_down` — `Filter::kNearest`, `MipmapMode::kNone`, upscale=false.
 */
public class TexelSubsetNearestNoneDownGM : GM() {
    init { setBGColor(0xFFFFFFFF.toInt()) }
    override fun getName(): String = "texel_subset_nearest_down"
    override fun getISize(): SkISize = kTexelSubsetSize
    override fun onDraw(canvas: SkCanvas?) {
        TODO("STUB.GR_TEXTURE_EFFECT_MAKE_SUBSET")
    }
}

/**
 * `texel_subset_nearest_up` — `Filter::kNearest`, `MipmapMode::kNone`, upscale=true.
 */
public class TexelSubsetNearestNoneUpGM : GM() {
    init { setBGColor(0xFFFFFFFF.toInt()) }
    override fun getName(): String = "texel_subset_nearest_up"
    override fun getISize(): SkISize = kTexelSubsetSize
    override fun onDraw(canvas: SkCanvas?) {
        TODO("STUB.GR_TEXTURE_EFFECT_MAKE_SUBSET")
    }
}

/**
 * `texel_subset_linear_down` — `Filter::kLinear`, `MipmapMode::kNone`, upscale=false.
 */
public class TexelSubsetLinearNoneDownGM : GM() {
    init { setBGColor(0xFFFFFFFF.toInt()) }
    override fun getName(): String = "texel_subset_linear_down"
    override fun getISize(): SkISize = kTexelSubsetSize
    override fun onDraw(canvas: SkCanvas?) {
        TODO("STUB.GR_TEXTURE_EFFECT_MAKE_SUBSET")
    }
}

/**
 * `texel_subset_linear_up` — `Filter::kLinear`, `MipmapMode::kNone`, upscale=true.
 */
public class TexelSubsetLinearNoneUpGM : GM() {
    init { setBGColor(0xFFFFFFFF.toInt()) }
    override fun getName(): String = "texel_subset_linear_up"
    override fun getISize(): SkISize = kTexelSubsetSize
    override fun onDraw(canvas: SkCanvas?) {
        TODO("STUB.GR_TEXTURE_EFFECT_MAKE_SUBSET")
    }
}

/**
 * `texel_subset_nearest_mipmap_nearest_down` — `Filter::kNearest`, `MipmapMode::kNearest`,
 * upscale=false.
 *
 * Note: upscaling with mipmaps is not registered upstream ("It doesn't make sense to have
 * upscaling MIP map").
 */
public class TexelSubsetNearestMipNearestDownGM : GM() {
    init { setBGColor(0xFFFFFFFF.toInt()) }
    override fun getName(): String = "texel_subset_nearest_mipmap_nearest_down"
    override fun getISize(): SkISize = kTexelSubsetSize
    override fun onDraw(canvas: SkCanvas?) {
        TODO("STUB.GR_TEXTURE_EFFECT_MAKE_SUBSET")
    }
}

/**
 * `texel_subset_linear_mipmap_nearest_down` — `Filter::kLinear`, `MipmapMode::kNearest`,
 * upscale=false.
 */
public class TexelSubsetLinearMipNearestDownGM : GM() {
    init { setBGColor(0xFFFFFFFF.toInt()) }
    override fun getName(): String = "texel_subset_linear_mipmap_nearest_down"
    override fun getISize(): SkISize = kTexelSubsetSize
    override fun onDraw(canvas: SkCanvas?) {
        TODO("STUB.GR_TEXTURE_EFFECT_MAKE_SUBSET")
    }
}

/**
 * `texel_subset_nearest_mipmap_linear_down` — `Filter::kNearest`, `MipmapMode::kLinear`,
 * upscale=false.
 */
public class TexelSubsetNearestMipLinearDownGM : GM() {
    init { setBGColor(0xFFFFFFFF.toInt()) }
    override fun getName(): String = "texel_subset_nearest_mipmap_linear_down"
    override fun getISize(): SkISize = kTexelSubsetSize
    override fun onDraw(canvas: SkCanvas?) {
        TODO("STUB.GR_TEXTURE_EFFECT_MAKE_SUBSET")
    }
}

/**
 * `texel_subset_linear_mipmap_linear_down` — `Filter::kLinear`, `MipmapMode::kLinear`,
 * upscale=false.
 */
public class TexelSubsetLinearMipLinearDownGM : GM() {
    init { setBGColor(0xFFFFFFFF.toInt()) }
    override fun getName(): String = "texel_subset_linear_mipmap_linear_down"
    override fun getISize(): SkISize = kTexelSubsetSize
    override fun onDraw(canvas: SkCanvas?) {
        TODO("STUB.GR_TEXTURE_EFFECT_MAKE_SUBSET")
    }
}
