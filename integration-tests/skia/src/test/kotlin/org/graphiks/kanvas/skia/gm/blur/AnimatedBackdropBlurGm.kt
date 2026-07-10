package org.graphiks.kanvas.skia.gm.blur

import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/animatedimageblurs.cpp::AnimatedBackdropBlur`.
 *
 * STUB: Requires `saveLayer(SaveLayerRec)` with backdrop `SkImageFilter`
 * (Crop -> Blur -> Crop chain) — not yet available on GmCanvas.
 * @see https://github.com/google/skia/blob/main/gm/animatedimageblurs.cpp
 */
class AnimatedBackdropBlurGm : SkiaGm {
    override val name = "animatedbackdropblur"
    override val renderFamily = RenderFamily.BLUR
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 512
    override val height = 1024

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        // STUB: backdrop SaveLayerRec with Crop/Blur filter chain not available
    }
}
