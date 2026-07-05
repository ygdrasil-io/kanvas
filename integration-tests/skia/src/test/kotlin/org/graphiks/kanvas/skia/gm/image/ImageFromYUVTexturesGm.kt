package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/imagefromyuvtextures.cpp`.
 *
 * STUB: Requires YUVA (multi-planar YUV + alpha) image support in Kanvas.
 * @see https://github.com/google/skia/blob/main/gm/imagefromyuvtextures.cpp
 */
class ImageFromYUVTexturesGm : SkiaGm {
    override val name = "image_from_yuv_textures"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 1950
    override val height = 800

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        // STUB: YUVA pixmap support not yet available in Kanvas
    }
}
