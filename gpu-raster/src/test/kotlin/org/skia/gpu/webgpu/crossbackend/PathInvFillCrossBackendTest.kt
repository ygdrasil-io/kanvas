package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.PathInvFillGM

/**
 * Cross-backend test : `PathInvFillGM` (`pathinvfill`) on raster + GPU.
 *
 * 450 x 220, 4 cells = 2 (doclip) x 2 (AA). Each cell draws a circle at
 * (50, 50) r=40 with `kInverseWinding` fill twice -- first under an
 * upper-half axis-aligned `clipRect`, then a lower-half one. With inverse
 * fill the visible area is everywhere outside the circle modulated by
 * the optional inner clip. Exercises convex inverse fill (G3.3b.3b)
 * under nested axis-aligned clipRects, non-AA and AA.
 *
 * Floors mirror the existing per-backend tests :
 *  - raster (`Round6Test`, tol=1) : 98.0 % ;
 *  - GPU (`PathInvFillWebGpuTest`, tol=8) : 99.45 %.
 */
class PathInvFillCrossBackendTest {

    @Test
    fun `PathInvFillGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = PathInvFillGM(),
            rasterFloor = 98.0,
            gpuFloor = 99.45,
            rasterTolerance = 1,
        )
    }
}
