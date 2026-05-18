package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils
import org.skia.tests.PointsGM

/**
 * Cross-test : `PointsGM` on the GPU backend.
 *
 * 99 pseudo-random points rendered four times :
 *  - `kPolygon` red 4-px polyline,
 *  - `kLines` green hairline pairs,
 *  - `kPoints` blue 6-px round-cap stamps,
 *  - `kPoints` white hairline butt-cap dots overlay.
 *
 * Stresses the full point-mode dispatch and confirms `drawPoints`
 * routing through [SkCanvas.drawCircle] / [SkCanvas.drawLine] reaches
 * the WebGPU rasteriser correctly.
 */
class PointsWebGpuTest {

    @Test
    fun `PointsGM renders close to reference PNG on the GPU backend`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val gm = PointsGM()
            val gpuBitmap = WebGpuSink.draw(ctx, gm)
            val reference = TestUtils.loadReferenceBitmap("points")
                ?: error("original-888/points.png missing")

            val cmp = TestUtils.compareBitmapsDetailed(
                gpuBitmap, reference, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
            )
            println(
                "[PointsWebGpu] similarity=${"%.2f".format(cmp.similarity)}%, " +
                    "matching=${cmp.matchingPixels}/${cmp.totalPixels}, " +
                    "maxDiff=${cmp.maxChannelDiff}",
            )
            TestUtils.saveDebugImage(gpuBitmap, "points-gpu")
            // Floor : PointsGM lands at ~99.45 % on the GPU backend
            // (matching the CPU ratchet at 99.44 %), so the floor sits
            // a small margin below to ride out hairline-rounding wobble
            // between runs.
            val floor = 99.0
            assertTrue(
                cmp.similarity >= floor,
                "PointsGM regressed below floor : ${cmp.similarity}% < $floor%.",
            )
        }
    }
}
