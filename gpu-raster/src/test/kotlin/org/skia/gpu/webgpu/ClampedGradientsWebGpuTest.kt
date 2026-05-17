package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils
import org.skia.tests.ClampedGradientsGM

/**
 * G4.x cross-test : `ClampedGradientsGM` -- 640 x 510 canvas with
 * a 0xFFDDDDDD background (via drawPaint) and a single drawRect
 * (100 x 300, translated by (20, 20)) filled with a 5-stop kClamp
 * `SkRadialGradient` (red/green/blue/white/black) centred at (0, 300)
 * with radius 200 -- centre is outside the drawn rect so every pixel
 * sees a non-trivial radial distance.
 *
 * Pure radial-on-rect + drawPaint workout, all in-scope on GPU.
 */
class ClampedGradientsWebGpuTest {

    @Test
    fun `ClampedGradientsGM renders close to reference PNG on the GPU backend`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val gm = ClampedGradientsGM()
            val gpuBitmap = WebGpuSink.draw(ctx, gm)
            val reference = TestUtils.loadReferenceBitmap("clamped_gradients")
                ?: error("original-888/clamped_gradients.png missing")

            val cmp = TestUtils.compareBitmapsDetailed(
                gpuBitmap, reference, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
            )
            println(
                "[ClampedGradientsWebGpu] similarity=${"%.2f".format(cmp.similarity)}%, " +
                    "matching=${cmp.matchingPixels}/${cmp.totalPixels}, " +
                    "maxDiff=${cmp.maxChannelDiff}",
            )
            TestUtils.saveDebugImage(gpuBitmap, "clamped_gradients-gpu")
            val floor = 99.95
            assertTrue(
                cmp.similarity >= floor,
                "ClampedGradientsGM regressed below floor : ${cmp.similarity}% < $floor%.",
            )
        }
    }
}
