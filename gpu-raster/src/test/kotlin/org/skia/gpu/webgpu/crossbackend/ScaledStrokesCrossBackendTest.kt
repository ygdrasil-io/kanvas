package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.ScaledStrokesGM

/**
 * Cross-backend test : `ScaledStrokesGM` on raster + GPU.
 *
 * Grid of stroked primitives (rect, circle, line, path) under
 * varying CTM scales (0.1x .. 10x). Exercises the resScale-aware
 * stroker on the full primitive matrix at multiple scale orders.
 *
 * Floors : GPU 96.44 % / raster 96.36 % (initial run 96.49 % / 96.41 %).
 */
class ScaledStrokesCrossBackendTest {

    @Test
    fun `ScaledStrokesGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = ScaledStrokesGM(),
            rasterFloor = 96.36,
            gpuFloor = 96.44,
        )
    }
}
