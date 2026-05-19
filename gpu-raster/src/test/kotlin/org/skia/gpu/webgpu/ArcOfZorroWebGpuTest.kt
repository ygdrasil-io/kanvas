package org.skia.gpu.webgpu

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
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
        // Heavy stroker throughput on cubic-flattened arcs : 200
        // strokes per frame. Score : 99.73 %.
        runGpuCrossTest(ArcOfZorroGM(), floor = 99.68, logTag = "ArcOfZorroWebGpu")
    }
}
