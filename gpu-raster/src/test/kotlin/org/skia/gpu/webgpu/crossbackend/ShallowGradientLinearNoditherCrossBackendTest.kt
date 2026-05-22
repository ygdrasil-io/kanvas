package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.ShallowGradientLinearNoditherGM

/**
 * Cross-backend test : `ShallowGradientLinearNoditherGM` on raster + GPU.
 *
 * No-dither twin of `ShallowGradientLinearGM`. 800 x 800 rect filled
 * with a 2-stop linear gradient between near-identical greys
 * (`0xFF555555 -> 0xFF444444`). Same SkLinearGradient kClamp pipeline,
 * `isDither = false`. Both backends byte-exact against the reference.
 *
 * Floors : GPU 99.95 % / raster 99.95 % (initial run 100.00 % / 100.00 %).
 */
class ShallowGradientLinearNoditherCrossBackendTest {

    @Test
    fun `ShallowGradientLinearNoditherGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = ShallowGradientLinearNoditherGM(),
            rasterFloor = 99.95,
            gpuFloor = 99.95,
        )
    }
}
