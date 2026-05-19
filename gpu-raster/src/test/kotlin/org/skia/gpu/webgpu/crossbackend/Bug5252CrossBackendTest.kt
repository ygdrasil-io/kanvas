package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.Bug5252GM

/**
 * Cross-backend test : `Bug5252GM` on raster + GPU.
 *
 * 500 x 500 canvas, AA `clipPath(Oval 225 x 200)` containing a 15 x 10
 * grid of `(stroked rect + stroked cubic)` AA draws. Regression test
 * for stroke miscomputation inside an oval clip. Exercises
 * clipPath(oval) + drawPath(stroked rect) + drawPath(stroked cubic)
 * combo -- the 100 % clipPath coverage unlocked by #565 routes
 * every sub-branch (G3.3b stencil-cover, G3.4 stroker) through the
 * clipPath intersection.
 *
 * Floors :
 *  - raster (tol=1) : 92.92 %
 *  - GPU (tol=8) : 98.16 %
 */
class Bug5252CrossBackendTest {

    @Test
    fun `Bug5252GM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = Bug5252GM(),
            rasterFloor = 92.87,
            gpuFloor = 98.11,
            rasterTolerance = 1,
        )
    }
}
