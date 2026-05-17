package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils
import org.skia.tests.InnerJoinGeometryGM

/**
 * Cross-test : `InnerJoinGeometryGM` on the GPU backend.
 *
 * 8 acute-angle line-triangles (4 x 2 grid) stroked at `strokeWidth=100`
 * with `kMiter_Join` (default), each overlaid with a red 0-width
 * skeleton showing the stroker's emitted outline. Originally a regression
 * repro for `skbug.com/40043052` — missing inner-join geometry on
 * highly-acute corners.
 *
 * G3.4.4 caps/joins coverage : exercises miter joins on open polyline
 * paths with acute corners, plus the 0-width (hairline) outline overlay
 * that lands on the G3.4.3 hairline-synthesis code path.
 */
class InnerJoinGeometryWebGpuTest {

    @Test
    fun `InnerJoinGeometryGM renders close to reference PNG on the GPU backend`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val gm = InnerJoinGeometryGM()
            val gpuBitmap = WebGpuSink.draw(ctx, gm)
            val reference = TestUtils.loadReferenceBitmap("inner_join_geometry")
                ?: error("original-888/inner_join_geometry.png missing")

            val cmp = TestUtils.compareBitmapsDetailed(
                gpuBitmap, reference, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
            )
            println(
                "[InnerJoinGeometryWebGpu] similarity=${"%.2f".format(cmp.similarity)}%, " +
                    "matching=${cmp.matchingPixels}/${cmp.totalPixels}, " +
                    "maxDiff=${cmp.maxChannelDiff}",
            )
            TestUtils.saveDebugImage(gpuBitmap, "inner_join_geometry-gpu")
            // Score : 98.71 %. Drift on the AA boundary of the wide
            // miter-joined outlines and the 0-width red skeleton overlay
            // (hairline-synthesis path).
            val floor = 98.66
            assertTrue(
                cmp.similarity >= floor,
                "InnerJoinGeometryGM regressed below floor : ${cmp.similarity}% < $floor%.",
            )
        }
    }
}
