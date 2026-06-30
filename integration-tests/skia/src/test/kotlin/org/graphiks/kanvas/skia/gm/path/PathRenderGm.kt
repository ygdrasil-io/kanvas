package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.Paint
import org.graphiks.kanvas.Canvas
import org.graphiks.kanvas.Path
import org.graphiks.kanvas.KanvasFillType
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
class PathRenderGm : SkiaGm {
    override val name = "path-render"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 90.0

    override fun draw(canvas: Canvas, width: Int, height: Int) {
        val cx = width / 2f
        val cy = height / 2f

        val star = Path().apply {
            moveTo(cx, cy - 120f)
            lineTo(cx + 28f, cy - 36f)
            lineTo(cx + 114f, cy - 36f)
            lineTo(cx + 46f, cy + 14f)
            lineTo(cx + 70f, cy + 96f)
            lineTo(cx, cy + 50f)
            lineTo(cx - 70f, cy + 96f)
            lineTo(cx - 46f, cy + 14f)
            lineTo(cx - 114f, cy - 36f)
            lineTo(cx - 28f, cy - 36f)
            close()
        }
        canvas.drawPath(star, Paint().apply {
            r = 0.9f; g = 0.2f; b = 0.2f
        })

        val circleRing = Path().apply {
            fillType = KanvasFillType.EVEN_ODD
            addCircle(cx, cy, 80f)
            addCircle(cx, cy, 50f)
        }
        canvas.drawPath(circleRing, Paint().apply {
            r = 0.2f; g = 0.6f; b = 0.9f
        })

        val diamond = Path().apply {
            moveTo(cx + 130f, cy - 120f)
            lineTo(cx + 180f, cy - 70f)
            lineTo(cx + 130f, cy - 20f)
            lineTo(cx + 80f, cy - 70f)
            close()
        }
        canvas.drawPath(diamond, Paint().apply {
            r = 0.2f; g = 0.8f; b = 0.3f
        })

        val curvePath = Path().apply {
            moveTo(cx - 100f, cy + 120f)
            quadTo(cx - 50f, cy + 60f, cx, cy + 120f)
            quadTo(cx + 50f, cy + 180f, cx + 100f, cy + 120f)
        }
        canvas.drawPath(curvePath, Paint().apply {
            r = 0.9f; g = 0.7f; b = 0.1f
        })
    }

}
