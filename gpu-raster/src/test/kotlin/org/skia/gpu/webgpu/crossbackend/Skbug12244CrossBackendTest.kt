package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.Skbug12244GM

/**
 * Cross-backend test : `Skbug12244GM` on raster + GPU.
 *
 * Single `drawPath` with a **multi-contour** line-only path, default
 * `isAntiAlias = false`, default fill style. Stresses winding-count
 * fill rule on the inner-contour hole. The G3.3b.2b stencil-and-cover
 * landed the hole correctly ; remaining drift is sub-pixel AA edge
 * convention (the reference is rasterised with AA, our GPU is binary
 * non-AA, raster is binary non-AA — so the GPU and raster *should*
 * agree closely with each other, even where both differ from the
 * reference). A useful cross-backend case where the "two backends
 * agree with each other better than either agrees with the reference"
 * invariant is testable.
 *
 * Floors mirror the existing per-backend tests :
 *  - raster (`Skbug12244Test`, tol=1) : 90.0 %
 *  - GPU (`Skbug12244WebGpuTest`, tol=8) : 99.0 %
 */
class Skbug12244CrossBackendTest {

    @Test
    fun `Skbug12244GM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = Skbug12244GM(),
            rasterFloor = 90.0,
            gpuFloor = 99.0,
            rasterTolerance = 1,
        )
    }
}
