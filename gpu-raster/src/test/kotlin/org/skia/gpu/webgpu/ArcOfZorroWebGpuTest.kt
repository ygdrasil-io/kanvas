package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils
import org.skia.tests.ArcOfZorroGM

/**
 * Cross-test : `ArcOfZorroGM` on the GPU backend.
 *
 * 200 stroked open-arc paths (`drawArc(useCenter = false)`,
 * `strokeWidth = 35`, default `kButt_Cap` + `kMiter_Join`) of slowly
 * increasing sweep angle (134° → 136° in 0.01° steps), randomly
 * coloured, laid out in a boustrophedon pattern on a 1000 × 1000 canvas
 * over a `0xCCCCCC` grey background painted via `drawPaint`.
 *
 * G3.4.1 stroke coverage on cubic-flattened open arcs : each arc is
 * built via `arcTo` + cubic flattening, then stroked into a single
 * closed outline (left + cap + reversed-right + cap) and routed
 * recursively through `drawPath` as an AA single-contour concave fill
 * (G3.3b.3a.2). Heavy stroker throughput — 200 strokes per frame.
 */
class ArcOfZorroWebGpuTest {

    @Test
    fun `ArcOfZorroGM renders close to reference PNG on the GPU backend`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        context!!.use { ctx ->
            val gm = ArcOfZorroGM()
            val gpuBitmap = WebGpuSink.draw(ctx, gm)
            val reference = TestUtils.loadReferenceBitmap("arcofzorro")
                ?: error("original-888/arcofzorro.png missing")

            val cmp = TestUtils.compareBitmapsDetailed(
                gpuBitmap, reference, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
            )
            println(
                "[ArcOfZorroWebGpu] similarity=${"%.2f".format(cmp.similarity)}%, " +
                    "matching=${cmp.matchingPixels}/${cmp.totalPixels}, " +
                    "maxDiff=${cmp.maxChannelDiff}",
            )
            TestUtils.saveDebugImage(gpuBitmap, "arcofzorro-gpu")
            // Heavy stroker throughput on cubic-flattened arcs : 200
            // strokes per frame. Score : 99.73 %.
            val floor = 99.68
            assertTrue(
                cmp.similarity >= floor,
                "ArcOfZorroGM regressed below floor : ${cmp.similarity}% < $floor%.",
            )
        }
    }
}
