package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils
import org.skia.tests.ArcCircleGapGM

/**
 * Cross-test : `ArcCircleGapGM` on the GPU backend.
 *
 * Stroked circle + stroked tangent-arc, both at huge radius (~1097),
 * exercising the sub-pixel-gap regression case from upstream Skia.
 * Uses default hairline stroke (`strokeWidth = 0`), so routes through
 * the G3.4.3 `1 / resScale` synthesis path before SkStroker.
 */
class ArcCircleGapWebGpuTest {

    @Test
    fun `ArcCircleGapGM renders close to reference PNG on the GPU backend`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val gm = ArcCircleGapGM()
            val gpuBitmap = WebGpuSink.draw(ctx, gm)
            val reference = TestUtils.loadReferenceBitmap("arccirclegap")
                ?: error("original-888/arccirclegap.png missing")

            val cmp = TestUtils.compareBitmapsDetailed(
                gpuBitmap, reference, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
            )
            println(
                "[ArcCircleGapWebGpu] similarity=${"%.2f".format(cmp.similarity)}%, " +
                    "matching=${cmp.matchingPixels}/${cmp.totalPixels}, " +
                    "maxDiff=${cmp.maxChannelDiff}",
            )
            TestUtils.saveDebugImage(gpuBitmap, "arccirclegap-gpu")
            val floor = 99.05
            assertTrue(
                cmp.similarity >= floor,
                "ArcCircleGapGM regressed below floor : ${cmp.similarity}% < $floor%.",
            )
        }
    }
}
