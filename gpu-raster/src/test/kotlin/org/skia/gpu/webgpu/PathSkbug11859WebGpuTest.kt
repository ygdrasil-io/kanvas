package org.skia.gpu.webgpu

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.PathSkbug11859GM

/**
 * Cross-test : `PathSkbug11859GM` on the GPU backend.
 *
 * 512 × 512 red AA-filled two-subpath path drawn under `scale(2, 2)` —
 * regression for clipping when coordinates near the bitmap edge (-2)
 * interact with the rasterizer's edge arithmetic. Multi-contour kWinding
 * fill via G3.3b.3a stencil-and-cover.
 */
class PathSkbug11859WebGpuTest {

    @Test
    fun `PathSkbug11859GM renders close to reference PNG on the GPU backend`() {
        runGpuCrossTest(PathSkbug11859GM(), floor = 99.90)
    }
}
