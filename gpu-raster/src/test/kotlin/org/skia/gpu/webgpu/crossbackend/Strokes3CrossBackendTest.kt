package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.Strokes3GM

/**
 * O6 cross-backend : `Strokes3GM` (`strokes3`, 1500x1500) on raster
 * + GPU. Pure stroker stress on nested-contour paths under wide
 * stroke widths.
 */
class Strokes3CrossBackendTest {
    @Test
    fun `Strokes3GM matches reference on raster and GPU backends`() {
        runCrossBackendTest(gm = Strokes3GM(), rasterFloor = 50.0, gpuFloor = 50.0)
    }
}
