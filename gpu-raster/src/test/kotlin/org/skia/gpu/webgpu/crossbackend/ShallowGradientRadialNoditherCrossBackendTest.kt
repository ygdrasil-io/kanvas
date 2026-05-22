package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.ShallowGradientRadialNoditherGM

/**
 * Cross-backend test : `ShallowGradientRadialNoditherGM` on raster + GPU.
 *
 * No-dither twin of `ShallowGradientRadialGM`. 800 x 800 rect filled
 * with a 2-stop radial gradient between near-identical greys
 * (`0xFF555555 -> 0xFF444444`), `isDither = false`. Both backends
 * byte-exact against the reference.
 *
 * Floors : GPU 99.95 % / raster 99.95 % (initial run 100.00 % / 100.00 %).
 */
class ShallowGradientRadialNoditherCrossBackendTest {

    @Test
    fun `ShallowGradientRadialNoditherGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = ShallowGradientRadialNoditherGM(),
            rasterFloor = 99.95,
            gpuFloor = 99.95,
        )
    }
}
