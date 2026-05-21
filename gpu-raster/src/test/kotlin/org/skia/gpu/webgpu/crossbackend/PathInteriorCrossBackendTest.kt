package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.PathInteriorGM

/**
 * Cross-backend test : `PathInteriorGM` on raster + GPU.
 *
 * Compound path with multiple sub-paths exercising the "interior"
 * regions of winding / even-odd fill. Stresses the polygon AA
 * dispatcher on non-convex shapes with internal holes.
 *
 * Floors : GPU 98.65 % / raster 98.63 % (initial run 98.70 % / 98.68 %).
 */
class PathInteriorCrossBackendTest {

    @Test
    fun `PathInteriorGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = PathInteriorGM(),
            rasterFloor = 98.63,
            gpuFloor = 98.65,
        )
    }
}
