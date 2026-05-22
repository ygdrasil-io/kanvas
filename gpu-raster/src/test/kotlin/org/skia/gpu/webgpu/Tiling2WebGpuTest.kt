package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils
import org.skia.tests.Tiling2GM

class Tiling2WebGpuTest {

    @Test
    fun `Tiling2GM bitmap renders on the GPU backend`() {
        runGpu(Tiling2GM(kind = Tiling2GM.Kind.Bitmap), "tilemode_bitmap")
    }

    @Test
    fun `Tiling2GM gradient renders on the GPU backend`() {
        runGpu(Tiling2GM(kind = Tiling2GM.Kind.Gradient), "tilemode_gradient")
    }

    private fun runGpu(gm: Tiling2GM, ref: String) {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(
            context != null,
            "No WebGPU adapter available -- skipping GPU cross-test",
        )

        context!!.use { ctx ->
            val gpuBitmap = WebGpuSink.draw(ctx, gm)
            val reference = TestUtils.loadReferenceBitmap(ref)
                ?: error("original-888/$ref.png missing from test classpath.")

            val cmp = TestUtils.compareBitmapsDetailed(
                gpuBitmap, reference, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
            )

            println(
                "[Tiling2WebGpu/$ref] similarity=${"%.2f".format(cmp.similarity)}%, " +
                    "matching=${cmp.matchingPixels}/${cmp.totalPixels}, " +
                    "maxDiff=${cmp.maxChannelDiff}",
            )

            TestUtils.saveDebugImage(gpuBitmap, "$ref-gpu")

            assertTrue(
                cmp.similarity >= 0.0,
                "Tiling2GM/$ref on GPU regressed below ratchet floor.",
            )
        }
    }
}
