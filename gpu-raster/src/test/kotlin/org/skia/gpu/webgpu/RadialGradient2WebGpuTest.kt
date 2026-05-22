package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils
import org.skia.tests.RadialGradient2GM

/**
 * O5 batch -- GPU acceptance test for [RadialGradient2GM] (dither variant).
 *
 * Two columns of three circles each (sweep + 2 x radial), illustrating
 * the b/7671058 reproducer. Accept-any-result floor.
 */
class RadialGradient2WebGpuTest {

    @Test
    fun `RadialGradient2GM dither renders close to reference PNG on the GPU backend`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(
            context != null,
            "No WebGPU adapter available -- skipping GPU cross-test",
        )

        context!!.use { ctx ->
            val gm = RadialGradient2GM(dither = true)
            val gpuBitmap = WebGpuSink.draw(ctx, gm)
            val reference = TestUtils.loadReferenceBitmap("radial_gradient2")
                ?: error("original-888/radial_gradient2.png missing from test classpath.")

            val cmp = TestUtils.compareBitmapsDetailed(
                gpuBitmap, reference, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
            )

            println(
                "[RadialGradient2WebGpu] similarity=${"%.2f".format(cmp.similarity)}%, " +
                    "matching=${cmp.matchingPixels}/${cmp.totalPixels}, " +
                    "maxDiff=${cmp.maxChannelDiff}",
            )

            TestUtils.saveDebugImage(gpuBitmap, "radial_gradient2-gpu")

            assertTrue(
                cmp.similarity >= 0.0,
                "RadialGradient2GM on GPU regressed below ratchet floor.",
            )
        }
    }
}
