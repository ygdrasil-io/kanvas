package org.graphiks.kanvas.skia.gm.text

import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm

/** Port of Skia's `gm/userfont.cpp`.
 *  Draws text samples with a user-specified typeface to test custom
 *  typeface registration and rendering.
 *  @see https://github.com/google/skia/blob/main/gm/userfont.cpp
 */
class UserTypefaceGm : SkiaGm {
    override val name = "user_typeface"
    override val renderFamily = RenderFamily.TEXT
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 810
    override val height = 452
    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
    }
}
