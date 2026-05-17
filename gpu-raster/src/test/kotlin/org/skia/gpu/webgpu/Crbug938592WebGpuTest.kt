package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils
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
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val gm = Crbug938592GM()
            val gpuBitmap = WebGpuSink.draw(ctx, gm)
            val reference = TestUtils.loadReferenceBitmap("crbug_938592")
                ?: error("original-888/crbug_938592.png missing")

            val cmp = TestUtils.compareBitmapsDetailed(
                gpuBitmap, reference, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
            )
            println(
                "[Crbug938592WebGpu] similarity=${"%.2f".format(cmp.similarity)}%, " +
                    "matching=${cmp.matchingPixels}/${cmp.totalPixels}, " +
                    "maxDiff=${cmp.maxChannelDiff}",
            )
            TestUtils.saveDebugImage(gpuBitmap, "crbug_938592-gpu")
            val floor = 99.75
            assertTrue(
                cmp.similarity >= floor,
                "Crbug938592GM regressed below floor : ${cmp.similarity}% < $floor%.",
            )
        }
    }
}
