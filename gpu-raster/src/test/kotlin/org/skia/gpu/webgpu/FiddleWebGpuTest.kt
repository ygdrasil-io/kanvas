package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils
import org.skia.tests.FiddleGM

/**
 * Cross-test : `FiddleGM` on the GPU backend.
 *
 * Empty fiddle placeholder — `onDraw` is intentionally a no-op so the
 * GM renders only the default white background. Tiny but meaningful :
 * exercises the device's "no draws ; flush only the background clear"
 * path through `WebGpuSink`, end-to-end including the G6.0/G6.1
 * sRGB → Rec.2020 present-pass transform on a uniform colour. A baseline
 * sanity check that the post-process colour-space pipeline is round-trip
 * exact on pure background.
 */
class FiddleWebGpuTest {

    @Test
    fun `FiddleGM renders close to reference PNG on the GPU backend`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val gm = FiddleGM()
            val gpuBitmap = WebGpuSink.draw(ctx, gm)
            val reference = TestUtils.loadReferenceBitmap("fiddle")
                ?: error("original-888/fiddle.png missing from test classpath")

            val cmp = TestUtils.compareBitmapsDetailed(
                gpuBitmap, reference, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
            )
            println(
                "[FiddleWebGpu] similarity=${"%.2f".format(cmp.similarity)}%, " +
                    "matching=${cmp.matchingPixels}/${cmp.totalPixels}, " +
                    "maxDiff=${cmp.maxChannelDiff}",
            )
            TestUtils.saveDebugImage(gpuBitmap, "fiddle-gpu")
            // Background-only render — every pixel survives the
            // sRGB → Rec.2020 present-pass transform byte-exact, so the
            // floor is at the ceiling.
            val floor = 99.99
            assertTrue(
                cmp.similarity >= floor,
                "FiddleGM regressed below floor : ${cmp.similarity}% < $floor%.",
            )
        }
    }
}
