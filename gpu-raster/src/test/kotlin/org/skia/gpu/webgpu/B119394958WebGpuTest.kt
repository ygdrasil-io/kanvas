package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils
import org.skia.tests.B119394958GM

/**
 * Cross-test : `B119394958GM` on the GPU backend.
 *
 * Three layered draws : blue filled circle, green stroked circle,
 * red round-cap stroked arc on `(30, 30, 70, 70)` sweeping 110 deg.
 * First GM in scope to combine `drawArc(useCenter = false)` with
 * `kRound_Cap` -- the round-cap arc endpoints are emitted as half
 * circles by `SkStroker`, exercising the round-cap dispatch on a
 * short-sweep curve through G3.4.1.
 */
class B119394958WebGpuTest {

    @Test
    fun `B119394958GM renders close to reference PNG on the GPU backend`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val gm = B119394958GM()
            val gpuBitmap = WebGpuSink.draw(ctx, gm)
            val reference = TestUtils.loadReferenceBitmap("b_119394958")
                ?: error("original-888/b_119394958.png missing")

            val cmp = TestUtils.compareBitmapsDetailed(
                gpuBitmap, reference, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
            )
            println(
                "[B119394958WebGpu] similarity=${"%.2f".format(cmp.similarity)}%, " +
                    "matching=${cmp.matchingPixels}/${cmp.totalPixels}, " +
                    "maxDiff=${cmp.maxChannelDiff}",
            )
            TestUtils.saveDebugImage(gpuBitmap, "b_119394958-gpu")
            // G6.2 re-ratchet : moving the intermediate render target
            // to RGBA16Float introduced sub-byte precision in the
            // gradient lerp / coverage products, which shifted one
            // edge pixel by 1 LSB and dropped similarity by 0.01 %.
            val floor = 93.74
            assertTrue(
                cmp.similarity >= floor,
                "B119394958GM regressed below floor : ${cmp.similarity}% < $floor%.",
            )
        }
    }
}
