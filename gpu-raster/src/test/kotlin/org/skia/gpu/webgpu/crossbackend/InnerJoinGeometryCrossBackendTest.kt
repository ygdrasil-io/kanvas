package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.InnerJoinGeometryGM

/**
 * Cross-backend test : `InnerJoinGeometryGM` (`inner_join_geometry`) on
 * raster + GPU.
 *
 * 8 acute-angle line-triangles (4 x 2 grid) stroked at `strokeWidth = 100`
 * with `kMiter_Join` (default), each overlaid with a red 0-width skeleton
 * showing the stroker's emitted outline. Regression repro for
 * `skbug.com/40043052` -- missing inner-join geometry on highly-acute
 * corners.
 *
 * G3.4.4 caps/joins coverage : miter joins on open polyline paths with
 * acute corners, plus the 0-width (hairline) outline overlay that lands
 * on the G3.4.3 hairline-synthesis code path.
 *
 * Floors mirror the existing per-backend tests :
 *  - raster (`Round5Test`, tol=1) : 96.0 % ;
 *  - GPU (`InnerJoinGeometryWebGpuTest`, tol=8) : 98.66 %.
 */
class InnerJoinGeometryCrossBackendTest {

    @Test
    fun `InnerJoinGeometryGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = InnerJoinGeometryGM(),
            rasterFloor = 96.0,
            gpuFloor = 98.66,
            rasterTolerance = 1,
        )
    }
}
