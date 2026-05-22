package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Placeholder port of upstream Skia
 * `gm/blurrect.cpp::BlurRectCompareGM`.
 *
 * Original compares the analytic blur-rect mask fast-path against the
 * brute-force gaussian convolution reference, side-by-side at a fixed
 * sigma. Used to validate the analytic shortcut.
 *
 * TODO: full port — needs `SkBlurMask::ComputeBlurredScanline` /
 * brute-force gaussian reference plus the analytic path side-by-side
 * harness.
 */
public class BlurRectCompareGM : GM() {
    override fun getName(): String = "blurrect_compare"
    override fun getISize(): SkISize = SkISize.Make(900, 1200)

    override fun onDraw(canvas: SkCanvas?) {
        // TODO: analytic blur-rect vs brute-force gaussian side-by-side.
    }
}
