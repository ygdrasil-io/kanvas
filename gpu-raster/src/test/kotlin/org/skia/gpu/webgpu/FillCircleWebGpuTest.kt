package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils
import org.skia.tests.FillCircleGM

/**
 * G4.x cross-test : `FillCircleGM` -- 520 x 520 canvas, spiralling
 * stack of concentric AA ovals under scale(20, 20) + translate(13, 13)
 * CTM. Each oval is filled with a deterministic 565-quantised random
 * colour. Pure axis-aligned AA oval fill workout (no shader, no stroke,
 * no clip).
 */
class FillCircleWebGpuTest {

    @Test
    fun `FillCircleGM renders close to reference PNG on the GPU backend`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val gm = FillCircleGM()
            val gpuBitmap = WebGpuSink.draw(ctx, gm)
            val reference = TestUtils.loadReferenceBitmap("fillcircle")
                ?: error("original-888/fillcircle.png missing")

            val cmp = TestUtils.compareBitmapsDetailed(
                gpuBitmap, reference, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
            )
            println(
                "[FillCircleWebGpu] similarity=${"%.2f".format(cmp.similarity)}%, " +
                    "matching=${cmp.matchingPixels}/${cmp.totalPixels}, " +
                    "maxDiff=${cmp.maxChannelDiff}",
            )
            TestUtils.saveDebugImage(gpuBitmap, "fillcircle-gpu")
            val floor = 98.50
            assertTrue(
                cmp.similarity >= floor,
                "FillCircleGM regressed below floor : ${cmp.similarity}% < $floor%.",
            )
        }
    }
}
