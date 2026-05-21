package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.CubicStrokeGM

/**
 * Cross-backend test : `CubicStrokeGM` on raster + GPU.
 *
 * Three near-identical AA stroked cubic Bezier paths with sub-1 %
 * stroke-width variation (1.0720 / 1.0721 / 1.0722). Validates the
 * SkStroker integration end-to-end on curve + AA stack ; the closed
 * outline from open-contour stroking should land identically on
 * both backends.
 *
 * Floors mirror the existing per-backend tests :
 *  - raster (`CubicStrokeTest`, tol=1) : 97.0 %
 *  - GPU (`CubicStrokeWebGpuTest`, tol=8) : 98.5 %
 */
class CubicStrokeCrossBackendTest {

    @Test
    fun `CubicStrokeGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = CubicStrokeGM(),
            rasterFloor = 97.0,
            gpuFloor = 98.5,
            rasterTolerance = 1,
        )
    }
}
