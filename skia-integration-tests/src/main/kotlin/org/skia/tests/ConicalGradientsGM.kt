package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Upstream-name alias for `gm/gradients_2pt_conical.cpp::ConicalGradientsGM`.
 *
 * Already covered by three GradCaseType-specialised siblings :
 *  - `ConicalGradients2ptInsideGM`   (kInside)
 *  - `ConicalGradients2ptOutsideGM`  (kOutside)
 *  - `ConicalGradients2ptTileModeGMs` (Edge + tileMode variants)
 *
 * This class plants the upstream base-name flag; the draw is a no-op.
 */
public class ConicalGradientsGM : GM() {
    override fun getName(): String = "gradients_2pt_conical"
    override fun getISize(): SkISize = SkISize.Make(840, 815)

    override fun onDraw(canvas: SkCanvas?) {
        // No-op alias : ported as ConicalGradients2pt{Inside,Outside,TileMode}GMs.
    }
}
