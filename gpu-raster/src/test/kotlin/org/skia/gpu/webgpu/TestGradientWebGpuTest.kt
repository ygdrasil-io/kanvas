package org.skia.gpu.webgpu

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.TestGradientGM

/**
 * G4.x cross-test : `TestGradientGM` -- 800 x 800 mixed-primitive smoke
 * test combining a filled rect with a kClamp blue->yellow linear
 * gradient, a filled RRect (oval), a filled circle, and a stroked
 * round-rect (radii 10, AA, strokeWidth 4). Exercises the gradient
 * partial-sweep lookup alongside drawCircle / drawRRect /
 * drawRoundRect on the same canvas.
 */
class TestGradientWebGpuTest {

    @Test
    fun `TestGradientGM renders close to reference PNG on the GPU backend`() {
        runGpuCrossTest(TestGradientGM(), floor = 99.90)
    }
}
