package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.ScaledTilingGradientGM

/**
 * O6 cross-backend : `ScaledTilingGradientGM`
 * (`scaled_tilemode_gradient`, 650x610) on raster + GPU.
 */
class ScaledTilingGradientCrossBackendTest {
    @Test
    fun `ScaledTilingGradientGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(gm = ScaledTilingGradientGM(), rasterFloor = 50.0, gpuFloor = 50.0)
    }
}
