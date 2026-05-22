package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.Bug6987GM

/**
 * Cross-backend test : `Bug6987GM` on raster + GPU.
 *
 * Reproduces skbug 6987 -- a stroker-corner regression on closed
 * convex paths under a near-degenerate scale CTM. Pure
 * G3.4.1 stroke-style coverage on closed convex polygons.
 *
 * Floors : GPU 99.72 % / raster 99.51 % (initial run 99.77 % / 99.56 %).
 */
class Bug6987CrossBackendTest {

    @Test
    fun `Bug6987GM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = Bug6987GM(),
            rasterFloor = 99.51,
            gpuFloor = 99.72,
        )
    }
}
