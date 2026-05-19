package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.BlurImageGM

/**
 * Cross-backend test : `BlurImageGM` (`blur_image`) on raster + GPU.
 *
 * 500 x 500 GM that draws `mandrill_128.png` twice with a `kNormal`
 * sigma = 4 [SkBlurMaskFilter] on the paint -- once at the canonical
 * position, once after a `canvas.scale(1.01, 1.01)`. Original
 * regression check for the sprite-blitter `drawImage` path that
 * silently dropped the mask filter on the unscaled call ; both calls
 * should now produce the same halo.
 *
 * Exercises the bitmap-shader pipeline (G5.x) composed with the
 * #570 / #575 `SkBlurMaskFilter(kNormal)` unlock on `drawImage`.
 *
 * Floors (observed) :
 *  - raster (tol = 8) : 94.76 %
 *  - GPU (tol = 8)    : 94.76 %
 * Set 0.05 % below observed. Cross-backend matching-pixel count is
 * within 8 pixels out of 250000 -- bit-stable across raster and GPU.
 */
class BlurImageCrossBackendTest {

    @Test
    fun `BlurImageGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = BlurImageGM(),
            rasterFloor = 94.71,
            gpuFloor = 94.71,
            rasterTolerance = 8,
            gpuTolerance = 8,
        )
    }
}
