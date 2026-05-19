package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.StrokeRectGM

/**
 * Cross-backend test : `StrokeRectGM` on raster + GPU.
 *
 * Previously blocked by a `wgpu::setVertexBuffer("invalid size")` panic
 * on the zero-extent / `FLT_EPSILON`-thin rect cells (see G-suivi round
 * 17 / 18 deferral notes). The stroker output for these degenerate
 * rects is multi-contour with each contour carrying fewer than 3
 * vertices : `fanTessellateContours` then emits an empty triangle list
 * (the per-contour fan needs >= 3 vertices, and the path-level `n < 3`
 * gate only checks the aggregate). The empty array reached
 * `createBuffer(size = 0u)` + `setVertexBuffer` and crashed the test
 * worker. Fix : filter empty-vertex stencil-and-cover draws out of
 * `pending` at the dispatch loop entry, mirroring upstream Skia's
 * silent-skip semantics on degenerate paths.
 *
 * Floors :
 *  - raster (`StrokeRectTest`, tol=1, floor 93.5 %) : 93.5 %
 *  - GPU (post-fix, tol=8) : 95.91 %
 */
class StrokeRectCrossBackendTest {

    @Test
    fun `StrokeRectGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = StrokeRectGM(),
            rasterFloor = 93.5,
            gpuFloor = 95.91,
            rasterTolerance = 1,
        )
    }
}
