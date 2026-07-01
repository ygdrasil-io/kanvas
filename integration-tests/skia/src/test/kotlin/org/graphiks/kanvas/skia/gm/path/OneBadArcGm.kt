package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

class OneBadArcGm : SkiaGm {
    override val name = "onebadarc"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = 100
    override val height = 100

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val path = Path {
            moveTo(20f, 20f)
            lineTo(34.1421f, 34.1421f)
            quadTo(20f, 48.2843f, 5.85786f, 34.1421f)
            lineTo(20f, 20f)
            close()
        }
        val p0 = Paint(
            color = Color.fromRGBA(1f, 0f, 0f, 100f / 255f),
            strokeWidth = 15f,
            style = PaintStyle.STROKE,
        )
        canvas.translate(20f, 0f)
        canvas.drawPath(path, p0)
        canvas.drawArc(Rect.fromLTRB(60f, 0f, 100f, 40f), 45f, 90f, useCenter = true, p0)
    }
}
