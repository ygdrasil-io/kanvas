package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils
import org.skia.tests.SmallArcGM

/**
 * Cross-test : `SmallArcGM` on the GPU backend.
 *
 * One red AA stroked cubic Bezier approximating a quarter-arc, drawn at
 * `strokeWidth = 120` under `translate(-400,-400) ; scale(8, 8)`. The
 * upstream-style ¾-arc lands as a thick wedge across the 762 × 762 canvas.
 *
 * G3.4.1 stroke-style coverage : the stroker's `resScale` is exercised at
 * CTM scale 8×. The single open cubic becomes one closed outline that
 * routes recursively through `drawPath` as an AA single-contour concave
 * fill (G3.3b.3a.2).
 */
class SmallArcWebGpuTest {

    @Test
    fun `SmallArcGM renders close to reference PNG on the GPU backend`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val gm = SmallArcGM()
            val gpuBitmap = WebGpuSink.draw(ctx, gm)
            val reference = TestUtils.loadReferenceBitmap("smallarc")
                ?: error("original-888/smallarc.png missing")

            val cmp = TestUtils.compareBitmapsDetailed(
                gpuBitmap, reference, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
            )
            println(
                "[SmallArcWebGpu] similarity=${"%.2f".format(cmp.similarity)}%, " +
                    "matching=${cmp.matchingPixels}/${cmp.totalPixels}, " +
                    "maxDiff=${cmp.maxChannelDiff}",
            )
            TestUtils.saveDebugImage(gpuBitmap, "smallarc-gpu")
            // First stroke-via-cubic GM under CTM 8× exercising
            // SkStroker.resScale. Score : 99.80 %.
            val floor = 99.75
            assertTrue(
                cmp.similarity >= floor,
                "SmallArcGM regressed below floor : ${cmp.similarity}% < $floor%.",
            )
        }
    }
}
