package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.ThinRoundRectsGM

/**
 * Cross-backend test : `ThinRoundRectsGM` on raster + GPU.
 *
 * Sister of `ThinRectsGM` -- vert / horiz / square thin-rrect matrix
 * at 1/8-pixel translations, but each cell is `drawRRect` instead of
 * `drawRect`. Vert rects use a 4-corner `setRectRadii` ; horiz rects
 * use `setNinePatch` ; squares use a uniform `setRectXY`. BG black.
 *
 * Exercises sub-pixel-precision rrect drawing on small geometry
 * (sub-pixel radii of 1/32 px) -- a worst-case for AA edge coverage
 * across the four rrect corner types.
 *
 * Floors :
 *  - raster (tol=1) : 92.09 %
 *  - GPU (tol=8) : 96.55 %
 */
class ThinRoundRectsCrossBackendTest {

    @Test
    fun `ThinRoundRectsGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = ThinRoundRectsGM(),
            rasterFloor = 92.09,
            gpuFloor = 96.55,
            rasterTolerance = 1,
        )
    }
}
