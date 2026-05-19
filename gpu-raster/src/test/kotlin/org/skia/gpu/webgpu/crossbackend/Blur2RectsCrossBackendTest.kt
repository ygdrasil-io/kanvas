package org.skia.gpu.webgpu.crossbackend

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runCrossBackendTest
import org.skia.tests.Blur2RectsGM

/**
 * Phase MaskFilter-blur cross-backend test : `Blur2RectsGM`.
 *
 * Two-rect even-odd ring path with `paint.maskFilter =
 * SkBlurMaskFilter(kNormal, sigma=2.3)`, drawn twice at slightly
 * different sub-pixel phases. Exercises :
 *  - drawPath dispatch through the blur gate (rect contour, not
 *    drawRect),
 *  - multi-contour path rendered to the offscreen shape mask,
 *  - blur footprint independence under sub-pixel translation.
 *
 * Sigma 2.3 -> radius 7 -> tail dies off in ~7 px past the rect
 * boundary. Reference PNG is the upstream Skia output.
 *
 * Floors (observed) :
 *  - raster : 98.33 %
 *  - GPU    : 98.20 %
 * Both at tolerance 8 (TEXTUAL_GM_TOLERANCE default). The 0.13 %
 * GPU - raster gap is the F16 intermediate's residual blend
 * precision drift versus the CPU's float32 raster -- well inside
 * the cross-backend WARNING_BAND_PERCENT envelope.
 */
class Blur2RectsCrossBackendTest {

    @Test
    fun `Blur2RectsGM matches reference on raster and GPU backends`() {
        runCrossBackendTest(
            gm = Blur2RectsGM(),
            rasterFloor = 98.0,
            gpuFloor = 98.0,
            rasterTolerance = 8,
            gpuTolerance = 8,
        )
    }
}
