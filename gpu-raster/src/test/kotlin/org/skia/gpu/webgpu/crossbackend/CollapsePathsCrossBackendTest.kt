package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.CollapsePathsGM

/**
 * Cross-backend test : `CollapsePathsGM` on raster + GPU.
 *
 * 500 x 600 canvas, 10 hand-crafted near-collapse paths -- thin
 * almost-degenerate triangles with cubic-precision coordinates that
 * previously caused the edge-flattener to drop edges entirely.
 * Filled (default `kEvenOdd` or `kWinding`) AA. Exercises the
 * degenerate-vertex filter (#567) on stencil-and-cover AA fill
 * (multi-contour and single-contour paths with near-collinear edges).
 *
 * Floors :
 *  - raster (tol=1) : 98.61 %
 *  - GPU (tol=8) : 99.39 %
 */
class CollapsePathsCrossBackendTest {

    @Test
    fun `CollapsePathsGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = CollapsePathsGM(),
            rasterFloor = 98.56,
            gpuFloor = 99.34,
            rasterTolerance = 1,
        )
    }
}
