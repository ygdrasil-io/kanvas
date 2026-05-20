package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.ClipLargeRectGM

/**
 * Cross-backend test : `ClipLargeRectGM` on raster + GPU.
 *
 * 256 x 256, stress test for clipRect interaction with a giant
 * translate (1e24f). Outer clip narrows to (0, 0, 120, 256), nested
 * translate(1e24, 0) + clear(GREEN). The clipRect must dominate the
 * extreme translate. Final black hairline at x=120 shows the clip
 * boundary. Exercises clip + clear under extreme CTM.
 */
class ClipLargeRectCrossBackendTest {

    @Test
    fun `ClipLargeRectGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = ClipLargeRectGM(),
            rasterFloor = 99.56,
            gpuFloor = 99.17,
        )
    }
}
