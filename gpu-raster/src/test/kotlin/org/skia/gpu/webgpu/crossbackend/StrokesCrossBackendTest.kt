package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.StrokesGM

/**
 * Cross-backend test : `StrokesGM` on raster + GPU.
 *
 * Family of stroked open + closed paths with mixed cap / join styles.
 * A general stroker stress-test ; both backends route through
 * SkStroker -> fill, so the cross-backend pair acts as a regression
 * net for the shared stroker output.
 *
 * Floors mirror the existing per-backend tests :
 *  - raster (`StrokesTest`, tol=1) : 90.0 %
 *  - GPU (`StrokesWebGpuTest`, tol=8) : 94.22 %
 */
class StrokesCrossBackendTest {

    @Test
    fun `StrokesGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = StrokesGM(),
            rasterFloor = 90.0,
            gpuFloor = 94.22,
            rasterTolerance = 1,
        )
    }
}
