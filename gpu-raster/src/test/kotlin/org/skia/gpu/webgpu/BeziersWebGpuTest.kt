package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils
import org.skia.tests.BeziersGM

/**
 * Cross-test : `BeziersGM` on the GPU backend.
 *
 * Two panes of 10 random AA-stroked Bezier paths each — top uses
 * `moveTo + 2x quadTo`, bottom uses `moveTo + 2x cubicTo`. Stroke
 * widths span ~1-25 px. Exercises the G3.4.1 SkStroker integration on
 * quad + cubic flatten flows, with per-path random opaque colors.
 */
class BeziersWebGpuTest {

    @Test
    fun `BeziersGM renders close to reference PNG on the GPU backend`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val gm = BeziersGM()
            val gpuBitmap = WebGpuSink.draw(ctx, gm)
            val reference = TestUtils.loadReferenceBitmap("beziers")
                ?: error("original-888/beziers.png missing")

            val cmp = TestUtils.compareBitmapsDetailed(
                gpuBitmap, reference, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
            )
            println(
                "[BeziersWebGpu] similarity=${"%.2f".format(cmp.similarity)}%, " +
                    "matching=${cmp.matchingPixels}/${cmp.totalPixels}, " +
                    "maxDiff=${cmp.maxChannelDiff}",
            )
            TestUtils.saveDebugImage(gpuBitmap, "beziers-gpu")
            val floor = 96.9
            assertTrue(
                cmp.similarity >= floor,
                "BeziersGM regressed below floor : ${cmp.similarity}% < $floor%.",
            )
        }
    }
}
