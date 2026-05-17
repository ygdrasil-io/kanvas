package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils
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
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val gm = TestGradientGM()
            val gpuBitmap = WebGpuSink.draw(ctx, gm)
            val reference = TestUtils.loadReferenceBitmap("testgradient")
                ?: error("original-888/testgradient.png missing")

            val cmp = TestUtils.compareBitmapsDetailed(
                gpuBitmap, reference, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
            )
            println(
                "[TestGradientWebGpu] similarity=${"%.2f".format(cmp.similarity)}%, " +
                    "matching=${cmp.matchingPixels}/${cmp.totalPixels}, " +
                    "maxDiff=${cmp.maxChannelDiff}",
            )
            TestUtils.saveDebugImage(gpuBitmap, "testgradient-gpu")
            val floor = 99.90
            assertTrue(
                cmp.similarity >= floor,
                "TestGradientGM regressed below floor : ${cmp.similarity}% < $floor%.",
            )
        }
    }
}
