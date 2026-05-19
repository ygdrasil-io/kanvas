package org.skia.gpu.webgpu

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.Skbug12244GM

/**
 * Cross-test : `Skbug12244GM` on the GPU backend.
 *
 * Single `drawPath` call with a **multi-contour** line-only path
 * (two closed contours), `isAntiAlias` left at the default
 * `false`, default fill style. Pre-G3.3b.2, the SkWebGpuDevice
 * fan tessellator flattens both contours into a single vertex
 * list, so the inner contour's "hole" is filled rather than
 * excluded — score will be lower than the raster output.
 *
 * This test exists to capture that exact baseline : we know the
 * output is geometrically wrong (no hole), and the ratchet records
 * the imperfect score. Once G3.3b.2 lands proper multi-contour
 * tessellation, the score should jump significantly.
 */
class Skbug12244WebGpuTest {

    @Test
    fun `Skbug12244GM renders best-effort on the GPU backend (multi-contour)`() {
        // Floor calibrated by the baseline 70.87%. Multi-contour
        // path = fan tessellation fills the "hole" between outer and
        // inner contours (geometrically wrong but ~70% of the
        // bitmap is unchanged background, so the score isn't
        // catastrophic). G3.3b.2 proper multi-contour tessellation
        // should bump this significantly.
        // G3.3b.2b stencil-and-cover → 99.29 %. Hole correctly excluded
        // via winding count. Remaining ~160 edge pixels differ because
        // the reference is rasterized with AA but our path fill is
        // non-AA (binary coverage). AA multi-contour = G3.3b.3.
        runGpuCrossTest(Skbug12244GM(), floor = 99.0)
    }
}
