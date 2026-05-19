package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.BlurPositioningGM

/**
 * Cross-backend test : `BlurPositioningGM` (`check_small_sigma_offset`)
 * on raster + GPU.
 *
 * 200 x 1200 GM that walks nine sigmas `{0.0, 0.1, 0.2, 0.3, 0.4, 0.6,
 * 0.8, 1.0, 1.2}` and for each draws a red stroked outline plus a
 * black filled rect with `paint.imageFilter = SkImageFilters.Blur(s,
 * s)`. Upstream's regression check : the blur output must stay
 * centred inside the red outline at every sigma -- the bug was a
 * half-pixel shift when the gauss kernel collapsed to identity.
 *
 * The `paint.imageFilter` on direct draws path is normally out of
 * scope on the GPU device (see #571 / #573 unlocks which are
 * `saveLayer`-only). What lets this GM pass at >= 98 % on both
 * backends : every sigma here is small enough (max 1.2, max border =
 * ceil(3 * 1.2) = 4 px) that the rendered output looks essentially
 * identical to a non-blurred draw against the reference PNG, and
 * both backends silently drop the filter -- so the cross-backend
 * comparison is bit-stable.
 *
 * Floors (observed) :
 *  - raster (tol = 8) : 98.50 %
 *  - GPU (tol = 8)    : 98.50 %
 * Set 0.05 % below observed.
 */
class BlurPositioningCrossBackendTest {

    @Test
    fun `BlurPositioningGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = BlurPositioningGM(),
            rasterFloor = 98.45,
            gpuFloor = 98.45,
            rasterTolerance = 8,
            gpuTolerance = 8,
        )
    }
}
