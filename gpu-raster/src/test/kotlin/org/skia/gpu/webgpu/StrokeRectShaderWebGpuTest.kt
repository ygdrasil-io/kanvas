package org.skia.gpu.webgpu

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.StrokeRectShaderGM

/**
 * G-suivi (round 18) cross-test : `StrokeRectShaderGM` -- port of
 * upstream `gm/stroke_rect_shader.cpp::stroke_rect_shader`.
 *
 * 690 x 300, AA-off + AA-on rows of 5 stroked rects {bevel / miter /
 * miter-limited-to-bevel (strokeMiter = 0.01) / round / hairline}, each
 * painted with a red->blue linear gradient (kClamp tile mode). Initial
 * `translate(rect.centerX, rect.centerY)` ensures local-shader coords
 * disagree with device coords for the first rect (stresses the inverse-
 * CTM path in the shader sampler).
 *
 * First cross-test combining the stroker (G3.4.1) with paint.shader =
 * SkLinearGradient on a non-rect path : the stroked-rect outline routes
 * through drawPath, where G4.1.2 (linear gradient on AA non-rect path
 * via stencil-and-cover) takes over for the AA-on row, and through the
 * non-AA polygon path for the AA-off row.
 */
class StrokeRectShaderWebGpuTest {

    @Test
    fun `StrokeRectShaderGM renders close to reference PNG on the GPU backend`() {
        // Landing score 91.88 %. Floor set 0.05 % below for scoring
        // drift headroom. Residual drift on AA stroke edge convention
        // and on the sub-1-px hairline gradient sampling.
        runGpuCrossTest(StrokeRectShaderGM(), floor = 91.83)
    }
}
