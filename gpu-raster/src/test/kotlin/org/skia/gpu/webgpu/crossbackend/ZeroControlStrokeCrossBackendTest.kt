package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.ZeroControlStrokeGM

/**
 * Cross-backend test : `ZeroControlStrokeGM` on raster + GPU.
 *
 * 400 x 800 canvas, six stroked curves with degenerate tangents at
 * `t=0` or `t=1` (skbug.com/40035337) -- cubic / quad / conic with
 * one control point coinciding with start or end. All strokes
 * width 40, `kButt_Cap`, red, AA. Stroker output for degenerate-
 * tangent curves is exactly the degenerate-vertex shape that #567
 * unblocked at the device dispatch level.
 *
 * Floors :
 *  - raster (tol=1) : 99.48 %
 *  - GPU (tol=8) : 99.60 %
 */
class ZeroControlStrokeCrossBackendTest {

    @Test
    fun `ZeroControlStrokeGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = ZeroControlStrokeGM(),
            rasterFloor = 99.43,
            gpuFloor = 99.55,
            rasterTolerance = 1,
        )
    }
}
