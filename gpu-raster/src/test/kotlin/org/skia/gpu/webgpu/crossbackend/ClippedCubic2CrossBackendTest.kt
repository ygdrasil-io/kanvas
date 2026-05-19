package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.ClippedCubic2GM

/**
 * Cross-backend test : `ClippedCubic2GM` on raster + GPU.
 *
 * 8 cells in a 4x2 layout : a self-intersecting cubic and its
 * 90-deg-flipped variant (built via `SkPath.makeTransform`) drawn
 * under various clip rectangles to expose the rasterizer's behaviour
 * with curves that exit and re-enter the clip. Each cell paints a
 * stroked frame, a 0-width stroked path outline, and a filled path
 * clipped to the frame.
 *
 * Floors :
 *  - raster (tol=1) : 99.91 %
 *  - GPU (tol=8) : 99.91 %
 */
class ClippedCubic2CrossBackendTest {

    @Test
    fun `ClippedCubic2GM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = ClippedCubic2GM(),
            rasterFloor = 99.91,
            gpuFloor = 99.91,
            rasterTolerance = 1,
        )
    }
}
