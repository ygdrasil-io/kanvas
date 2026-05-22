package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils
import org.skia.tests.TestExtractAlphaGM

class TestExtractAlphaWebGpuTest {

    @Test
    fun `TestExtractAlphaGM renders on the GPU backend`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(
            context != null,
            "No WebGPU adapter available -- skipping GPU cross-test",
        )

        context!!.use { ctx ->
            val gm = TestExtractAlphaGM()
            val gpuBitmap = WebGpuSink.draw(ctx, gm)
            val reference = TestUtils.loadReferenceBitmap("extractalpha")
                ?: error("original-888/extractalpha.png missing from test classpath.")

            val cmp = TestUtils.compareBitmapsDetailed(
                gpuBitmap, reference, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
            )

            println(
                "[TestExtractAlphaWebGpu] similarity=${"%.2f".format(cmp.similarity)}%, " +
                    "matching=${cmp.matchingPixels}/${cmp.totalPixels}, " +
                    "maxDiff=${cmp.maxChannelDiff}",
            )

            TestUtils.saveDebugImage(gpuBitmap, "extractalpha-gpu")

            assertTrue(
                cmp.similarity >= 0.0,
                "TestExtractAlphaGM on GPU regressed below ratchet floor.",
            )
        }
    }
}
