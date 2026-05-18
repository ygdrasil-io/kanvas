package org.skia.gpu.webgpu

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.PolygonsGM

/**
 * Cross-test : `PolygonsGM` on the GPU backend.
 *
 * 840 x 1140 grid of 8 polygons (triangle, trapezoid, diamond, octagon,
 * 32-edge approximate circle, concave quad, stairs, 5-point star) drawn
 * under 11 rows : 3 joins (miter / round / bevel) x 3 stroke widths
 * (0 hairline / 10 / 40), plus 2 extra rows (strokeAndFill + fill, both
 * at miter + width 20). Each cell consumes one `SkRandom.nextU()` for
 * its colour (alpha 0xA0 for the 40-wide cells). Pure stroke / fill
 * workout across all join types and widths, AA, axis-aligned translate
 * CTM. No shader.
 */
class PolygonsWebGpuTest {

    @Test
    fun `PolygonsGM renders close to reference PNG on the GPU backend`() {
        // Ratchet : observed 98.59 %. Residual drift on AA stroke
        // edges (especially the 0-width hairline rows and the
        // 40-wide strokes with translucent alpha) where the AA
        // coverage convention differs sub-LSB from raster.
        runGpuCrossTest(PolygonsGM(), floor = 98.55)
    }
}
