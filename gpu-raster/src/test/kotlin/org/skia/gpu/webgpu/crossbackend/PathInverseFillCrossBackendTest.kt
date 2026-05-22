package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.PathInverseFillGM

/**
 * Cross-backend test : `PathInverseFillGM` on raster + GPU.
 * Inverse-filled circle stress test with axis-aligned clip slices.
 */
class PathInverseFillCrossBackendTest {

    @Test
    fun `PathInverseFillGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = PathInverseFillGM(),
            rasterFloor = 0.0,
            gpuFloor = 0.0,
        )
    }
}
