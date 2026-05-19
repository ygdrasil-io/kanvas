package org.skia.gpu.webgpu

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.Bug6987GM

/**
 * Cross-test : `Bug6987GM` on the GPU backend.
 *
 * skbug.com regression repro : a tiny 1-px-scale triangle stroked at
 * `strokeWidth = 0.0001`, then drawn under `scale(50000, 50000)`. Tests
 * `SkStroker.resScale` on extreme CTM scale — without proper res-scale,
 * the triangle's outline flattens to a polygon at low resolution.
 *
 * G3.4.1 stroke coverage : closed line-only triangle through
 * `SkStroker` → multi-contour fill outline (left + reversed-right with
 * miter joins, closed) → routes through AA stencil-and-cover
 * (G3.3b.3a/G3.3b.3b).
 */
class Bug6987WebGpuTest {

    @Test
    fun `Bug6987GM renders close to reference PNG on the GPU backend`() {
        // Extreme CTM scale (50000×) exercising SkStroker.resScale on a
        // sub-µm closed triangle. Score : 99.77 %.
        runGpuCrossTest(Bug6987GM(), floor = 99.72)
    }
}
