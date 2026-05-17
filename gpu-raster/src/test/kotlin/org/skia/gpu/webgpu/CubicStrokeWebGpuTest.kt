package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils
import org.skia.tests.CubicStrokeGM

/**
 * Cross-test : `CubicStrokeGM` on the GPU backend.
 *
 * Three near-identical AA stroked cubic Bezier paths with sub-1 %
 * stroke-width variation (1.0720 / 1.0721 / 1.0722). Each is a single
 * open contour ; `SkStroker` turns each into a single closed outline
 * (left + cap + reversed right + cap). The outline routes through
 * `drawPath` recursively as a fill : open-path outline = single
 * concave contour, so it lands on the AA stencil-and-cover path
 * (G3.3b.3a.2). Validates the G3.4.1 SkStroker integration end-to-end
 * on curve + AA stack.
 */
class CubicStrokeWebGpuTest {

    @Test
    fun `CubicStrokeGM renders close to reference PNG on the GPU backend`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val gm = CubicStrokeGM()
            val gpuBitmap = WebGpuSink.draw(ctx, gm)
            val reference = TestUtils.loadReferenceBitmap("CubicStroke")
                ?: error("original-888/CubicStroke.png missing")

            val cmp = TestUtils.compareBitmapsDetailed(
                gpuBitmap, reference, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
            )
            println(
                "[CubicStrokeWebGpu] similarity=${"%.2f".format(cmp.similarity)}%, " +
                    "matching=${cmp.matchingPixels}/${cmp.totalPixels}, " +
                    "maxDiff=${cmp.maxChannelDiff}",
            )
            TestUtils.saveDebugImage(gpuBitmap, "cubicstroke-gpu")
            val floor = 98.5
            assertTrue(
                cmp.similarity >= floor,
                "CubicStrokeGM regressed below floor : ${cmp.similarity}% < $floor%.",
            )
        }
    }
}
