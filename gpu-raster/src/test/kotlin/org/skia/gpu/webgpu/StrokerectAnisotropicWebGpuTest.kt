package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils
import org.skia.tests.StrokerectAnisotropicGM

/**
 * Cross-test : `StrokerectAnisotropicGM` on the GPU backend.
 *
 * 4 x 2 grid : `{miter, miter-half-pixel, bevel, bevel-half-pixel}` x
 * `{AA, non-AA}`, each cell drawing a `1000x20` rect routed through
 * `drawPath(SkPath.Rect(...))` under anisotropic `scale(0.03, 2)`.
 * Originally `crbug.com/935303` regression repro for anisotropic
 * stroke-rect bugs.
 *
 * G3.4.4 joins coverage : miter and bevel joins under heavy anisotropic
 * CTM (resScale stress) on stroked rects routed via path. Validates that
 * the stroker's join geometry is correct when source and device aspect
 * ratios diverge.
 */
class StrokerectAnisotropicWebGpuTest {

    @Test
    fun `StrokerectAnisotropicGM renders close to reference PNG on the GPU backend`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val gm = StrokerectAnisotropicGM()
            val gpuBitmap = WebGpuSink.draw(ctx, gm)
            val reference = TestUtils.loadReferenceBitmap("strokerect_anisotropic")
                ?: error("original-888/strokerect_anisotropic.png missing")

            val cmp = TestUtils.compareBitmapsDetailed(
                gpuBitmap, reference, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
            )
            println(
                "[StrokerectAnisotropicWebGpu] similarity=${"%.2f".format(cmp.similarity)}%, " +
                    "matching=${cmp.matchingPixels}/${cmp.totalPixels}, " +
                    "maxDiff=${cmp.maxChannelDiff}",
            )
            TestUtils.saveDebugImage(gpuBitmap, "strokerect_anisotropic-gpu")
            // Score : 98.11 %. Drift on the anisotropic stroke edges
            // (heavy aspect-ratio CTM) and on the half-pixel-offset
            // columns where stroke edges land between integer rows.
            val floor = 98.06
            assertTrue(
                cmp.similarity >= floor,
                "StrokerectAnisotropicGM regressed below floor : ${cmp.similarity}% < $floor%.",
            )
        }
    }
}
