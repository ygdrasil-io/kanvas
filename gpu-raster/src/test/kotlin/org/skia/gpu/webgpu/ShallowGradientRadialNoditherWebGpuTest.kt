package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils
import org.skia.tests.ShallowGradientRadialNoditherGM

/**
 * G4.x cross-test : `ShallowGradientRadialNoditherGM` -- 800 x 800
 * single drawRect filled with a kClamp `SkRadialGradient` centred at
 * (400, 400), radius 400, colours `0xFF555555 -> 0xFF444444`. Identical
 * draw body to [ShallowGradientRadialWebGpuTest] -- only the upstream
 * dither flag differs (no-op in our 8-bit pipeline). Reference PNG
 * differs from the dithered variant by the dither pattern only.
 */
class ShallowGradientRadialNoditherWebGpuTest {

    @Test
    fun `ShallowGradientRadialNoditherGM renders close to reference PNG on the GPU backend`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val gm = ShallowGradientRadialNoditherGM()
            val gpuBitmap = WebGpuSink.draw(ctx, gm)
            val reference = TestUtils.loadReferenceBitmap("shallow_gradient_radial_nodither")
                ?: error("original-888/shallow_gradient_radial_nodither.png missing")

            val cmp = TestUtils.compareBitmapsDetailed(
                gpuBitmap, reference, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
            )
            println(
                "[ShallowGradientRadialNoditherWebGpu] similarity=${"%.2f".format(cmp.similarity)}%, " +
                    "matching=${cmp.matchingPixels}/${cmp.totalPixels}, " +
                    "maxDiff=${cmp.maxChannelDiff}",
            )
            TestUtils.saveDebugImage(gpuBitmap, "shallow_gradient_radial_nodither-gpu")
            val floor = 99.95
            assertTrue(
                cmp.similarity >= floor,
                "ShallowGradientRadialNoditherGM regressed below floor : ${cmp.similarity}% < $floor%.",
            )
        }
    }
}
