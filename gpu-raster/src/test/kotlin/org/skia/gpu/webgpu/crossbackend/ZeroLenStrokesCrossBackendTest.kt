package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.ZeroLenStrokesGM

/**
 * O6 cross-backend : `ZeroLenStrokesGM` (`zeroPath`, 400x800) on
 * raster + GPU. Stroker zero-length / degenerate path regression.
 */
class ZeroLenStrokesCrossBackendTest {
    @Test
    fun `ZeroLenStrokesGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(gm = ZeroLenStrokesGM(), rasterFloor = 50.0, gpuFloor = 50.0)
    }
}
