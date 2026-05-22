package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.PathFillGM

/**
 * Cross-backend test : `PathFillGM` on raster + GPU.
 * 10 stacked path-fill shapes + 3 pictogram paths at various scales.
 */
class PathFillCrossBackendTest {

    @Test
    fun `PathFillGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = PathFillGM(),
            rasterFloor = 0.0,
            gpuFloor = 0.0,
        )
    }
}
