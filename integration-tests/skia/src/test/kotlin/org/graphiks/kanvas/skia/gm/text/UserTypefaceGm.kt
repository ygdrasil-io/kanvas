package org.graphiks.kanvas.skia.gm.text

import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm

class UserTypefaceGm : SkiaGm {
    override val name = "user_typeface"
    override val renderFamily = RenderFamily.TEXT
    override val minSimilarity = 0.0
    override val width = 810
    override val height = 452
    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
    }
}
