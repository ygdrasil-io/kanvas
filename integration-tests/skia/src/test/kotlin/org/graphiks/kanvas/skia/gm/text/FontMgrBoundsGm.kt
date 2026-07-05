package org.graphiks.kanvas.skia.gm.text

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color

class FontMgrBoundsGm(
    private val fScaleX: Float,
    private val fSkewX: Float,
) : SkiaGm {
    constructor() : this(1f, 0f)

    override val name: String
        get() = if (fScaleX != 1f || fSkewX != 0f) "fontmgr_bounds_${fmtScalar(fScaleX)}_${fmtScalar(fSkewX)}" else "fontmgr_bounds"
    override val renderFamily = RenderFamily.TEXT
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 1024
    override val height = 850

    private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val font = Font(typeface, 100f)
        canvas.translate(10f, 120f)
        val paint = Paint(color = Color.RED, style = PaintStyle.STROKE, antiAlias = true)
        canvas.drawString("Liberation Sans", 0f, 0f, font, paint)
    }

    private companion object {
        private fun fmtScalar(v: Float): String {
            val s = v.toString()
            return if (s.endsWith(".0")) s.dropLast(2) else s
        }
    }
}
