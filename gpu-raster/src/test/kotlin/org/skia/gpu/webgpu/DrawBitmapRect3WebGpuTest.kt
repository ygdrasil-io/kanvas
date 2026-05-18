package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils
import org.skia.tests.DrawBitmapRect3

/**
 * G-suivi (round 14) cross-test : `DrawBitmapRect3` -- 640 x 480
 * canvas, single `drawImageRect` of a 3 x 3 colour-stamped bitmap
 * with `srcR = (0.5, 0.5, 2.5, 2.5)` into `dstR = (100, 100, 300, 200)`,
 * default sampling (nearest). With nearest sampling the partial src
 * rect produces a 2 x 2 grid of coloured stripes scaled into the
 * 200 x 100 dst rect. Pure drawImageRect routing (G5.1) -- no shader
 * on paint, just the direct image -> rect path.
 *
 * In-scope after G5.1 :
 *  - drawImageRect with kStrict src constraint,
 *  - axis-aligned CTM,
 *  - nearest sampling.
 */
class DrawBitmapRect3WebGpuTest {

    @Test
    fun `DrawBitmapRect3 renders close to reference PNG on the GPU backend`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val gm = DrawBitmapRect3()
            val gpuBitmap = WebGpuSink.draw(ctx, gm)
            val reference = TestUtils.loadReferenceBitmap("3x3bitmaprect")
                ?: error("original-888/3x3bitmaprect.png missing")

            val cmp = TestUtils.compareBitmapsDetailed(
                gpuBitmap, reference, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
            )
            println(
                "[DrawBitmapRect3WebGpu] similarity=${"%.2f".format(cmp.similarity)}%, " +
                    "matching=${cmp.matchingPixels}/${cmp.totalPixels}, " +
                    "maxDiff=${cmp.maxChannelDiff}",
            )
            TestUtils.saveDebugImage(gpuBitmap, "3x3bitmaprect-gpu")
            // Landing score 100.00 % -- byte-exact match (nearest
            // sampling, kStrict constraint on a small 3 x 3 src ;
            // pure drawImageRect fast path).
            val floor = 99.95
            assertTrue(
                cmp.similarity >= floor,
                "DrawBitmapRect3 regressed below floor : ${cmp.similarity}% < $floor%.",
            )
        }
    }
}
