package org.graphiks.kanvas.skia.gm.blur

import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/crbug_1313579.cpp`.
 *
 * STUB: Requires `saveLayer(SaveLayerRec)` with backdrop `SkImageFilter`, matrix concatenation,
 * and clip rect with near-integer transforms — GmCanvas lacks backdrop SaveLayerRec support.
 * @see https://github.com/google/skia/blob/main/gm/crbug_1313579.cpp
 */
class Crbug1313579Gm : SkiaGm {
    override val name = "crbug_1313579"
    override val renderFamily = RenderFamily.BLUR
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 110
    override val height = 110

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        // STUB: backdrop blur with SaveLayerRec not available
    }
}
