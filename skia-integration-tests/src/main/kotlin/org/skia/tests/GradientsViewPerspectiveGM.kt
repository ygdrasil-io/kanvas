package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize
import org.graphiks.math.SkMatrix

/**
 * Port of upstream Skia's `gm/gradients.cpp::GradientsViewPerspectiveGM`
 * (`DEF_GM(return new GradientsViewPerspectiveGM(true);)` — dither variant).
 *
 * Inherits [GradientsGM] (same 6×5 grid of gradient permutations) but
 * applies a perspective transform to the *view* matrix before drawing.
 *
 * **Note** : the `dither` parameter is dropped — kanvas-skia raster
 * doesn't toggle dither.
 */
public class GradientsViewPerspectiveGM : GradientsGM() {

    override fun getName(): String = "gradients_view_perspective"

    // Upstream declares 840 × 500 here (shorter than GradientsGM's 815)
    // because the perspective squashes the grid vertically.
    override fun getISize(): SkISize = SkISize.Make(840, 500)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        // perspective.setIdentity(); perspective.setPerspY(0.001f); perspective.setSkewX(8/25)
        val perspective = SkMatrix(
            sx = 1f, kx = 8f / 25f, tx = 0f,
            ky = 0f, sy = 1f, ty = 0f,
            persp0 = 0f, persp1 = 0.001f, persp2 = 1f,
        )
        c.concat(perspective)
        super.onDraw(canvas)
    }
}
