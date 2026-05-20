package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.Crbug847759GM

/**
 * Cross-backend test : `Crbug847759GM` on raster + GPU.
 *
 * 500 x 500 AA hairline stroke (strokeWidth = 0, strokeMiter = 1.5)
 * over a 4-cubic squashed-oval-like closed path translated by
 * (-80, -330). Reduced from a Chromium AAHairlinePathRenderer cubic-to-
 * quad conversion bug at the left / right tips. Pure G3.4 stroker on
 * cubic-flatten paths.
 */
class Crbug847759CrossBackendTest {

    @Test
    fun `Crbug847759GM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = Crbug847759GM(),
            rasterFloor = 99.59,
            gpuFloor = 99.59,
        )
    }
}
