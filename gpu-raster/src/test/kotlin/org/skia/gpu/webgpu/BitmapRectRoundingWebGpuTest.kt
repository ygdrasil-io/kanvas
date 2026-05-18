package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils
import org.skia.tests.BitmapRectRoundingGM

/**
 * G-suivi (round 14) cross-test : `BitmapRectRoundingGM` -- 640 x 480
 * canvas, two stacked axis-aligned draws under uniform `scale(0.9, 0.9)`
 * CTM :
 *  1. `drawRect((1, 1, 110, 114))` filled red,
 *  2. `drawImageRect(blueBitmap, src=image, dst=same rect)` covering
 *     the red rect entirely.
 *
 * Precision regression : if `drawImageRect` rounded the bottom edge
 * to a value smaller than the underlying `drawRect`, a 1-pixel red
 * seam would leak. Expected output : all blue inside the rect.
 *
 * All in-scope after G5.1 :
 *  - axis-aligned CTM (uniform scale),
 *  - drawImageRect with kStrict constraint.
 */
class BitmapRectRoundingWebGpuTest {

    @Test
    fun `BitmapRectRoundingGM renders close to reference PNG on the GPU backend`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val gm = BitmapRectRoundingGM()
            val gpuBitmap = WebGpuSink.draw(ctx, gm)
            val reference = TestUtils.loadReferenceBitmap("bitmaprect_rounding")
                ?: error("original-888/bitmaprect_rounding.png missing")

            val cmp = TestUtils.compareBitmapsDetailed(
                gpuBitmap, reference, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
            )
            println(
                "[BitmapRectRoundingWebGpu] similarity=${"%.2f".format(cmp.similarity)}%, " +
                    "matching=${cmp.matchingPixels}/${cmp.totalPixels}, " +
                    "maxDiff=${cmp.maxChannelDiff}",
            )
            TestUtils.saveDebugImage(gpuBitmap, "bitmaprect_rounding-gpu")
            // Landing score 100.00 % -- byte-exact match (uniform
            // axis-aligned scale ; the drawImageRect rounding fix
            // upstream regressed here is honoured byte-for-byte by
            // our pipeline).
            val floor = 99.95
            assertTrue(
                cmp.similarity >= floor,
                "BitmapRectRoundingGM regressed below floor : ${cmp.similarity}% < $floor%.",
            )
        }
    }
}
