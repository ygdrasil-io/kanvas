package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/wacky_yuv_formats.cpp`.
 *
 * STUB: Requires YUVA (multi-planar YUV + alpha) image support in Kanvas.
 * @see https://github.com/google/skia/blob/main/gm/wacky_yuv_formats.cpp
 */
class WackyYUVFormatsGm : SkiaGm {
    override val name = "wacky_yuv_formats"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 1880
    override val height = 1430

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        // STUB: YUVA pixmap support not yet available in Kanvas
    }
}
