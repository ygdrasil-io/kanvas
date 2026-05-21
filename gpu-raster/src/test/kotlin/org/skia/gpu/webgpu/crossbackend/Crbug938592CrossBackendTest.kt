package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.Crbug938592GM

/**
 * Cross-backend test : `Crbug938592GM` on raster + GPU.
 *
 * 150 x 30 rect filled with a 3-stop hardstop linear gradient
 * (red -> green -> blue with red and green colocated at stop 0.5).
 * Reduced from a Chromium hardstop-gradient banding regression.
 *
 * Floors : GPU 99.75 % / raster 99.75 % (initial run 99.80 % / 99.80 %).
 */
class Crbug938592CrossBackendTest {

    @Test
    fun `Crbug938592GM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = Crbug938592GM(),
            rasterFloor = 99.75,
            gpuFloor = 99.75,
        )
    }
}
