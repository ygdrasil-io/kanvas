package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.RRectGM

/**
 * Cross-backend test : `RRectGM` on raster + GPU.
 *
 * 820 x 710 canvas, 4 x 4 grid : 4 inset strategies (rows) x 4 rrect
 * starting types (cols : rect / oval / simple / complex per-corner
 * radii). Each cell drawRRects 13 nested rings stepping `d` from -30
 * to +30 in 5-unit increments. AA hairline strokes with per-type
 * colour. Pure G2.x drawRRect workout under axis-aligned CTM.
 *
 * Floors :
 *  - raster (tol=1) : 89.06 %
 *  - GPU (tol=8) : 91.04 %
 */
class RRectCrossBackendTest {

    @Test
    fun `RRectGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = RRectGM(),
            rasterFloor = 89.01,
            gpuFloor = 90.99,
            rasterTolerance = 1,
        )
    }
}
