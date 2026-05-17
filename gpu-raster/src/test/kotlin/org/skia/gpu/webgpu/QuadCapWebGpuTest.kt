package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils
import org.skia.tests.QuadCapGM

/**
 * Cross-test : `QuadCapGM` on the GPU backend.
 *
 * Two AA-stroked quadratic Beziers at `strokeWidth = 0` (hairline). The
 * first uses default `kButt_Cap` and is extended by `pi/8` along the
 * tangent at both ends ; the second uses `kRound_Cap` on the original
 * unextended quad. They should reach the same pixel boundary — i.e. a
 * round-cap reaches as far as a butt-cap extended by `pi/8`.
 *
 * G3.4.4 caps coverage : butt vs round cap geometry comparison on hairline
 * strokes — lands on the G3.4.3 `1 / resScale` hairline-synthesis path.
 */
class QuadCapWebGpuTest {

    @Test
    fun `QuadCapGM renders close to reference PNG on the GPU backend`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val gm = QuadCapGM()
            val gpuBitmap = WebGpuSink.draw(ctx, gm)
            val reference = TestUtils.loadReferenceBitmap("quadcap")
                ?: error("original-888/quadcap.png missing")

            val cmp = TestUtils.compareBitmapsDetailed(
                gpuBitmap, reference, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
            )
            println(
                "[QuadCapWebGpu] similarity=${"%.2f".format(cmp.similarity)}%, " +
                    "matching=${cmp.matchingPixels}/${cmp.totalPixels}, " +
                    "maxDiff=${cmp.maxChannelDiff}",
            )
            TestUtils.saveDebugImage(gpuBitmap, "quadcap-gpu")
            // Score : 99.80 %. Hairline strokes, tiny 200 x 200 image,
            // butt+tangent-extension vs round-cap geometry match almost
            // pixel-perfect through the G3.4.3 `1 / resScale` synthesis.
            val floor = 99.75
            assertTrue(
                cmp.similarity >= floor,
                "QuadCapGM regressed below floor : ${cmp.similarity}% < $floor%.",
            )
        }
    }
}
