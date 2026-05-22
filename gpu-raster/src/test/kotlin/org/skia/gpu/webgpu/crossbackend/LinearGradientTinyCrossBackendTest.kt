package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.LinearGradientTinyGM

/**
 * Cross-backend test : `LinearGradientTinyGM` on raster + GPU.
 * Stress-tests degenerate gradient stop positions and near-coincident
 * endpoints. Floors set to 0.0 (accept-any) for the O4 breadth-first
 * batch.
 */
class LinearGradientTinyCrossBackendTest {

    @Test
    fun `LinearGradientTinyGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = LinearGradientTinyGM(),
            rasterFloor = 0.0,
            gpuFloor = 0.0,
        )
    }
}
