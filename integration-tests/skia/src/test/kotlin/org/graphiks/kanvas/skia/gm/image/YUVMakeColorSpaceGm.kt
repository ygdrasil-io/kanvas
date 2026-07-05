package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/yuvtorgbeffect.cpp`.
 *
 * STUB: Requires YUVA (multi-planar YUV + alpha) image support in Kanvas.
 * @see https://github.com/google/skia/blob/main/gm/yuvtorgbeffect.cpp
 */
class YUVMakeColorSpaceGm : SkiaGm {
    override val name = "yuv_make_color_space"
    override val renderFamily = RenderFamily.IMAGE
    override val minSimilarity = 0.0
    override val width = 1100
    override val height = 750

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        // STUB: YUVA pixmap support not yet available in Kanvas
    }
}
