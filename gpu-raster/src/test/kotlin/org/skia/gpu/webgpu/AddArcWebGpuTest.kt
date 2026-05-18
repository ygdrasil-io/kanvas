package org.skia.gpu.webgpu

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.AddArcGM

/**
 * Cross-test : `AddArcGM` on the GPU backend.
 *
 * Concentric stroked open arcs of `345°` sweep, randomly rotated and
 * inset by `strokeWidth + 4` per iteration, until the rect would no
 * longer fit two stroke widths across. Each path is built via
 * `SkPathBuilder.addArc` (oval + start + sweep) — exercising the
 * cubic-Bezier arc emitter end-to-end. Paint : AA, `kStroke_Style`,
 * `strokeWidth = 15`.
 *
 * G3.4.1 stroke coverage on `addArc`-built open paths : each arc is a
 * single open contour of cubic Beziers; `SkStroker` wraps it into one
 * closed outline (left + cap + reversed-right + cap) that routes
 * recursively as an AA single-contour concave fill (G3.3b.3a.2).
 */
class AddArcWebGpuTest {

    @Test
    fun `AddArcGM renders close to reference PNG on the GPU backend`() {
        // Concentric stroked open arcs via SkPathBuilder.addArc.
        // Score : 93.30 %. Drift dominated by AA stroke edges across
        // many overlapping arc strokes — same edge convention drift
        // as observed on CPU raster (91.91 %).
        runGpuCrossTest(AddArcGM(), floor = 93.25)
    }
}
