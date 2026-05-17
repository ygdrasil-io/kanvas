package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils
import org.skia.tests.ArcToGM

/**
 * Cross-test : `ArcToGM` on the GPU backend.
 *
 * SVG-style `SkPathBuilder.arcTo(rx, ry, xAxisRotate, ArcSize, sweep, x, y)`
 * stressed in three sections : loop section (8 dark-red arcs across two
 * rotations × two oval heights × small-CW/large-CCW), 4-coloured chord
 * permutation (kSmall/kLarge × kCW/kCCW), and zero-length round-cap
 * degenerate arcs. Pure AA stroke + arc flattening workout via G3.4.1
 * SkStroker on cubic-flattened arcs.
 */
class ArcToWebGpuTest {

    @Test
    fun `ArcToGM renders close to reference PNG on the GPU backend`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val gm = ArcToGM()
            val gpuBitmap = WebGpuSink.draw(ctx, gm)
            val reference = TestUtils.loadReferenceBitmap("arcto")
                ?: error("original-888/arcto.png missing")

            val cmp = TestUtils.compareBitmapsDetailed(
                gpuBitmap, reference, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
            )
            println(
                "[ArcToWebGpu] similarity=${"%.2f".format(cmp.similarity)}%, " +
                    "matching=${cmp.matchingPixels}/${cmp.totalPixels}, " +
                    "maxDiff=${cmp.maxChannelDiff}",
            )
            TestUtils.saveDebugImage(gpuBitmap, "arcto-gpu")
            val floor = 96.33
            assertTrue(
                cmp.similarity >= floor,
                "ArcToGM regressed below floor : ${cmp.similarity}% < $floor%.",
            )
        }
    }
}
