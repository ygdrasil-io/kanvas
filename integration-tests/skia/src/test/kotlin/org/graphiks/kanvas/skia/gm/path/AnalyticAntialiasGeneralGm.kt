package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect
import kotlin.math.cos
import kotlin.math.sin

/**
 * Port of Skia's `gm/aaa.cpp::analytic_antialias_general`.
 * 8-pointed star path (filled + stroked, rotated 1°) and
 * two pairs of abutting fractional rects.
 * @see https://github.com/google/skia/blob/main/gm/aaa.cpp
 */
class AnalyticAntialiasGeneralGm : SkiaGm {
    override val name = "analytic_antialias_general"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 85.3
    override val width = 800
    override val height = 800

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val paint = Paint(antiAlias = true, color = Color.RED)
        canvas.drawRect(Rect(0f, 0f, 800f, 800f), Paint(color = Color.WHITE))

        val r = 115.2f
        val cc = 128.0f
        val path = Path {
            moveTo(cc + r, cc)
            for (i in 1 until 8) {
                val a = 2.6927937f * i
                lineTo(cc + r * cos(a), cc + r * sin(a))
            }
        }
        canvas.drawPath(path, paint)

        canvas.save()
        canvas.translate(200f, 0f)
        val strokePaint = Paint(
            antiAlias = true,
            color = Color.RED,
            style = PaintStyle.STROKE,
            strokeWidth = 5f,
        )
        canvas.drawPath(path, strokePaint)
        canvas.restore()

        canvas.translate(0f, 300f)
        canvas.drawPath(
            Path { }.also { it.addRect(Rect(20f, 20f, 100.4999f, 100f)); it.addRect(Rect(100.5001f, 20f, 200f, 100f)) },
            paint,
        )

        canvas.translate(300f, 0f)
        canvas.drawPath(
            Path { }.also { it.addRect(Rect(20f, 20f, 100.1f, 100f)); it.addRect(Rect(100.9f, 20f, 200f, 100f)) },
            paint,
        )
    }
}
