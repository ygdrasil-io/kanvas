package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils
import org.skia.tests.PathArcToSkbug9077GM

/**
 * Cross-test : `PathArcToSkbug9077GM` on the GPU backend.
 *
 * 200 × 200 single AA-stroked path : 3 lineTo + close, then tangent
 * `arcTo(p1, p2, radius=60)` after the close — regression for the
 * implicit moveTo after close emitting a stale starting point.
 * Exercises G3.4.1 SkStroker on a multi-contour cubic-flattened arc.
 */
class PathArcToSkbug9077WebGpuTest {

    @Test
    fun `PathArcToSkbug9077GM renders close to reference PNG on the GPU backend`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val gm = PathArcToSkbug9077GM()
            val gpuBitmap = WebGpuSink.draw(ctx, gm)
            val reference = TestUtils.loadReferenceBitmap("path_arcto_skbug_9077")
                ?: error("original-888/path_arcto_skbug_9077.png missing")

            val cmp = TestUtils.compareBitmapsDetailed(
                gpuBitmap, reference, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
            )
            println(
                "[PathArcToSkbug9077WebGpu] similarity=${"%.2f".format(cmp.similarity)}%, " +
                    "matching=${cmp.matchingPixels}/${cmp.totalPixels}, " +
                    "maxDiff=${cmp.maxChannelDiff}",
            )
            TestUtils.saveDebugImage(gpuBitmap, "path_arcto_skbug_9077-gpu")
            val floor = 98.95
            assertTrue(
                cmp.similarity >= floor,
                "PathArcToSkbug9077GM regressed below floor : ${cmp.similarity}% < $floor%.",
            )
        }
    }
}
