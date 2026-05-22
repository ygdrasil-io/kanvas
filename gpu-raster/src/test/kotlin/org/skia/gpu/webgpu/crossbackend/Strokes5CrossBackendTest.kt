package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.Strokes5GM

/**
 * O6 cross-backend : `Strokes5GM` (`zero_control_stroke`, 400x800)
 * on raster + GPU. Stroker degenerate-tangent regression.
 */
class Strokes5CrossBackendTest {
    @Test
    fun `Strokes5GM matches reference on raster and GPU backends`() {
        runCrossBackendTest(gm = Strokes5GM(), rasterFloor = 50.0, gpuFloor = 50.0)
    }
}
