package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.ClipCubicGM

/**
 * Cross-backend test : `ClipCubicGM` on raster + GPU.
 *
 * Filled cubic path clipped against rects + paths. Exercises the cubic
 * intersection with clip boundaries — divergence between SkCanvas
 * clipping and GPU scissor / coverage clipping is what this catches.
 *
 * Floors mirror the existing per-backend tests :
 *  - raster (`ClipCubicTest`, tol=1) : 90.0 %
 *  - GPU (`ClipCubicWebGpuTest`, tol=8) : 99.27 %
 */
class ClipCubicCrossBackendTest {

    @Test
    fun `ClipCubicGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = ClipCubicGM(),
            rasterFloor = 90.0,
            gpuFloor = 99.27,
            rasterTolerance = 1,
        )
    }
}
