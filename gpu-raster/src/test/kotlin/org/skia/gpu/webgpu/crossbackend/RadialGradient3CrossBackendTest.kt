package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.RadialGradient3GM

/**
 * O5 batch -- cross-backend test for [RadialGradient3GM] (dither variant).
 * Single 500x500 drawRect with off-canvas-centre wide-radius radial.
 * Accept-any-result.
 */
class RadialGradient3CrossBackendTest {

    @Test
    fun `RadialGradient3GM renders on raster and GPU backends`() {
        runCrossBackendTest(
            gm = RadialGradient3GM(dither = true),
            rasterFloor = 0.0,
            gpuFloor = 0.0,
        )
    }
}
