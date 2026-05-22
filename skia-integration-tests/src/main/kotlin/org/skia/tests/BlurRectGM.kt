package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Placeholder port of upstream Skia `gm/blurrect.cpp::BlurRectGM`.
 *
 * Original draws a wide matrix of rects with `SkMaskFilter::MakeBlur`
 * across all `SkBlurStyle` values, with / without a radial gradient
 * shader and clipping, at two scales. Exercises the analytic
 * fast-path for blurred rects.
 *
 * Related blurred-rect GMs already in `:skia-integration-tests` cover
 * specific shapes (`Blur2RectsGM`, `Blur2RectsNonNinepatchGM`,
 * `BlurCirclesGM`, `BigBlursGM`). This placeholder is for the
 * canonical 860 × 820 upstream layout, gated on full
 * shader+maskfilter+clipping interplay.
 *
 * TODO: full port — radial-gradient `make_radial()`, donut helper,
 * skewed donut, all `SkBlurStyle` permutations.
 */
public class BlurRectGM(
    private val gmName: String = "blurrects",
    @Suppress("unused") private val alpha: Int = 0xFF,
) : GM() {
    override fun getName(): String = gmName
    override fun getISize(): SkISize = SkISize.Make(860, 820)

    override fun onDraw(canvas: SkCanvas?) {
        // TODO: full blurrects matrix — SkBlurStyle × shader × clip × scale.
    }
}
