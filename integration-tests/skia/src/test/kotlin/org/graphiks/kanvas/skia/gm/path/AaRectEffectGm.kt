package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/aarecteffect.cpp` — `AARectEffect`.
 * GPU-only Ganesh GM that directly exercises GrFragmentProcessor::Rect for
 * anti-aliased rectangle clipping via the Ganesh backend.
 * @see https://github.com/google/skia/blob/main/gm/aarecteffect.cpp
 */
class AaRectEffectGm : SkiaGm {
    override val name = "aa_rect_effect"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 210
    override val height = 250

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        TODO("STUB.AA_RECT_EFFECT — requires GrFragmentProcessor::Rect (Ganesh GPU-only)")
    }
}
