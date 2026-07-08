package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.RRect
import org.graphiks.kanvas.types.Rect

/** Port of Skia's `gm/bigrrectaaeffect.cpp`. Tests large rounded-rect circle AA effect. */
class CircleBigGm : SkiaGm {
    override val name = "big_rrect_circle_aa_effect"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 722
    override val height = 722

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(r = 0f, g = 0f, b = 1f)
        canvas.save()
        canvas.translate(10f, 10f)
        val outer = Rect(0f, 0f, 700f, 700f)
        canvas.drawRect(outer, Paint(color = Color.WHITE))
        val inset = 3f
        val oval = Rect(inset, inset, 700f - inset, 700f - inset)
        canvas.drawOval(oval, Paint(antiAlias = true))
        canvas.restore()
    }
}


