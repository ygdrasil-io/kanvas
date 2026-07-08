package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/gradients.cpp` — `DEF_SIMPLE_GM(fancy_gradients, ...)`.
 * Draws three circles with complex shader compositions (picture shaders with
 * linear/sweep/radial gradient tiles, blender shaders).
 * @see https://github.com/google/skia/blob/main/gm/gradients.cpp
 */
class FancyGradientsGm : SkiaGm {
    override val name = "fancy_gradients"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 800
    override val height = 300

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        TODO("STUB.FANCY_GRADIENTS — requires picture shader + sweep/radial shader composition")
    }
}
