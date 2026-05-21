package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.ShallowGradientRadialGM

/**
 * Cross-backend test : `ShallowGradientRadialGM` on raster + GPU.
 *
 * 800 x 800 rect filled with a 2-stop radial gradient between near-
 * identical greys (`0xFF555555 -> 0xFF444444`) centred at (400, 400)
 * with radius 400. With dither enabled -- our rasterizer doesn't apply
 * dither, but the dither delta stays sub-LSB on this near-grey ramp,
 * so both backends are byte-exact against the dithered upstream reference.
 *
 * Floors : GPU 99.95 % / raster 99.95 % (initial run 100.00 % / 100.00 %).
 */
class ShallowGradientRadialCrossBackendTest {

    @Test
    fun `ShallowGradientRadialGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = ShallowGradientRadialGM(),
            rasterFloor = 99.95,
            gpuFloor = 99.95,
        )
    }
}
