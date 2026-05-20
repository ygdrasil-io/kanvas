package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.Crbug640176GM

/**
 * Cross-backend test : `Crbug640176GM` on raster + GPU.
 *
 * 250 x 250 AA-filled line / line / conic path translated to
 * (125, 125). Conic weight `0.965926f` (`cos(15 deg)`). Reduced from a
 * Chromium subdivision regression where the path interior produced a
 * hole near the line -> conic transition. Pure G3.3b conic flatten +
 * polygon AA, no shader / mask filter.
 */
class Crbug640176CrossBackendTest {

    @Test
    fun `Crbug640176GM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = Crbug640176GM(),
            rasterFloor = 99.77,
            gpuFloor = 99.85,
        )
    }
}
