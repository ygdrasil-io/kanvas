package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils
import org.skia.tests.FillrectGradientGM

/**
 * G4.x cross-test : `FillrectGradientGM` -- 120 x 540 canvas, 2-column
 * x 9-row grid of 50 x 50 rect cells. Each row is the same stop list
 * rendered once with `SkLinearGradient` (column 0) and once with
 * `SkRadialGradient` (column 1). Stops cover all the corner cases the
 * gradient infrastructure must handle : 2/3-stop endpoints, sub-range,
 * single-stop, disjoint via duplicate position, ignored duplicates,
 * unsorted input. All under kClamp tile mode on rect (in scope).
 */
class FillrectGradientWebGpuTest {

    @Test
    fun `FillrectGradientGM renders close to reference PNG on the GPU backend`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val gm = FillrectGradientGM()
            val gpuBitmap = WebGpuSink.draw(ctx, gm)
            val reference = TestUtils.loadReferenceBitmap("fillrect_gradient")
                ?: error("original-888/fillrect_gradient.png missing")

            val cmp = TestUtils.compareBitmapsDetailed(
                gpuBitmap, reference, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
            )
            println(
                "[FillrectGradientWebGpu] similarity=${"%.2f".format(cmp.similarity)}%, " +
                    "matching=${cmp.matchingPixels}/${cmp.totalPixels}, " +
                    "maxDiff=${cmp.maxChannelDiff}",
            )
            TestUtils.saveDebugImage(gpuBitmap, "fillrect_gradient-gpu")
            val floor = 95.70
            assertTrue(
                cmp.similarity >= floor,
                "FillrectGradientGM regressed below floor : ${cmp.similarity}% < $floor%.",
            )
        }
    }
}
