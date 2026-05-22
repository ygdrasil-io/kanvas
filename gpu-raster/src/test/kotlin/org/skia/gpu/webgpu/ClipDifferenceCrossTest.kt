package org.skia.gpu.webgpu

import org.junit.jupiter.api.Test
import org.skia.gpu.webgpu.testing.runGpuCrossTest
import org.skia.tests.BlurredClippedCircleGM
import org.skia.tests.Skbug9319GM

/**
 * M4 -- `clipRect(_, kDifference)` and `clipRRect(_, kDifference)` cross-
 * backend acceptance.
 *
 * Pre-M4 both GMs threw `IllegalStateException ("SkWebGpuDevice does not
 * support arbitrary clipPath")` at `SkCanvas.bindClip` time : the
 * canvas-side `clipPathDifference` always rasterised the path into an
 * `aaClip` then nuked the `simpleShapeClip` slot, so the GPU bound the
 * draw with an alpha-mask clip the device couldn't honour.
 *
 * M4 widens the analytic clip path : `SkClipShape` now carries an
 * [org.skia.foundation.SkClipOp] field and `clipPathDifference` runs the
 * same simple-shape detector as the intersect arm. When the path matches
 * a canonical rect / oval / circle / uniform-corner rrect under an axis-
 * aligned CTM, the GPU device evaluates `coverage *= 1 - rrect_cov(p)`
 * per pixel instead of falling back to the alpha mask. Rect-difference
 * is encoded as kind 2 with `rx = ry = 0` so the rrect coverage formula
 * collapses to axis-aligned rect coverage.
 *
 *  - `Skbug9319GM` : two cells, `clipRect(rect, kDifference) +
 *    drawRect(rect, paint{maskFilter=BlurNormal})` and the matching
 *    rrect variant. Validates that the analytic difference clip
 *    composes with the blur mask path.
 *  - `BlurredClippedCircleGM` : nested clips ending with `clipRRect(
 *    MakeOval(...), kDifference)` + a blurred + colourFilter'd
 *    `drawRRect`. Exercises kDifference under a scaled CTM.
 *
 * Floors are kept deliberately loose pending follow-up tuning : the
 * blur-mask composite under M4 still routes through the parent device's
 * scissor for the final blit, which differs from CPU raster's per-pixel
 * mask multiply by a few percent at the halo. The key is that both
 * tests now actually **run** rather than throwing.
 */
class ClipDifferenceCrossTest {

    @Test
    fun `Skbug9319GM survives clipRect_kDifference and clipRRect_kDifference on GPU`() {
        // Measured at M4 land : GPU 84.44 % (110672 / 131072 matching px,
        // maxDiff 255 on the halo edge where the blur-mask composite's
        // sub-pixel coverage drifts from the CPU reference). Floor sits
        // 4 points below the measurement to allow Apple M-series WGPU
        // jitter.
        runGpuCrossTest(Skbug9319GM(), floor = 80.0)
    }

    @Test
    fun `BlurredClippedCircleGM survives clipRRect_kDifference oval on GPU`() {
        // Measured at M4 land : GPU 71.22 % (664874 / 933528 matching px).
        // The blur-mask composite under a scaled (2x) CTM + oval kDiff
        // clip still drifts more than the rect-shaped variants because
        // the inverted oval coverage interacts with the halo's
        // separable Gaussian on a curved boundary. Floor 67 leaves room
        // for ULP jitter on the analytic distance test while still
        // catching real regressions.
        runGpuCrossTest(BlurredClippedCircleGM(), floor = 67.0)
    }
}
