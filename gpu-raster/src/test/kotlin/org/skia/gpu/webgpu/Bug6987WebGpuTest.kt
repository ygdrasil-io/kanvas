package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils
import org.skia.tests.Bug6987GM

/**
 * Cross-test : `Bug6987GM` on the GPU backend.
 *
 * skbug.com regression repro : a tiny 1-px-scale triangle stroked at
 * `strokeWidth = 0.0001`, then drawn under `scale(50000, 50000)`. Tests
 * `SkStroker.resScale` on extreme CTM scale — without proper res-scale,
 * the triangle's outline flattens to a polygon at low resolution.
 *
 * G3.4.1 stroke coverage : closed line-only triangle through
 * `SkStroker` → multi-contour fill outline (left + reversed-right with
 * miter joins, closed) → routes through AA stencil-and-cover
 * (G3.3b.3a/G3.3b.3b).
 */
class Bug6987WebGpuTest {

    @Test
    fun `Bug6987GM renders close to reference PNG on the GPU backend`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val gm = Bug6987GM()
            val gpuBitmap = WebGpuSink.draw(ctx, gm)
            val reference = TestUtils.loadReferenceBitmap("bug6987")
                ?: error("original-888/bug6987.png missing")

            val cmp = TestUtils.compareBitmapsDetailed(
                gpuBitmap, reference, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
            )
            println(
                "[Bug6987WebGpu] similarity=${"%.2f".format(cmp.similarity)}%, " +
                    "matching=${cmp.matchingPixels}/${cmp.totalPixels}, " +
                    "maxDiff=${cmp.maxChannelDiff}",
            )
            TestUtils.saveDebugImage(gpuBitmap, "bug6987-gpu")
            // Extreme CTM scale (50000×) exercising SkStroker.resScale on a
            // sub-µm closed triangle. Score : 99.77 %.
            val floor = 99.72
            assertTrue(
                cmp.similarity >= floor,
                "Bug6987GM regressed below floor : ${cmp.similarity}% < $floor%.",
            )
        }
    }
}
