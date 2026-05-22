package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.SliverPathsGM

/**
 * O6 cross-backend : `SliverPathsGM` (`mandoline`, 560x475) on
 * raster + GPU. AA path rasterization on sliver-cone contours.
 */
class SliverPathsCrossBackendTest {
    @Test
    fun `SliverPathsGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(gm = SliverPathsGM(), rasterFloor = 50.0, gpuFloor = 50.0)
    }
}
