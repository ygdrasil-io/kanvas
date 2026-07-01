package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.StrokeJoin
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

class StrokerectAnisotropicGm : SkiaGm {
    override val name = "strokerect_anisotropic"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = 160
    override val height = 160

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val aaPaint = Paint(
            color = Color.BLACK,
            antiAlias = true,
            strokeWidth = 10f,
            style = PaintStyle.STROKE,
        )
        val bwPaint = Paint(
            color = Color.BLACK,
            strokeWidth = 10f,
            style = PaintStyle.STROKE,
        )

        drawSqooshedRect(canvas, 20f, 40.5f, aaPaint)
        drawSqooshedRect(canvas, 20f, 110.5f, bwPaint)

        drawSqooshedRect(canvas, 60.5f, 40f, aaPaint)
        drawSqooshedRect(canvas, 60.5f, 110f, bwPaint)

        val aaBevel = aaPaint.copy(strokeJoin = StrokeJoin.BEVEL)
        val bwBevel = bwPaint.copy(strokeJoin = StrokeJoin.BEVEL)

        drawSqooshedRect(canvas, 100f, 40.5f, aaBevel)
        drawSqooshedRect(canvas, 100f, 110.5f, bwBevel)

        drawSqooshedRect(canvas, 140.5f, 40f, aaBevel)
        drawSqooshedRect(canvas, 140.5f, 110f, bwBevel)
    }

    private fun drawSqooshedRect(canvas: GmCanvas, tx: Float, ty: Float, p: Paint) {
        canvas.save()
        canvas.translate(tx, ty)
        canvas.scale(0.03f, 2f)
        canvas.drawPath(
            Path { }.apply { addRect(Rect.fromLTRB(-500f, -10f, 500f, 10f)) },
            p,
        )
        canvas.restore()
    }
}
