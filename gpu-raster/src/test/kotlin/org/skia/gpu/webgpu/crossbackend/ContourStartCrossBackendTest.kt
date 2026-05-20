package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.ContourStartGM

/**
 * Cross-backend test : `ContourStartGM` on raster + GPU.
 *
 * Newly unlocked by H5 (#583) -- `SkDashPathEffect` on `drawPath`.
 * 1200 x 600 canvas. Five path families (rect, oval, rrect-with-
 * radii, rrect-as-rect, rrect-as-oval), each drawn 16 times (8
 * starting indices x 2 directions CW + CCW) with a long geometric-
 * progression dash pattern that rotates around the contour as the
 * start point shifts. The GM also calls `drawPoints` to mark each
 * path's vertices.
 *
 * Floors : GPU 90.97 % / raster 90.97 % (initial run GPU 91.03 % /
 * raster 91.02 %, ratchet 0.05 % below observed).
 */
class ContourStartCrossBackendTest {

    @Test
    fun `ContourStartGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = ContourStartGM(),
            rasterFloor = 90.97,
            gpuFloor = 90.97,
        )
    }
}
