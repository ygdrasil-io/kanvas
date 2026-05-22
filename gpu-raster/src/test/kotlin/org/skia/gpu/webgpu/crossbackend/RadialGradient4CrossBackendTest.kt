package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.RadialGradient4GM

/**
 * O5 batch -- cross-backend test for [RadialGradient4GM] (dither variant).
 * 500x500 drawRect with 5-stop radial (red bands) at repeated positions.
 * Accept-any-result.
 */
class RadialGradient4CrossBackendTest {

    @Test
    fun `RadialGradient4GM renders on raster and GPU backends`() {
        runCrossBackendTest(
            gm = RadialGradient4GM(dither = true),
            rasterFloor = 0.0,
            gpuFloor = 0.0,
        )
    }
}
