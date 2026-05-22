package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.ShallowAnglePathArcToGM

/**
 * Cross-backend test : `ShallowAnglePathArcToGM` on raster + GPU.
 *
 * arcTo with extremely shallow sweep angles (sub-degree), reduced from
 * a Chromium arc-to subdivision regression. Exercises the conic-to-arc
 * decomposition at the tangent-degeneracy threshold.
 *
 * Floors : GPU 99.83 % / raster 99.83 % (initial run 99.88 % / 99.88 %).
 */
class ShallowAnglePathArcToCrossBackendTest {

    @Test
    fun `ShallowAnglePathArcToGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = ShallowAnglePathArcToGM(),
            rasterFloor = 99.83,
            gpuFloor = 99.83,
        )
    }
}
