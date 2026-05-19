package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.ConvexPathsGM

/**
 * Cross-backend test : `ConvexPathsGM` on raster + GPU.
 *
 * 38 convex paths (rect / circle / oval / rrect / cubic / quad / conic
 * / arc / line + many degenerate variants) tiled in a 5-column grid
 * under axis-aligned `scale(2/3) + translate` CTM. Each path is fill-
 * style AA with a pseudo-random opaque colour. Pure convex-single-
 * contour AA fill workout across both backends.
 *
 * Floors mirror the existing per-backend tests :
 *  - raster (`ConvexPathsTest`, tol=1) : 95.0 %
 *  - GPU (`ConvexPathsWebGpuTest`, tol=8) : 99.80 %
 */
class ConvexPathsCrossBackendTest {

    @Test
    fun `ConvexPathsGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = ConvexPathsGM(),
            rasterFloor = 95.0,
            gpuFloor = 99.80,
            rasterTolerance = 1,
        )
    }
}
