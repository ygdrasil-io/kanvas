package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Port of Skia's `gm/gpu_blur_utils.cpp` — 7 GMs exercising Ganesh-internal
 * Gaussian blur utilities:
 *
 * | GM name                                           | Size        |
 * |---------------------------------------------------|-------------|
 * | `gpu_blur_utils`                                  | 765 × 955   |
 * | `gpu_blur_utils_ref`                              | 765 × 955   |
 * | `gpu_blur_utils_subset_rect`                      | 485 × 730   |
 * | `gpu_blur_utils_subset_ref`                       | 485 × 730   |
 * | `very_large_sigma_gpu_blur`                       | 350 × 1030  |
 * | `very_large_sigma_gpu_blur_subset`                | 350 × 1030  |
 * | `very_large_sigma_gpu_blur_subset_transparent_border` | 355 × 1055 |
 *
 * ## Why STUB.GPU_GANESH_BLUR_UTILS
 *
 * All seven GMs are gated behind `DEF_SIMPLE_GPU_GM_CAN_FAIL`, which requires
 * a live `GrRecordingContext`. The rendering pipeline calls:
 *  - `GrBlurUtils::GaussianBlur` — Ganesh-internal multi-pass separable blur
 *    operating on `GrSurfaceProxyView` (internal GPU proxy handles).
 *  - `skgpu::TopDeviceSurfaceDrawContext` — retrieves the `SurfaceDrawContext`
 *    of the backing device; only valid inside a Ganesh GPU render pass.
 *  - `GrTextureEffect::MakeSubset` / `GrBlendFragmentProcessor::Make` —
 *    Ganesh fragment-processor construction APIs unavailable on the CPU/WebGPU
 *    pipeline.
 *
 * None of these has a raster equivalent in `:kanvas-skia`. Per the post-#678
 * protocol, each GM class carries `TODO("STUB.GPU_GANESH_BLUR_UTILS")` so the
 * compile contract is satisfied, and the paired test class is `@Disabled`.
 *
 * Re-enable when a Ganesh-compatible render path is wired in.
 *
 * Upstream: `gm/gpu_blur_utils.cpp` (Google/Skia).
 */

// ---------------------------------------------------------------------------
// Helper constant shared by the two "subset" GMs
// ---------------------------------------------------------------------------
private const val kSubsetW = 485
private const val kSubsetH = 730

// ---------------------------------------------------------------------------
// GM 1 : gpu_blur_utils  (full source, fast path)
// ---------------------------------------------------------------------------

/**
 * `gpu_blur_utils` (765 × 955) — exercises `GrBlurUtils::GaussianBlur` on a
 * 60 × 60 source at various `SkTileMode`s and dst-rect placements.
 * STUB.GPU_GANESH_BLUR_UTILS.
 */
public class GpuBlurUtilsGM : GM() {
    override fun getName(): String = "gpu_blur_utils"
    override fun getISize(): SkISize = SkISize.Make(765, 955)

    override fun onDraw(canvas: SkCanvas?) {
        TODO("STUB.GPU_GANESH_BLUR_UTILS")
    }
}

// ---------------------------------------------------------------------------
// GM 2 : gpu_blur_utils_ref  (full source, slow reference path)
// ---------------------------------------------------------------------------

/**
 * `gpu_blur_utils_ref` (765 × 955) — same layout as [GpuBlurUtilsGM] but
 * uses `slow_blur` (repeated clamp-blurs) as the reference implementation.
 * STUB.GPU_GANESH_BLUR_UTILS.
 */
public class GpuBlurUtilsRefGM : GM() {
    override fun getName(): String = "gpu_blur_utils_ref"
    override fun getISize(): SkISize = SkISize.Make(765, 955)

    override fun onDraw(canvas: SkCanvas?) {
        TODO("STUB.GPU_GANESH_BLUR_UTILS")
    }
}

// ---------------------------------------------------------------------------
// GM 3 : gpu_blur_utils_subset_rect  (subset of source, fast path)
// ---------------------------------------------------------------------------

/**
 * `gpu_blur_utils_subset_rect` (485 × 730) — same as [GpuBlurUtilsGM] but
 * restricts the source rectangle to a central 5/8 × 6/8 subset of the 60 × 60
 * image. STUB.GPU_GANESH_BLUR_UTILS.
 */
