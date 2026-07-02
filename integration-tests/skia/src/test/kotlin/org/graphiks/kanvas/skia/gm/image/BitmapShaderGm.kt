package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/bitmapshader.cpp::BitmapShaderGM`.
 * Placeholder stub — tiled bitmap-shader coverage lives in dedicated GMs.
 * @see https://github.com/google/skia/blob/main/gm/bitmapshader.cpp
 */
class BitmapShaderGm : SkiaGm {
    override val name = "bitmapshaders"
    override val renderFamily = RenderFamily.IMAGE
    override val minSimilarity = 0.0
    override val width = 150
    override val height = 100

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
    }
}
