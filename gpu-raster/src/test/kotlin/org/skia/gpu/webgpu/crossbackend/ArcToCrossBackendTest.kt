package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.ArcToGM

/**
 * Cross-backend test : `ArcToGM` on raster + GPU.
 *
 * SVG-style `SkPathBuilder.arcTo(rx, ry, xAxisRotate, ArcSize, sweep,
 * x, y)` in three sections : 8-arc loop (rotation × oval × small-CW
 * / large-CCW), 4-chord coloured permutation (kSmall / kLarge × kCW
 * / kCCW), and zero-length round-cap degenerates. Hits the
 * SVG-endpoint-to-conic conversion + AA stroke + arc flattening on
 * both backends.
 *
 * Floors mirror the existing per-backend tests :
 *  - raster (`ArcToTest`, tol=1) : 90.0 %
 *  - GPU (`ArcToWebGpuTest`, tol=8) : 96.33 %
 */
class ArcToCrossBackendTest {

    @Test
    fun `ArcToGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = ArcToGM(),
            rasterFloor = 90.0,
            gpuFloor = 96.33,
            rasterTolerance = 1,
        )
    }
}
