package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.AaclipGM

/**
 * Cross-backend test : `AaclipGM` on raster + GPU.
 *
 * Stress test for AA-clipping at sub-pixel offsets. For each of 5
 * horizontal positions the GM offsets the canvas by an additional
 * `0.2 x col` px, then draws three rect tests (square / column /
 * bar). Each test draws a 2-px-wide green border, a red rect filling
 * the target, AA-clipRects to the target, then draws a slightly-
 * larger blue rect over the same area. The expected behaviour is
 * blue inside, green border, no red leakage.
 *
 * Exercises AA-clipRect at fractional sub-pixel offsets across the
 * full grid -- a worst-case for AA rect-clip arithmetic.
 *
 * Floors :
 *  - raster (tol=1) : 96.95 %
 *  - GPU (tol=8) : 98.78 %
 */
class AaclipCrossBackendTest {

    @Test
    fun `AaclipGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = AaclipGM(),
            rasterFloor = 96.95,
            gpuFloor = 98.78,
            rasterTolerance = 1,
        )
    }
}
