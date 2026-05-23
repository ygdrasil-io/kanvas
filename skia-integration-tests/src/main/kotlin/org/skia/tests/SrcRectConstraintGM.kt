package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Stub port of Skia's `gm/srcrectconstraint.cpp::SrcRectConstraintGM`
 * (registered as `srcrectconstraint`, 800 x 1000).
 *
 * Upstream draws a small bitmap into a grid of [SkCanvas.drawImageRect]
 * calls, alternating between
 * [SkCanvas.SrcRectConstraint.kStrict_SrcRectConstraint] and
 * [SkCanvas.SrcRectConstraint.kFast_SrcRectConstraint], to verify
 * that the strict variant prevents the GPU sampler from leaking
 * neighbouring pixels at the src-rect edges.
 *
 * `:kanvas-skia` does not expose the `SkCanvas.SrcRectConstraint`
 * enum on `drawImageRect` -- all bitmap draws use the
 * fast constraint implicitly. Until the constraint flag is
 * plumbed through the canvas / GPU dispatcher the strict-vs-fast
 * difference cannot be observed.
 *
 * TODO: missing API -- `SkCanvas.SrcRectConstraint` parameter on
 * `drawImageRect`. Flag-planting stub: empty draw, fixed size.
 */
public class SrcRectConstraintGM : GM() {

    override fun getName(): String = "srcrectconstraint"
    override fun getISize(): SkISize = SkISize.Make(800, 1000)

    override fun onDraw(canvas: SkCanvas?) {
        // TODO: missing API -- SkCanvas.SrcRectConstraint on drawImageRect.
    }
}
