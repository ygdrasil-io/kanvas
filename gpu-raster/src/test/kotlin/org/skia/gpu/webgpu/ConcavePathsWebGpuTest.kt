package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils
import org.skia.tests.ConcavePathsGM

/**
 * Cross-test : `ConcavePathsGM` on the GPU backend.
 *
 * 29 sub-tests of concave / self-intersecting / coincident-edge polygons,
 * all painted with `isAntiAlias = true`. Multi-contour cells
 * (`testHole`, `testFastForward`, `testPartners`) flow through the
 * G3.3b.3a AA stencil-and-cover path (stencil pass for winding count,
 * fragment shader for edge-segment AA falloff). Single-contour concave
 * cells go through the existing AA polygon shader, which uses
 * min-of-signed-perp-distance ; that's wrong for concave shapes so a
 * subset of those cells will mismatch the reference where the concave
 * pocket intersects an edge's extension.
 *
 * Mixed-quality score expected : the multi-contour cells should render
 * close to the reference, the concave-single-contour ones still wrong.
 */
class ConcavePathsWebGpuTest {

    @Test
    fun `ConcavePathsGM renders best-effort on the GPU backend`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val gm = ConcavePathsGM()
            val gpuBitmap = WebGpuSink.draw(ctx, gm)
            val reference = TestUtils.loadReferenceBitmap("concavepaths")
                ?: error("original-888/concavepaths.png missing")

            val cmp = TestUtils.compareBitmapsDetailed(
                gpuBitmap, reference, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
            )
            println(
                "[ConcavePathsWebGpu] similarity=${"%.2f".format(cmp.similarity)}%, " +
                    "matching=${cmp.matchingPixels}/${cmp.totalPixels}, " +
                    "maxDiff=${cmp.maxChannelDiff}",
            )
            TestUtils.saveDebugImage(gpuBitmap, "concavepaths-gpu")
            val floor = 99.25
            assertTrue(
                cmp.similarity >= floor,
                "ConcavePathsGM regressed below floor : ${cmp.similarity}% < $floor%.",
            )
        }
    }
}
