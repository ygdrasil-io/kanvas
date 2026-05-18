package org.skia.gpu.webgpu

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.ConvexPathsGM

/**
 * Cross-test : `ConvexPathsGM` on the GPU backend.
 *
 * 38 convex paths (rect / circle / oval / rrect / cubic / quad / conic
 * / arc / line + many degenerate variants) tiled in a 5-column grid
 * under axis-aligned `scale(2/3) + translate` CTM. Each path is fill-
 * style AA with a pseudo-random opaque colour. Pure convex-single-
 * contour AA fill workout : exercises the curve-flattening + AA
 * polygon shader stack on dozens of shapes in a single GM.
 */
class ConvexPathsWebGpuTest {

    @Test
    fun `ConvexPathsGM renders close to reference PNG on the GPU backend`() {
        runGpuCrossTest(ConvexPathsGM(), floor = 99.80)
    }
}
