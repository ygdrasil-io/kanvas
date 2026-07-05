package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/yuvtorgbsubset.cpp`.
 *
 * STUB: Requires YUVA (multi-planar YUV + alpha) image support in Kanvas.
 * @see https://github.com/google/skia/blob/main/gm/yuvtorgbsubset.cpp
 */
class YuvToRgbSubsetEffectGm : SkiaGm {
    override val name = "yuv_to_rgb_subset_effect"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 1310
    override val height = 540

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        // STUB: YUVA pixmap support not yet available in Kanvas
    }
}
