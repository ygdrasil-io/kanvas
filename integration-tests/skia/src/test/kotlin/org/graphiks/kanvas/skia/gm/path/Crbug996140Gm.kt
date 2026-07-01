package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color

class Crbug996140Gm : SkiaGm {
    override val name = "crbug_996140"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 85.6
    override val width = 300
    override val height = 300

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawRect(
            Rect(0f, 0f, width.toFloat(), height.toFloat()),
            Paint(color = Color.fromRGBA(1f, 1f, 1f, 1f)),
        )

        val cx = 19.221f
        val cy = 720f - 6.76f
        val radius = 0.0295275590551181f
        val s = 203.20f
        val tx = -14.55f
        val ty = -711.51f

        canvas.translate(-800f, -200f)
        canvas.scale(s, s)
        canvas.translate(tx, ty)

        val rx = radius
        val ry = radius
        val x1 = cx + rx
        val y1 = cy
        val x2 = cx - rx
        val y2 = cy

        val path = Path {
            moveTo(x1, y1)
            arcTo(rx, ry, 0f, false, true, x2, y2)
            arcTo(rx, ry, 0f, false, true, x1, y1)
        }

        val stroke = Paint(
            color = Color.fromRGBA(0f, 0f, 1f, 1f),
            strokeWidth = 1f,
            style = PaintStyle.STROKE,
            antiAlias = true,
        )
        val fill = Paint(
            color = Color.fromRGBA(1f, 0f, 0f, 1f),
            style = PaintStyle.FILL,
            antiAlias = true,
        )

        canvas.drawPath(path, stroke)
        canvas.drawPath(path, fill)
    }
}
