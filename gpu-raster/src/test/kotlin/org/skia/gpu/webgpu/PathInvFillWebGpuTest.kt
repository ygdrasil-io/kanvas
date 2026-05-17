package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils
import org.skia.tests.PathInvFillGM

/**
 * Cross-test : `PathInvFillGM` on the GPU backend.
 *
 * 450 x 220, 4 cells = 2 (doclip) x 2 (AA). Each cell draws a circle
 * at (50, 50) r=40 with `kInverseWinding` fill twice — first under an
 * upper-half axis-aligned clipRect, then a lower-half one. With inverse
 * fill the visible area is everywhere outside the circle modulated by
 * the optional inner clip. Exercises convex inverse fill (G3.3b.3b)
 * under nested axis-aligned clipRects, non-AA and AA.
 */
class PathInvFillWebGpuTest {

    @Test
    fun `PathInvFillGM renders close to reference PNG on the GPU backend`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val gm = PathInvFillGM()
            val gpuBitmap = WebGpuSink.draw(ctx, gm)
            val reference = TestUtils.loadReferenceBitmap("pathinvfill")
                ?: error("original-888/pathinvfill.png missing")

            val cmp = TestUtils.compareBitmapsDetailed(
                gpuBitmap, reference, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
            )
            println(
                "[PathInvFillWebGpu] similarity=${"%.2f".format(cmp.similarity)}%, " +
                    "matching=${cmp.matchingPixels}/${cmp.totalPixels}, " +
                    "maxDiff=${cmp.maxChannelDiff}",
            )
            TestUtils.saveDebugImage(gpuBitmap, "pathinvfill-gpu")
            val floor = 99.45
            assertTrue(
                cmp.similarity >= floor,
                "PathInvFillGM regressed below floor : ${cmp.similarity}% < $floor%. " +
                    "See build/debug-images/pathinvfill-gpu.png.",
            )
        }
    }
}
