package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils
import org.skia.tests.HairlineSubdivGM

/**
 * Cross-test : `HairlineSubdivGM` on the GPU backend.
 *
 * 512 × 256 canvas, 4 increasingly-large AA hairline (strokeWidth = 1)
 * quadratic Bezier strokes, overlaid at progressively-shifted origins.
 * Each draw exercises a different subdivision count in the Bezier
 * flattener routed through G3.4.3 hairline synthesis + G3.4.1 SkStroker.
 */
class HairlineSubdivWebGpuTest {

    @Test
    fun `HairlineSubdivGM renders close to reference PNG on the GPU backend`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val gm = HairlineSubdivGM()
            val gpuBitmap = WebGpuSink.draw(ctx, gm)
            val reference = TestUtils.loadReferenceBitmap("hairline_subdiv")
                ?: error("original-888/hairline_subdiv.png missing")

            val cmp = TestUtils.compareBitmapsDetailed(
                gpuBitmap, reference, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
            )
            println(
                "[HairlineSubdivWebGpu] similarity=${"%.2f".format(cmp.similarity)}%, " +
                    "matching=${cmp.matchingPixels}/${cmp.totalPixels}, " +
                    "maxDiff=${cmp.maxChannelDiff}",
            )
            TestUtils.saveDebugImage(gpuBitmap, "hairline_subdiv-gpu")
            val floor = 97.45
            assertTrue(
                cmp.similarity >= floor,
                "HairlineSubdivGM regressed below floor : ${cmp.similarity}% < $floor%.",
            )
        }
    }
}
