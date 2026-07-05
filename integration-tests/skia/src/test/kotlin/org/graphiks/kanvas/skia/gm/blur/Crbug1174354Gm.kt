package org.graphiks.kanvas.skia.gm.blur

import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/crbug_1174354.cpp`.
 *
 * STUB: Requires `saveLayer(SaveLayerRec)` with backdrop `SkImageFilter` (crop + blur) and
 * sweep gradient shader — these APIs are not yet available on GmCanvas.
 * @see https://github.com/google/skia/blob/main/gm/crbug_1174354.cpp
 */
class Crbug1174354Gm : SkiaGm {
    override val name = "crbug_1174354"
    override val renderFamily = RenderFamily.BLUR
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 70
    override val height = 250

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        // STUB: backdrop blur with SaveLayerRec not available
    }
}
