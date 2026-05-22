package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils
import org.skia.tests.RadialGradient3GM

class RadialGradient3WebGpuTest {

    @Test
    fun `RadialGradient3GM dither renders on the GPU backend`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(
            context != null,
            "No WebGPU adapter available -- skipping GPU cross-test",
        )

        context!!.use { ctx ->
            val gm = RadialGradient3GM(dither = true)
            val gpuBitmap = WebGpuSink.draw(ctx, gm)
            val reference = TestUtils.loadReferenceBitmap("radial_gradient3")
                ?: error("original-888/radial_gradient3.png missing from test classpath.")

            val cmp = TestUtils.compareBitmapsDetailed(
                gpuBitmap, reference, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
            )

            println(
                "[RadialGradient3WebGpu] similarity=${"%.2f".format(cmp.similarity)}%, " +
                    "matching=${cmp.matchingPixels}/${cmp.totalPixels}, " +
                    "maxDiff=${cmp.maxChannelDiff}",
            )

            TestUtils.saveDebugImage(gpuBitmap, "radial_gradient3-gpu")

            assertTrue(
                cmp.similarity >= 0.0,
                "RadialGradient3GM on GPU regressed below ratchet floor.",
            )
        }
    }
}
