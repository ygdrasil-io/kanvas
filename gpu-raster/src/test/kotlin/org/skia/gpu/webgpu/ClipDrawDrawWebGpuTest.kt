package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils
import org.skia.tests.ClipDrawDrawGM

/**
 * Cross-test : `ClipDrawDrawGM` on the GPU backend.
 *
 * Reproduces crbug.com/423834 — `clipRect + drawRect + drawRect` sequences
 * that historically left 1-px remnants when integer-edge rounding diverged
 * between `clipRect` and `drawRect`. Pure axis-aligned, non-AA fill rects
 * with `clipRect` — every operation is on the rect fast-path of
 * `SkWebGpuDevice` (G1.2 scissor + G2.3a non-AA fill). No stroke, no path,
 * no shader.
 */
class ClipDrawDrawWebGpuTest {

    @Test
    fun `ClipDrawDrawGM renders close to reference PNG on the GPU backend`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val gm = ClipDrawDrawGM()
            val gpuBitmap = WebGpuSink.draw(ctx, gm)
            val reference = TestUtils.loadReferenceBitmap("clipdrawdraw")
                ?: error("original-888/clipdrawdraw.png missing from test classpath")

            val cmp = TestUtils.compareBitmapsDetailed(
                gpuBitmap, reference, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
            )
            println(
                "[ClipDrawDrawWebGpu] similarity=${"%.2f".format(cmp.similarity)}%, " +
                    "matching=${cmp.matchingPixels}/${cmp.totalPixels}, " +
                    "maxDiff=${cmp.maxChannelDiff}",
            )
            TestUtils.saveDebugImage(gpuBitmap, "clipdrawdraw-gpu")
            // Pure axis-aligned non-AA fill rects on the rect fast-path,
            // pixel-edge rounding shared with `SkBitmapDevice` → perfect
            // match.
            val floor = 99.99
            assertTrue(
                cmp.similarity >= floor,
                "ClipDrawDrawGM regressed below floor : ${cmp.similarity}% < $floor%.",
            )
        }
    }
}
