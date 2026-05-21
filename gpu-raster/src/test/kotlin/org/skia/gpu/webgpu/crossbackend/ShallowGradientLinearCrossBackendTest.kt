package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.ShallowGradientLinearGM

/**
 * Cross-backend test : `ShallowGradientLinearGM` on raster + GPU.
 *
 * 800 x 800 rect filled with a 2-stop linear gradient between near-
 * identical greys (`0xFF555555 -> 0xFF444444`). With dither enabled --
 * our rasterizer never applies dither but the reference dither delta
 * stays sub-LSB on this near-grey ramp, so both backends are byte-exact.
 *
 * Floors : GPU 99.95 % / raster 99.95 % (initial run 100.00 % / 100.00 %).
 */
class ShallowGradientLinearCrossBackendTest {

    @Test
    fun `ShallowGradientLinearGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = ShallowGradientLinearGM(),
            rasterFloor = 99.95,
            gpuFloor = 99.95,
        )
    }
}
