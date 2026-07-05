package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/shadowutils.cpp::DEF_SIMPLE_GM(shadow_utils_gaussian_colorfilter, 512, 256)`.
 *
 * STUB: Requires `SkColorFilterPriv.makeGaussian` — internal Skia API for the Gaussian falloff
 * color filter used by the shadow mesh tessellator. Not exposed in Kanvas.
 * @see https://github.com/google/skia/blob/main/gm/shadowutils.cpp
 */
class ShadowUtilsGaussianColorFilterGm : SkiaGm {
    override val name = "shadow_utils_gaussian_colorfilter"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 512
    override val height = 256

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        // STUB: SkColorFilterPriv.makeGaussian not exposed
    }
}
