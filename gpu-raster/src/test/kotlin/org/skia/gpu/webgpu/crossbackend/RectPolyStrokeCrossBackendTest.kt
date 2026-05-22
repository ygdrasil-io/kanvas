package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.RectPolyStrokeGM

/**
 * Cross-backend test : `RectPolyStrokeGM` (`rect_poly_stroke`) on raster
 * + GPU.
 *
 * 3 (joins) x 4 (rect shapes including degenerate) x 2 (rotations) x 2
 * (procs : drawRect vs drawPath) = 48 cells of stroked rects at
 * `strokeWidth = 20`. Each cell additionally lays a 0-width green
 * hairline overlay to expose any geometry mismatch between the dedicated
 * rect rasterizer (G3.1) and the general path rasterizer (G3.4.1
 * SkStroker).
 *
 * G3.4.4 joins coverage : every join kind (miter / round / bevel) cycled
 * across rotated rect paths -- divergence between the two procs shows up
 * as red/black smear in the rendered cells.
 *
 * Floors mirror the existing per-backend tests :
 *  - raster (`Round5Test`, tol=1) : 96.0 % ;
 *  - GPU (`RectPolyStrokeWebGpuTest`, tol=8) : 98.16 %.
 */
class RectPolyStrokeCrossBackendTest {

    @Test
    fun `RectPolyStrokeGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = RectPolyStrokeGM(),
            rasterFloor = 96.0,
            gpuFloor = 98.16,
            rasterTolerance = 1,
        )
    }
}
