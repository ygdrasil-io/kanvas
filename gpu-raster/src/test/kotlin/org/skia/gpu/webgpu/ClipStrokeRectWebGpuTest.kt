package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils
import org.skia.tests.ClipStrokeRectGM

/**
 * G3.2 cross-test — ClipStrokeRectGM on GPU.
 *
 * Audited as a G2 target GM. Exercises :
 *  - AA stroke with thick width (22) and thin width (2)
 *  - `clipRect(rect, doAntiAlias = true)` on integer-aligned axis-aligned
 *    rects (no rotation in this GM). The expectation is that SkCanvas
 *    doesn't materialise an SkAAClip in that case (the integer clip
 *    rect carries all the information), so the GPU's bindClip
 *    no-op path doesn't throw.
 *
 * If the test fails with `IllegalStateException` from bindClip, the
 * assumption was wrong and an SkAAClip *is* being created ; that would
 * push ClipStrokeRectGM behind the rotated-clip / AA-clip work which
 * is parked behind G3.x.
 */
class ClipStrokeRectWebGpuTest {

    @Test
    fun `ClipStrokeRectGM renders close to reference PNG on the GPU backend`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val gm = ClipStrokeRectGM()
            val gpuBitmap = WebGpuSink.draw(ctx, gm)
            val reference = TestUtils.loadReferenceBitmap("clip_strokerect")
                ?: error("original-888/clip_strokerect.png missing from test classpath")

            val tolerance = TestUtils.TEXTUAL_GM_TOLERANCE
            val cmp = TestUtils.compareBitmapsDetailed(gpuBitmap, reference, tolerance = tolerance)

            println(
                "[ClipStrokeRectWebGpu] similarity=${"%.2f".format(cmp.similarity)}%, " +
                    "matching=${cmp.matchingPixels}/${cmp.totalPixels}, " +
                    "maxDiff=${cmp.maxChannelDiff}",
            )

            TestUtils.saveDebugImage(gpuBitmap, "clip_strokerect-gpu")

            // Floor calibrated by the G3.2 baseline at 96.60% : same drift
            // sources as BigRectGM (AA stroke corner, colorspace) but the
            // GM uses only 4 rect draws (no hairlines, fewer edges), so the
            // aggregate drift is small and the score lands close to the
            // plan G2 90% target. Tightened ratchet starts at 96.0 ; the
            // gap to 100% reflects colorspace drift (G6) + AA stroke
            // corner fractional differences.
            // G6.0 colorspace transform → perfect match.
            val floor = 99.99
            assertTrue(
                cmp.similarity >= floor,
                "ClipStrokeRectGM on GPU regressed below ratchet floor : " +
                    "${cmp.similarity}% < $floor%. See build/debug-images/clip_strokerect-gpu.png.",
            )
        }
    }
}
