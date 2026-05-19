package org.skia.gpu.webgpu

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.Crbug938592GM

/**
 * Cross-test : `Crbug938592GM` on the GPU backend.
 *
 * 150x30 rect filled with a 3-stop hardstop linear gradient
 * (blue->red->green at 9/20 and 11/20), mirrored 4 ways via
 * translate + scale(+/-1, +/-1). The scale-by-mirror keeps the CTM
 * `isScaleTranslate` so the G4.1 gradient route still fires. Exercises
 * the path.isRect gate combined with negative-scale axis-aligned CTM
 * and a 3-stop hardstop configuration.
 */
class Crbug938592WebGpuTest {

    @Test
    fun `Crbug938592GM renders close to reference PNG on the GPU backend`() {
        runGpuCrossTest(Crbug938592GM(), floor = 99.75, logTag = "Crbug938592WebGpu")
    }
}
