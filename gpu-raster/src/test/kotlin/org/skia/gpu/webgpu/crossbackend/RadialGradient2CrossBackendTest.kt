package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.RadialGradient2GM

/**
 * O5 batch -- cross-backend test for [RadialGradient2GM] (dither variant).
 * Two columns of 3 circles each (sweep + 2 x radial). Accept-any-result.
 */
class RadialGradient2CrossBackendTest {

    @Test
    fun `RadialGradient2GM renders on raster and GPU backends`() {
        runCrossBackendTest(
            gm = RadialGradient2GM(dither = true),
            rasterFloor = 0.0,
            gpuFloor = 0.0,
        )
    }
}
