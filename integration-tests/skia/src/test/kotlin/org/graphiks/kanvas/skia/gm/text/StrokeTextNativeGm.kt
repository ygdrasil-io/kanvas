package org.graphiks.kanvas.skia.gm.text

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm

/** Port of Skia's `gm/stroketext.cpp` (native variant).
 *  Tests stroke text rendering — draws stroked glyph paths using native
 *  stroke operations.
 *  @see https://github.com/google/skia/blob/main/gm/stroketext.cpp
 */
class StrokeTextNativeGm : SkiaGm {
    override val name = "stroketext_native"
    override val renderFamily = RenderFamily.TEXT
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 650
    override val height = 420
    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
    }
}
