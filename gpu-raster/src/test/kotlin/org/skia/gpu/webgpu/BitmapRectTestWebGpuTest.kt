package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils
import org.skia.tests.BitmapRectTestGM

/**
 * G-suivi (round 14) cross-test : `BitmapRectTestGM` -- 320 x 240
 * canvas. Source bitmap is built on a CPU `SkCanvas(SkBitmap)` (filled
 * triangle + stroked outline, 60 x 60) then drawn three times :
 *  1. `drawImage` at (150, 45) -- 1:1 placement,
 *  2. under `scale(0.472560018)` : `drawImageRect` into (100, 100,
 *     228, 228),
 *  3. under `scale(-1, 1)` : `drawImage` at (-310, 45) -- axis-aligned
 *     horizontal flip.
 *
 * All three routings are in-scope after G5.1 :
 *  - axis-aligned CTM (uniform scale or axis-aligned reflection),
 *  - drawImage / drawImageRect (G5.1 + G5.1.1).
 *
 * Tests precision of the drawImageRect fast path -- this is the
 * upstream bitmaprecttest.cpp regression GM for a bug that drew the
 * right column of source pixels twice under scale.
 */
class BitmapRectTestWebGpuTest {

    @Test
    fun `BitmapRectTestGM renders close to reference PNG on the GPU backend`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val gm = BitmapRectTestGM()
            val gpuBitmap = WebGpuSink.draw(ctx, gm)
            val reference = TestUtils.loadReferenceBitmap("bitmaprecttest")
                ?: error("original-888/bitmaprecttest.png missing")

            val cmp = TestUtils.compareBitmapsDetailed(
                gpuBitmap, reference, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
            )
            println(
                "[BitmapRectTestWebGpu] similarity=${"%.2f".format(cmp.similarity)}%, " +
                    "matching=${cmp.matchingPixels}/${cmp.totalPixels}, " +
                    "maxDiff=${cmp.maxChannelDiff}",
            )
            TestUtils.saveDebugImage(gpuBitmap, "bitmaprecttest-gpu")
            // Landing score 98.50 % -- ~1.5 % drift along the 1-px
            // stroked outline of the source bitmap. The CPU-built
            // bitmap's stroked outline lands on half-pixel boundaries
            // and sampling under `scale(0.472560018)` produces a few
            // pixels of edge antialiasing drift vs the upstream
            // raster reference.
            val floor = 98.45
            assertTrue(
                cmp.similarity >= floor,
                "BitmapRectTestGM regressed below floor : ${cmp.similarity}% < $floor%.",
            )
        }
    }
}
