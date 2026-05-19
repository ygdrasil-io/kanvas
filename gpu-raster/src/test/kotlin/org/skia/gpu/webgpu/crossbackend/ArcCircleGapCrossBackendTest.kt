package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.ArcCircleGapGM

/**
 * Cross-backend test : `ArcCircleGapGM` on raster + GPU.
 *
 * Stroked circle + stroked tangent-arc, both at huge radius (~1097),
 * exercising the sub-pixel-gap regression case from upstream Skia.
 * Default hairline stroke (`strokeWidth = 0`) — both backends route
 * through the `1 / resScale` synthesis path before their respective
 * stroker, so this catches divergence in the hairline synthesis layer
 * itself.
 *
 * Floors mirror the existing per-backend tests :
 *  - raster (`ArcCircleGapTest`, tol=1) : 90.0 %
 *  - GPU (`ArcCircleGapWebGpuTest`, tol=8) : 99.05 %
 */
class ArcCircleGapCrossBackendTest {

    @Test
    fun `ArcCircleGapGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = ArcCircleGapGM(),
            rasterFloor = 90.0,
            gpuFloor = 99.05,
            rasterTolerance = 1,
        )
    }
}