public class GpuBlurUtilsSubsetRectGM : GM() {
    override fun getName(): String = "gpu_blur_utils_subset_rect"
    override fun getISize(): SkISize = SkISize.Make(kSubsetW, kSubsetH)

    override fun onDraw(canvas: SkCanvas?) {
        TODO("STUB.GPU_GANESH_BLUR_UTILS")
    }
}

// ---------------------------------------------------------------------------
// GM 4 : gpu_blur_utils_subset_ref  (subset of source, slow reference path)
// ---------------------------------------------------------------------------

/**
 * `gpu_blur_utils_subset_ref` (485 × 730) — combines the subset source rect
 * from [GpuBlurUtilsSubsetRectGM] with the `slow_blur` reference path.
 * STUB.GPU_GANESH_BLUR_UTILS.
 */
public class GpuBlurUtilsSubsetRefGM : GM() {
    override fun getName(): String = "gpu_blur_utils_subset_ref"
    override fun getISize(): SkISize = SkISize.Make(kSubsetW, kSubsetH)

    override fun onDraw(canvas: SkCanvas?) {
        TODO("STUB.GPU_GANESH_BLUR_UTILS")
    }
}

// ---------------------------------------------------------------------------
// GM 5 : very_large_sigma_gpu_blur  (15 × 15 source, sigma up to 80)
// ---------------------------------------------------------------------------

/**
 * `very_large_sigma_gpu_blur` (350 × 1030) — stresses `GrBlurUtils::GaussianBlur`
 * with very large sigma values (0 / 5 / 25 / 80) on a tiny 15 × 15 source,
 * for all three blur-direction combinations and all four tile modes.
 *
 * Because sigma^2 concatenation is O(n) iterations for `slow_blur`, the upstream
 * code keeps `kShowSlowRefImages = false` in production; this GM only uses the
 * fast path. STUB.GPU_GANESH_BLUR_UTILS.
 */
public class VeryLargeSigmaGpuBlurGM : GM() {
    override fun getName(): String = "very_large_sigma_gpu_blur"
    override fun getISize(): SkISize = SkISize.Make(350, 1030)

    override fun onDraw(canvas: SkCanvas?) {
        TODO("STUB.GPU_GANESH_BLUR_UTILS")
    }
}

// ---------------------------------------------------------------------------
// GM 6 : very_large_sigma_gpu_blur_subset  (15 × 15 content in 19 × 19 image)
// ---------------------------------------------------------------------------

/**
 * `very_large_sigma_gpu_blur_subset` (350 × 1030) — like [VeryLargeSigmaGpuBlurGM]
 * but the 15 × 15 content is embedded inside a 19 × 19 image with a 2-pixel border,
 * and the blur src-rect is restricted to the inner `SkIRect::MakeXYWH(2,2,15,15)`.
 * STUB.GPU_GANESH_BLUR_UTILS.
 */
public class VeryLargeSigmaGpuBlurSubsetGM : GM() {
    override fun getName(): String = "very_large_sigma_gpu_blur_subset"
    override fun getISize(): SkISize = SkISize.Make(350, 1030)

    override fun onDraw(canvas: SkCanvas?) {
        TODO("STUB.GPU_GANESH_BLUR_UTILS")
    }
}

// ---------------------------------------------------------------------------
// GM 7 : very_large_sigma_gpu_blur_subset_transparent_border
// ---------------------------------------------------------------------------

/**
 * `very_large_sigma_gpu_blur_subset_transparent_border` (355 × 1055) — extends
 * [VeryLargeSigmaGpuBlurSubsetGM] by expanding the src-rect outward by 1 pixel
 * (`srcB.makeOutset(1, 1)`) so the blur sees the 1-pixel transparent/black
 * border of the source, exercising the border-bleed handling in
 * `GrBlurUtils::GaussianBlur`. STUB.GPU_GANESH_BLUR_UTILS.
 */
public class VeryLargeSigmaGpuBlurSubsetTransparentBorderGM : GM() {
    override fun getName(): String = "very_large_sigma_gpu_blur_subset_transparent_border"
    override fun getISize(): SkISize = SkISize.Make(355, 1055)

    override fun onDraw(canvas: SkCanvas?) {
        TODO("STUB.GPU_GANESH_BLUR_UTILS")
    }
}
