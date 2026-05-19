package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.ClipStrokeRectGM

/**
 * Cross-backend test : `ClipStrokeRectGM` on raster + GPU.
 *
 * AA stroke (thick = 22, thin = 2) under `clipRect(rect, doAntiAlias =
 * true)` on integer-aligned axis-aligned rects. Both backends hit
 * 100 % similarity on this GM (raster ratchet 100.0 %, GPU 100.0 %
 * post-G6.0) ; cross-backend tests it as a sanity baseline that the
 * cross-backend harness itself is wired correctly.
 *
 * Floors mirror the existing per-backend tests :
 *  - raster (`ClipStrokeRectTest`, tol=1) : 99.0 %
 *  - GPU (`ClipStrokeRectWebGpuTest`, tol=8) : 99.99 %
 */
class ClipStrokeRectCrossBackendTest {

    @Test
    fun `ClipStrokeRectGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = ClipStrokeRectGM(),
            rasterFloor = 99.0,
            gpuFloor = 99.99,
            rasterTolerance = 1,
        )
    }
}
