package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.PathSkbug11859GM

/**
 * Cross-backend test : `PathSkbug11859GM` on raster + GPU.
 *
 * Reduction from skbug 11859 -- a path with a near-degenerate quad
 * segment that previously tripped the AA edge generator. Stresses the
 * polygon AA dispatcher on a non-convex contour with sub-pixel-thin
 * features.
 *
 * Floors : GPU 99.90 % / raster 99.72 % (initial run 99.95 % / 99.77 %).
 */
class PathSkbug11859CrossBackendTest {

    @Test
    fun `PathSkbug11859GM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = PathSkbug11859GM(),
            rasterFloor = 99.72,
            gpuFloor = 99.90,
        )
    }
}
