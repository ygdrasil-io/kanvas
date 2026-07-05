package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/anisotropic.cpp` (base registration).
 *
 * No-op: the three anisotropic image-scale variants are ported separately
 * as AnisotropicImageScale{Linear,Mip,Aniso}Gm.
 * @see https://github.com/google/skia/blob/main/gm/anisotropic.cpp
 */
class AnisotropicGm : SkiaGm {
    override val name = "anisotropic_image_scale"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 522
    override val height = 1330

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
    }
}
