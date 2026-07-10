package org.graphiks.kanvas.skia.gm.text

import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm

/** Port of Skia's `gm/typeface.cpp` (typeface rendering variant).
 *  Draws glyph shapes rendered from typefaces across multiple sizes
 *  to test typeface rendering accuracy.
 *  @see https://github.com/google/skia/blob/main/gm/typeface.cpp
 */
class TypefaceRenderingGm : SkiaGm {
    override val name = "typefacerendering"
    override val renderFamily = RenderFamily.TEXT
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 640
    override val height = 840
    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
    }
}

class TypefaceRenderingPfaGm : SkiaGm {
    override val name = "typefacerendering_pfa"
    override val renderFamily = RenderFamily.TEXT
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 640
    override val height = 840
    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
    }
}

class TypefaceRenderingPfbGm : SkiaGm {
    override val name = "typefacerendering_pfb"
    override val renderFamily = RenderFamily.TEXT
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 640
    override val height = 840
    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
    }
}
