package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.ShallowGradientConicalDitherGM

/**
 * Cross-backend test : `ShallowGradientConicalDitherGM`
 * (`shallow_gradient_conical`) on raster + GPU.
 *
 * 800 x 800 single `drawRect` filled with a kClamp `SkConicalGradient`
 * whose start and end circles share centre (400, 400) -- the kRadial
 * sub-case. Inner radius = 12.5 px (width / 64), outer radius = 400 px
 * (width / 2). Colours `0xFF555555 -> 0xFF444444` (near-identical
 * greys).
 *
 * Dither-on twin of the existing `ShallowGradientConicalNoDitherGM`
 * single-backend tests. Our rasterizer never applies dither, so GPU
 * output is identical to the no-dither sibling -- the dithered upstream
 * reference still matches byte-exact because the dither delta stays
 * sub-LSB on this near-grey ramp.
 *
 * Both backends land effectively byte-exact (raster 99.99 % / GPU
 * 100.00 %). Floors :
 *  - raster (`Round9Test`, tol=1) : 99.95 % ;
 *  - GPU (`ShallowGradientConicalDitherWebGpuTest`, tol=8) : 99.95 %.
 */
class ShallowGradientConicalDitherCrossBackendTest {

    @Test
    fun `ShallowGradientConicalDitherGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = ShallowGradientConicalDitherGM(),
            rasterFloor = 99.95,
            gpuFloor = 99.95,
            rasterTolerance = 1,
        )
    }
}
