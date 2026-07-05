package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/postercircle.cpp::PosterCircleGM` (600 × 450).
 * 3D poster ring with perspective projection and per-poster rotation.
 * Missing API: SkM44 3D transform, perspective projection pipeline.
 * @see https://github.com/google/skia/blob/main/gm/postercircle.cpp
 */
class PosterCircleGm : SkiaGm {
    override val name = "poster_circle"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 600
    override val height = 450
    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        /* TODO: requires 3D perspective projection */
    }
}
