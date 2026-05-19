package org.skia.gpu.webgpu

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.PathInteriorGM

/**
 * G-suivi (round 18) cross-test : `PathInteriorGM` -- port of upstream
 * `gm/pathinterior.cpp:PathInteriorGM`.
 *
 * 770 x 770 canvas, 8 x 8 grid of two-contour outer + inner donut
 * paths under all 64 combinations of {insetFirst, doEvenOdd, outerRR,
 * innerRR, outerCW, innerCW}. Each cell is gray-filled (AA) + red-stroke
 * outlined.
 *
 * Exercises :
 *  - Path direction encoding (CW / CCW) on rect + rrect contours ;
 *  - Multi-contour {kWinding, kEvenOdd} fill rule dispatch (G3.3b.3b) ;
 *  - AA stencil-and-cover on the two-contour donut shapes (G3.3b.3a) ;
 *  - The G3.4.x SkStroker on rect + rrect outlines (red overlay).
 *
 * BG is drawn via `drawPaint(0xFFDDDDDD)` (mirrors ArcOfZorroGM's
 * eraseColor / sRGB workaround) so the colorspace transform applies to
 * the grey background too.
 */
class PathInteriorWebGpuTest {

    @Test
    fun `PathInteriorGM renders close to reference PNG on the GPU backend`() {
        // Landing score 98.70 %. Floor set 0.05 % below for scoring
        // drift headroom.
        runGpuCrossTest(PathInteriorGM(), floor = 98.65)
    }
}
