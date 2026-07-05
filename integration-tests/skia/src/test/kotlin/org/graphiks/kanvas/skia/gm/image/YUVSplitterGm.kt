package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/wacky_yuv_formats.cpp`.
 *
 * STUB: Requires YUVA (multi-planar YUV + alpha) image support in Kanvas.
 * @see https://github.com/google/skia/blob/main/gm/wacky_yuv_formats.cpp
 */
class YUVSplitterGm : SkiaGm {
    override val name = "yuv_splitter"
    override val renderFamily = RenderFamily.IMAGE
    override val minSimilarity = 0.0
    override val width = 1280
    override val height = 768

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        // STUB: YUVA pixmap support not yet available in Kanvas
    }
}
