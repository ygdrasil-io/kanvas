package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils
import org.skia.tests.HardstopGradientShaderGM

/**
 * G4.x cross-test : `HardstopGradientShaderGM` -- 8 x 3 grid of
 * linear gradients on rect. Columns iterate through SkTileMode
 * (kClamp / kRepeat / kMirror) ; rows iterate through 8 stop
 * configurations (no positions / evenly spaced / hard stops at
 * various positions, 2..5 stops). First multi-tile-mode GM
 * cross-test exercising kRepeat and kMirror on real geometry.
 */
class HardstopGradientsWebGpuTest {

    @Test
    fun `HardstopGradientShaderGM renders close to reference PNG on the GPU backend`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val gm = HardstopGradientShaderGM()
            val gpuBitmap = WebGpuSink.draw(ctx, gm)
            val reference = TestUtils.loadReferenceBitmap("hardstop_gradients")
                ?: error("original-888/hardstop_gradients.png missing")

            val cmp = TestUtils.compareBitmapsDetailed(
                gpuBitmap, reference, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
            )
            println(
                "[HardstopGradientsWebGpu] similarity=${"%.2f".format(cmp.similarity)}%, " +
                    "matching=${cmp.matchingPixels}/${cmp.totalPixels}, " +
                    "maxDiff=${cmp.maxChannelDiff}",
            )
            TestUtils.saveDebugImage(gpuBitmap, "hardstop_gradients-gpu")
            val floor = 99.95
            assertTrue(
                cmp.similarity >= floor,
                "HardstopGradientShaderGM regressed below floor : ${cmp.similarity}% < $floor%.",
            )
        }
    }
}
