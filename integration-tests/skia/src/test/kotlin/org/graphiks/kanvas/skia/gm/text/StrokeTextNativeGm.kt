package org.graphiks.kanvas.skia.gm.text

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm

class StrokeTextNativeGm : SkiaGm {
    override val name = "stroketext_native"
    override val renderFamily = RenderFamily.TEXT
    override val minSimilarity = 0.0
    override val width = 650
    override val height = 420
    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
    }
}
