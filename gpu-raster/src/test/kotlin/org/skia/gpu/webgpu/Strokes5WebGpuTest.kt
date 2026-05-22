package org.skia.gpu.webgpu

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.Strokes5GM

/**
 * O6 cross-test : `Strokes5GM` (`zero_control_stroke`, 400x800) on
 * the GPU backend. Six degenerate-tangent curves (cubic/quad/conic
 * with coincident control point) stroked at width 40, butt-cap, red.
 * Tests the stroker's fallback for vanishing tangent vectors.
 */
class Strokes5WebGpuTest {
    @Test
    fun `Strokes5GM renders close to reference PNG on the GPU backend`() {
        runGpuCrossTest(Strokes5GM(), floor = 50.0)
    }
}
