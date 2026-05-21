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
 *
 * K9 -- the non-AA linear-gradient gate in [drawPath] used to require
 * `paint.isAntiAlias`, which left the AA-off row painted as solid black
 * (the stroker outline emitted a `StencilCoverPolygonDraw` with
 * `paint.color` = opaque black). Dropping the `isAntiAlias` requirement
 * and reusing the AA stencil-cover linear-gradient pipeline with
 * `edgeCount = 0` (sentinel : sharp stencil-bound fill, no AA falloff --
 * mirror of the G5.2.3 bitmap-shader fix) lifted the score from 91.88 %
 * to 99.54 %.
 */
class StrokeRectShaderWebGpuTest {

    @Test
    fun `StrokeRectShaderGM renders close to reference PNG on the GPU backend`() {
        // Landing score 99.54 % (K9). Floor set 0.05 % below. Residual
        // drift on AA stroke edge convention and on the sub-1-px
        // hairline gradient sampling for the AA-off hairline rect.
        runGpuCrossTest(StrokeRectShaderGM(), floor = 99.49)
    }
}
