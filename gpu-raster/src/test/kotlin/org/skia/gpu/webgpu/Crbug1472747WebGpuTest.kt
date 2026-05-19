package org.skia.gpu.webgpu

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.Crbug1472747GM

/**
 * Cross-test : `Crbug1472747GM` (`crbug_1472747`) on the GPU backend.
 *
 * 400 x 400 single drawPath : inner + outer ovals at r=31000 / r=31005
 * filled with `kEvenOdd` to produce a thin ring. Each oval is built
 * from two 180-degree `arcTo` half-arcs as Canvas2D would. Tests the
 * even-odd fill rule on multi-contour input with conic-flattened arcs
 * at extreme radius (G3.3b.3b on top of conic flattening from G3.3b.1).
 */
class Crbug1472747WebGpuTest {

    @Test
    fun `Crbug1472747GM renders close to reference PNG on the GPU backend`() {
        runGpuCrossTest(Crbug1472747GM(), floor = 98.10, logTag = "Crbug1472747WebGpu")
    }
}
