package org.skia.gpu.webgpu

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.AnalyticAntialiasInverseGM

/**
 * Cross-test : `AnalyticAntialiasInverseGM` on the GPU backend.
 *
 * 800 x 800 single drawn path : a 30-radius circle at (100, 100) flipped
 * to `kInverseWinding` and painted red, AA on. The visible field is the
 * complement of the disc — red everywhere except inside the circle.
 * Exercises the single-contour convex inverse-fill route landed in
 * G3.3b.3b (stencil compare `Equal` 0, full-viewport cover quad).
 */
class AnalyticAntialiasInverseWebGpuTest {

    @Test
    fun `AnalyticAntialiasInverseGM renders close to reference PNG on the GPU backend`() {
        runGpuCrossTest(AnalyticAntialiasInverseGM(), floor = 99.93)
    }
}
