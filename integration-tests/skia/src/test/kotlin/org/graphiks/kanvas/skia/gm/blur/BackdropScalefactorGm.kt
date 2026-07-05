package org.graphiks.kanvas.skia.gm.blur

import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/backdrop.cpp::DEF_SIMPLE_GM(backdrop_scalefactor, 768, 1024)`.
 *
 * STUB: Requires backdrop scale factor via `SaveLayerRec` — this is a Ganesh-internal
 * quality knob (downsample → blur → upsample) with no surface in the public CPU raster API.
 * @see https://github.com/google/skia/blob/main/gm/backdrop.cpp
 */
class BackdropScalefactorGm : SkiaGm {
    override val name = "backdrop_scalefactor"
    override val renderFamily = RenderFamily.BLUR
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 768
    override val height = 1024

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        // STUB: backdrop scale factor not exposed
    }
}
