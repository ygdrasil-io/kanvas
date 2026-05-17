package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils
import org.skia.tests.ConvexPathsGM

/**
 * Cross-test : `ConvexPathsGM` on the GPU backend.
 *
 * 38 convex paths (rect / circle / oval / rrect / cubic / quad / conic
 * / arc / line + many degenerate variants) tiled in a 5-column grid
 * under axis-aligned `scale(2/3) + translate` CTM. Each path is fill-
 * style AA with a pseudo-random opaque colour. Pure convex-single-
 * contour AA fill workout : exercises the curve-flattening + AA
 * polygon shader stack on dozens of shapes in a single GM.
 */
class ConvexPathsWebGpuTest {

    @Test
    fun `ConvexPathsGM renders close to reference PNG on the GPU backend`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val gm = ConvexPathsGM()
            val gpuBitmap = WebGpuSink.draw(ctx, gm)
            val reference = TestUtils.loadReferenceBitmap("convexpaths")
                ?: error("original-888/convexpaths.png missing")

            val cmp = TestUtils.compareBitmapsDetailed(
                gpuBitmap, reference, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
            )
            println(
                "[ConvexPathsWebGpu] similarity=${"%.2f".format(cmp.similarity)}%, " +
                    "matching=${cmp.matchingPixels}/${cmp.totalPixels}, " +
                    "maxDiff=${cmp.maxChannelDiff}",
            )
            TestUtils.saveDebugImage(gpuBitmap, "convexpaths-gpu")
            val floor = 99.80
            assertTrue(
                cmp.similarity >= floor,
                "ConvexPathsGM regressed below floor : ${cmp.similarity}% < $floor%.",
            )
        }
    }
}
