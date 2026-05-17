package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils
import org.skia.tests.PathSkbug11859GM

/**
 * Cross-test : `PathSkbug11859GM` on the GPU backend.
 *
 * 512 × 512 red AA-filled two-subpath path drawn under `scale(2, 2)` —
 * regression for clipping when coordinates near the bitmap edge (-2)
 * interact with the rasterizer's edge arithmetic. Multi-contour kWinding
 * fill via G3.3b.3a stencil-and-cover.
 */
class PathSkbug11859WebGpuTest {

    @Test
    fun `PathSkbug11859GM renders close to reference PNG on the GPU backend`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val gm = PathSkbug11859GM()
            val gpuBitmap = WebGpuSink.draw(ctx, gm)
            val reference = TestUtils.loadReferenceBitmap("path_skbug_11859")
                ?: error("original-888/path_skbug_11859.png missing")

            val cmp = TestUtils.compareBitmapsDetailed(
                gpuBitmap, reference, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
            )
            println(
                "[PathSkbug11859WebGpu] similarity=${"%.2f".format(cmp.similarity)}%, " +
                    "matching=${cmp.matchingPixels}/${cmp.totalPixels}, " +
                    "maxDiff=${cmp.maxChannelDiff}",
            )
            TestUtils.saveDebugImage(gpuBitmap, "path_skbug_11859-gpu")
            val floor = 99.90
            assertTrue(
                cmp.similarity >= floor,
                "PathSkbug11859GM regressed below floor : ${cmp.similarity}% < $floor%.",
            )
        }
    }
}
