package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.AddArcGM

/**
 * Cross-backend test : `AddArcGM` on raster + GPU.
 *
 * Concentric stroked open arcs of 345° sweep, randomly rotated and
 * inset by `strokeWidth + 4` per iteration. Each arc is built via
 * `SkPathBuilder.addArc` (oval + start + sweep), exercising the cubic
 * Bezier arc emitter + SkStroker on open contours. AA, `kStroke_Style`,
 * `strokeWidth = 15`.
 *
 * Floors mirror the existing per-backend tests :
 *  - raster (`AddArcTest`, tol=1) : 90.0 %
 *  - GPU (`AddArcWebGpuTest`, tol=8) : 93.25 %
 */
class AddArcCrossBackendTest {

    @Test
    fun `AddArcGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = AddArcGM(),
            rasterFloor = 90.0,
            gpuFloor = 93.25,
            rasterTolerance = 1,
        )
    }
}
