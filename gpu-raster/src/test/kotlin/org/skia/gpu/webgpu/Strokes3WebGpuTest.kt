package org.skia.gpu.webgpu

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.Strokes3GM

/**
 * O6 cross-test : `Strokes3GM` (`strokes3`, 1500x1500) on the GPU
 * backend. 6 nested-contour generators × 13 stroke widths × 3 renders
 * (stroke 565-quantised blue, AA black skeleton, FillPathWithPaint
 * red overlay). Pure stroker stress on the GPU stroker path.
 */
class Strokes3WebGpuTest {
    @Test
    fun `Strokes3GM renders close to reference PNG on the GPU backend`() {
        runGpuCrossTest(Strokes3GM(), floor = 50.0)
    }
}
