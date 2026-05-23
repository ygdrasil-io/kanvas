package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

/**
 * Test driver for the seven `gpu_blur_utils` family GMs from
 * `gm/gpu_blur_utils.cpp`.
 *
 * All tests are **`@Disabled("STUB.GPU_GANESH_BLUR_UTILS")`** because every GM
 * unconditionally calls `TODO("STUB.GPU_GANESH_BLUR_UTILS")` in its `onDraw`.
 * The upstream GMs are gated behind `DEF_SIMPLE_GPU_GM_CAN_FAIL` and rely on
 * Ganesh-internal APIs (`GrRecordingContext`, `GrSurfaceProxyView`,
 * `GrBlurUtils::GaussianBlur`, `SurfaceDrawContext`) that have no raster
 * equivalent in `:kanvas-skia`.
 *
 * Re-enable when a Ganesh-compatible render path is implemented.
 */
class GpuBlurUtilsTest {

    @Disabled("STUB.GPU_GANESH_BLUR_UTILS: requires GrRecordingContext / GrBlurUtils::GaussianBlur — Ganesh-only, no raster equivalent")
    @Test
    fun `GpuBlurUtilsGM renders gpu_blur_utils`() {
        val gm = GpuBlurUtilsGM()
        gm.draw(null)
    }

    @Disabled("STUB.GPU_GANESH_BLUR_UTILS: requires GrRecordingContext / GrBlurUtils::GaussianBlur — Ganesh-only, no raster equivalent")
    @Test
    fun `GpuBlurUtilsRefGM renders gpu_blur_utils_ref`() {
        val gm = GpuBlurUtilsRefGM()
        gm.draw(null)
    }

    @Disabled("STUB.GPU_GANESH_BLUR_UTILS: requires GrRecordingContext / GrBlurUtils::GaussianBlur — Ganesh-only, no raster equivalent")
    @Test
    fun `GpuBlurUtilsSubsetRectGM renders gpu_blur_utils_subset_rect`() {
        val gm = GpuBlurUtilsSubsetRectGM()
        gm.draw(null)
    }

    @Disabled("STUB.GPU_GANESH_BLUR_UTILS: requires GrRecordingContext / GrBlurUtils::GaussianBlur — Ganesh-only, no raster equivalent")
    @Test
    fun `GpuBlurUtilsSubsetRefGM renders gpu_blur_utils_subset_ref`() {
        val gm = GpuBlurUtilsSubsetRefGM()
        gm.draw(null)
    }

    @Disabled("STUB.GPU_GANESH_BLUR_UTILS: requires GrRecordingContext / GrBlurUtils::GaussianBlur — Ganesh-only, no raster equivalent")
    @Test
    fun `VeryLargeSigmaGpuBlurGM renders very_large_sigma_gpu_blur`() {
        val gm = VeryLargeSigmaGpuBlurGM()
        gm.draw(null)
    }

    @Disabled("STUB.GPU_GANESH_BLUR_UTILS: requires GrRecordingContext / GrBlurUtils::GaussianBlur — Ganesh-only, no raster equivalent")
    @Test
    fun `VeryLargeSigmaGpuBlurSubsetGM renders very_large_sigma_gpu_blur_subset`() {
        val gm = VeryLargeSigmaGpuBlurSubsetGM()
        gm.draw(null)
    }

    @Disabled("STUB.GPU_GANESH_BLUR_UTILS: requires GrRecordingContext / GrBlurUtils::GaussianBlur — Ganesh-only, no raster equivalent")
    @Test
    fun `VeryLargeSigmaGpuBlurSubsetTransparentBorderGM renders very_large_sigma_gpu_blur_subset_transparent_border`() {
        val gm = VeryLargeSigmaGpuBlurSubsetTransparentBorderGM()
        gm.draw(null)
    }
}
