package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.DRRectSmallInnerGM

/**
 * Cross-backend test : `DRRectSmallInnerGM` on raster + GPU.
 *
 * 170 x 610, drawDRRect(outer, inner) where the inner rrect shrinks to
 * sub-pixel sizes (1 px -> 0.01 px), tested on/off-centre and oval-vs-
 * circle. 16 cells stacked vertically, 2 columns wide. Regression test
 * for tessellator divide-by-zero on vanishing inner radii. Exercises
 * drawDRRect + AA + sub-pixel inner geometry.
 */
class DRRectSmallInnerCrossBackendTest {

    @Test
    fun `DRRectSmallInnerGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = DRRectSmallInnerGM(),
            rasterFloor = 97.40,
            gpuFloor = 96.92,
        )
    }
}
