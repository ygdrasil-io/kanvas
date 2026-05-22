package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.ThinStrokedRectsGM

/**
 * Cross-backend test : `ThinStrokedRectsGM` on raster + GPU.
 *
 * Thin stroked rects (widths approaching the AA hairline regime).
 * Cross-validation here is interesting because both backends use
 * slightly different sub-pixel coverage maths near the 1 px stroke
 * boundary ; the floors are loose enough to accommodate that.
 *
 * Floors mirror the existing per-backend tests :
 *  - raster (`ThinStrokedRectsTest`, tol=1) : 80.0 %
 *  - GPU (`ThinStrokedRectsWebGpuTest`, tol=8) : 94.0 %
 */
class ThinStrokedRectsCrossBackendTest {

    @Test
    fun `ThinStrokedRectsGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = ThinStrokedRectsGM(),
            rasterFloor = 80.0,
            gpuFloor = 94.0,
            rasterTolerance = 1,
        )
    }
}
