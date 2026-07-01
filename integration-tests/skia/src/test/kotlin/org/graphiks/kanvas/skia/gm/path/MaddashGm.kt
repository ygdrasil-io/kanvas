package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.PathEffect
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

class MaddashGm : SkiaGm {
    override val name = "maddash"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = 1600
    override val height = 1600

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawRect(Rect.fromLTRB(0f, 0f, 1600f, 1600f), Paint())

        val intervals = floatArrayOf(2.5f, 10f)
        var p = Paint(
            color = Color.RED,
            style = PaintStyle.STROKE,
            strokeWidth = 380f,
            pathEffect = PathEffect.Dash(intervals, 0f),
            antiAlias = true,
        )

        canvas.drawCircle(400f, 400f, 200f, p)

        val quadPath = Path {
            moveTo(800f, 400f)
            quadTo(1000f, 400f, 1000f, 600f)
            quadTo(1000f, 800f, 800f, 800f)
            quadTo(600f, 800f, 600f, 600f)
            quadTo(600f, 400f, 800f, 400f)
            close()
        }
        canvas.translate(350f, 150f)
        p = p.copy(strokeWidth = 320f)
        canvas.drawPath(quadPath, p)

        val cubicPath = Path {
            moveTo(800f, 400f)
            cubicTo(900f, 400f, 1000f, 500f, 1000f, 600f)
            cubicTo(1000f, 700f, 900f, 800f, 800f, 800f)
            cubicTo(700f, 800f, 600f, 700f, 600f, 600f)
            cubicTo(600f, 500f, 700f, 400f, 800f, 400f)
            close()
        }
        canvas.translate(-550f, 500f)
        p = p.copy(strokeWidth = 300f)
        canvas.drawPath(cubicPath, p)
    }
}
