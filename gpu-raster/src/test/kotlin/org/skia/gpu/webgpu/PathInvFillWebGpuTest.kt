package org.skia.gpu.webgpu

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.PathInvFillGM

/**
 * Cross-test : `PathInvFillGM` on the GPU backend.
 *
 * 450 x 220, 4 cells = 2 (doclip) x 2 (AA). Each cell draws a circle
 * at (50, 50) r=40 with `kInverseWinding` fill twice — first under an
 * upper-half axis-aligned clipRect, then a lower-half one. With inverse
 * fill the visible area is everywhere outside the circle modulated by
 * the optional inner clip. Exercises convex inverse fill (G3.3b.3b)
 * under nested axis-aligned clipRects, non-AA and AA.
 */
class PathInvFillWebGpuTest {

    @Test
    fun `PathInvFillGM renders close to reference PNG on the GPU backend`() {
        runGpuCrossTest(PathInvFillGM(), floor = 99.45)
    }
}
