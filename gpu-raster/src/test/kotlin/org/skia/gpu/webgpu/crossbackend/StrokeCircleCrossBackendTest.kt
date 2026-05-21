package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.StrokeCircleGM

/**
 * Cross-backend test : `StrokeCircleGM` on raster + GPU.
 *
 * AA stroked circles at varying widths. Companion to `FillCircleGM` ;
 * stresses the SkStroker -> oval-outline path on both backends.
 *
 * Floors mirror the existing per-backend tests :
 *  - raster (`StrokeCircleTest`, tol=1) : 90.0 %
 *  - GPU (`StrokeCircleWebGpuTest`, tol=8) : 91.76 %
 */
class StrokeCircleCrossBackendTest {

    @Test
    fun `StrokeCircleGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = StrokeCircleGM(),
            rasterFloor = 90.0,
            gpuFloor = 91.76,
            rasterTolerance = 1,
        )
    }
}
