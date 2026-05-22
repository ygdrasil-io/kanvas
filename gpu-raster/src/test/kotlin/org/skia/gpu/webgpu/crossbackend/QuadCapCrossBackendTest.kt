package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.QuadCapGM

/**
 * Cross-backend test : `QuadCapGM` on raster + GPU.
 *
 * Single quad-Bezier path stroked with various stroke caps (butt, round,
 * square). Exercises the stroker on a degree-2 curve under each cap
 * convention. Edge-pixel AA drift at stroke endpoints dominates the floor.
 *
 * Floors : GPU 99.75 % / raster 99.72 % (initial run 99.80 % / 99.77 %).
 */
class QuadCapCrossBackendTest {

    @Test
    fun `QuadCapGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = QuadCapGM(),
            rasterFloor = 99.72,
            gpuFloor = 99.75,
        )
    }
}
