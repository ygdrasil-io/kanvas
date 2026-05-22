package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.StrokeRectShaderGM

/**
 * Cross-backend test : `StrokeRectShaderGM` on raster + GPU.
 *
 * 690 x 300, AA-off + AA-on rows of 5 stroked rects {bevel / miter /
 * miter-limited-to-bevel (strokeMiter = 0.01) / round / hairline},
 * each painted with a red->blue linear gradient (kClamp tile mode).
 * Initial `translate(rect.centerX, rect.centerY)` ensures local-
 * shader coords disagree with device coords for the first rect
 * (stresses the inverse-CTM path in the shader sampler).
 *
 * First cross-test combining the stroker (G3.4.1) with `paint.shader
 * = SkLinearGradient` on a non-rect path : the stroked-rect outline
 * routes through drawPath, where G4.1.2 (linear gradient on AA non-
 * rect path via stencil-and-cover) takes over for the AA-on row, and
 * through the non-AA polygon path for the AA-off row.
 *
 * Unlocked by K9 (#611) -- the non-AA linear-gradient gate in
 * `drawPath` no longer requires `paint.isAntiAlias`, so the AA-off
 * row paints through the gradient sampler instead of being filled
 * solid black. GPU similarity jumped from 91.88 % to 99.54 %.
 *
 * Floors mirror the existing per-backend tests :
 *  - raster (`StrokeRectShaderTest`, tol=1) : 80.0 % ;
 *  - GPU (`StrokeRectShaderWebGpuTest`, tol=8) : 99.49 % (K9-lifted ;
 *    residual drift on AA stroke edge convention and on the sub-1-px
 *    hairline gradient sampling for the AA-off hairline rect).
 */
class StrokeRectShaderCrossBackendTest {

    @Test
    fun `StrokeRectShaderGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = StrokeRectShaderGM(),
            rasterFloor = 80.0,
            gpuFloor = 99.49,
            rasterTolerance = 1,
        )
    }
}
