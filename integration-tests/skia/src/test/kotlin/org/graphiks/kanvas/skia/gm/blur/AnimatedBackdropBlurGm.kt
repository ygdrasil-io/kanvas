package org.graphiks.kanvas.skia.gm.blur

import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/animatedimageblurs.cpp::AnimatedBackdropBlur`.
 *
 * Port contract: draw the scrolling text and `color_wheel.png`, then apply a
 * backdrop `SaveLayerRec` filter chain of Crop(Decal) -> Blur(30) ->
 * Crop(Mirror) over `(0, 100, 512, 400)`. GmCanvas does not yet expose a
 * backdrop filter, so this GM remains explicitly unsupported.
 * @see https://github.com/google/skia/blob/main/gm/animatedimageblurs.cpp
 */
class AnimatedBackdropBlurGm : SkiaGm {
    override val name = "animated-backdrop-blur"
    override val renderFamily = RenderFamily.BLUR
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 512
    override val height = 1024

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        // STUB: backdrop SaveLayerRec with Crop/Blur filter chain not available.
    }
}
