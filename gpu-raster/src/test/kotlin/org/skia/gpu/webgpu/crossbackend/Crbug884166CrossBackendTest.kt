package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.Crbug884166GM

/**
 * Cross-backend test : `Crbug884166GM` on raster + GPU.
 *
 * 300 x 300 single `drawPath` -- 8-vertex line-only contour with a
 * near-vertical sliver, AA + default `kWinding`. Reduced from a
 * Chromium polygon-fill regression. Pure G3.3b polygon AA, no shader /
 * image-filter / mask-filter.
 */
class Crbug884166CrossBackendTest {

    @Test
    fun `Crbug884166GM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = Crbug884166GM(),
            rasterFloor = 99.34,
            gpuFloor = 99.71,
        )
    }
}
