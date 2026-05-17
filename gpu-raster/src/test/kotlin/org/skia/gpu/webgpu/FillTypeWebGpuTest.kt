package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils
import org.skia.tests.FillTypeGM

/**
 * Cross-test : `FillTypeGM` on the GPU backend.
 *
 * 4x4 grid : two overlapping circles (multi-contour) drawn under each of
 * the four fill types (`kWinding`, `kEvenOdd`, `kInverseWinding`,
 * `kInverseEvenOdd`) at two scales, each in non-AA AND AA passes.
 * Exercises the complete fill-rule matrix introduced by G3.3b.3b :
 *  - `kWinding` / `kEvenOdd` cover the path bbox with stencil compare
 *    `NotEqual` 0 against read-mask `0xFF` / `0x01` respectively.
 *  - `kInverseWinding` / `kInverseEvenOdd` cover the entire viewport
 *    (clipped to the cell) with stencil compare `Equal` 0 against the
 *    same read masks.
 */
class FillTypeWebGpuTest {

    @Test
    fun `FillTypeGM renders close to reference PNG on the GPU backend`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val gm = FillTypeGM()
            val gpuBitmap = WebGpuSink.draw(ctx, gm)
            val reference = TestUtils.loadReferenceBitmap("filltypes")
                ?: error("original-888/filltypes.png missing")

            val cmp = TestUtils.compareBitmapsDetailed(
                gpuBitmap, reference, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
            )
            println(
                "[FillTypeWebGpu] similarity=${"%.2f".format(cmp.similarity)}%, " +
                    "matching=${cmp.matchingPixels}/${cmp.totalPixels}, " +
                    "maxDiff=${cmp.maxChannelDiff}",
            )
            TestUtils.saveDebugImage(gpuBitmap, "filltypes-gpu")
            val floor = 99.5
            assertTrue(
                cmp.similarity >= floor,
                "FillTypeGM regressed below floor : ${cmp.similarity}% < $floor%. " +
                    "See build/debug-images/filltypes-gpu.png.",
            )
        }
    }
}
