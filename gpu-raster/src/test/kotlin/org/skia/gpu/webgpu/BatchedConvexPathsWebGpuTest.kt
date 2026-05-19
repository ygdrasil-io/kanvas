package org.skia.gpu.webgpu

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.BatchedConvexPathsGM

/**
 * Cross-test : `BatchedConvexPathsGM` on the GPU backend.
 *
 * 10 single-contour convex cubic-Bezier polygons at shrinking scales,
 * translucent SrcOver blend stacking on black background. Exercises :
 *  - G3.3b.1 Bezier cubic flattening (kCubic verbs)
 *  - G3.3b.2a AA polygon coverage (single-contour convex AA paths)
 *  - G2.1 translucent SrcOver
 *  - G6.0/G6.1 colorspace transform
 *
 * Pure feature stack : no multi-contour, no stroke, no shaders, no
 * filters. Lands at 99.94 % similarity (~160 pixels of sub-channel
 * drift on AA edges).
 */
class BatchedConvexPathsWebGpuTest {

    @Test
    fun `BatchedConvexPathsGM renders close to reference PNG on the GPU backend`() {
        runGpuCrossTest(
            BatchedConvexPathsGM(),
            floor = 99.85,
            logTag = "BatchedConvexPathsWebGpu",
        )
    }
}
