package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils
import org.skia.tests.ConvexLineOnlyPathsFillGM

/**
 * Cross-test : `ConvexLineOnlyPathsFillGM` (`convex-lineonly-paths`)
 * on the GPU backend.
 *
 * 512 x 512, 20 convex line-only polygons (narrow rects, trapezoids,
 * teardrops, n-gons up to 100 sides) each drawn 7 times at scales
 * `{1, 0.75, 0.5, 0.25, 0.1, 0.01, 0.001}` alternating CW / CCW, AA on
 * with alternating black / white fill. Plus three crbug repros at the
 * end. The fill variant uses default `kFill_Style` so every path
 * exercises the AA polygon shader directly.
 */
class ConvexLineOnlyPathsFillWebGpuTest {

    @Test
    fun `ConvexLineOnlyPathsFillGM renders close to reference PNG on the GPU backend`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val gm = ConvexLineOnlyPathsFillGM()
            val gpuBitmap = WebGpuSink.draw(ctx, gm)
            val reference = TestUtils.loadReferenceBitmap("convex-lineonly-paths")
                ?: error("original-888/convex-lineonly-paths.png missing")

            val cmp = TestUtils.compareBitmapsDetailed(
                gpuBitmap, reference, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
            )
            println(
                "[ConvexLineOnlyPathsFillWebGpu] similarity=${"%.2f".format(cmp.similarity)}%, " +
                    "matching=${cmp.matchingPixels}/${cmp.totalPixels}, " +
                    "maxDiff=${cmp.maxChannelDiff}",
            )
            TestUtils.saveDebugImage(gpuBitmap, "convex-lineonly-paths-gpu")
            val floor = 98.70
            assertTrue(
                cmp.similarity >= floor,
                "ConvexLineOnlyPathsFillGM regressed below floor : ${cmp.similarity}% < $floor%. " +
                    "See build/debug-images/convex-lineonly-paths-gpu.png.",
            )
        }
    }
}
