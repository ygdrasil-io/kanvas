package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/closedcappedhairlines.cpp::hairlines_buttcap`.
 * Hairline stroke paths with butt cap on open/closed contours.
 * @see https://github.com/google/skia/blob/main/gm/closedcappedhairlines.cpp
 */
class HairlinesButtcapGm : SkiaGm {
    override val name = "hairlines_buttcap"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = 250
    override val height = 250

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val paint = Paint(
            style = PaintStyle.STROKE,
            strokeWidth = 0f,
            color = Color.BLACK,
            antiAlias = true,
            strokeCap = StrokeCap.BUTT,
        )

        val pathSurface = Surface(GRID_WH, GRID_WH)
        pathSurface.canvas {
            // first row - on-pixel, open
            drawPath(
                Path { moveTo(0f, 5f); lineTo(5f, 5f); lineTo(5f, 0f) }
                    .transform(Matrix33.translate(5f, 5f)),
                paint,
            )
            drawPath(
                Path { moveTo(0f, 0f); quadTo(15f, 5f, 0f, 10f) }
                    .transform(Matrix33.translate(20f, 5f)),
                paint,
            )
            drawPath(
                Path { moveTo(0f, 0f); cubicTo(-5f, 0f, -5f, 5f, 0f, 10f) }
                    .transform(Matrix33.translate(40f, 5f)),
                paint,
            )

            // second row - off-pixel, open
            drawPath(
                Path { moveTo(0f, 5f); lineTo(5f, 5f); lineTo(5f, 0f) }
                    .transform(Matrix33.translate(5.5f, 20.5f)),
                paint,
            )
            drawPath(
                Path { moveTo(0f, 0f); quadTo(15f, 5f, 0f, 10f) }
                    .transform(Matrix33.translate(20.5f, 20.5f)),
                paint,
            )
            drawPath(
                Path { moveTo(0f, 0f); cubicTo(-5f, 0f, -5f, 5f, 0f, 10f) }
                    .transform(Matrix33.translate(40.5f, 20.5f)),
                paint,
            )

            // third row - on-pixel, closed
            drawPath(
                Path { moveTo(0f, 5f); lineTo(5f, 5f); lineTo(5f, 0f); close() }
                    .transform(Matrix33.translate(5f, 35f)),
                paint,
            )
            drawPath(
                Path { moveTo(0f, 0f); quadTo(15f, 5f, 0f, 10f); close() }
                    .transform(Matrix33.translate(20f, 35f)),
                paint,
            )
            drawPath(
                Path { moveTo(0f, 0f); cubicTo(-5f, 0f, -5f, 5f, 0f, 10f); close() }
                    .transform(Matrix33.translate(40f, 35f)),
                paint,
            )

            // fourth row - off-pixel, closed
            drawPath(
                Path { moveTo(0f, 5f); lineTo(5f, 5f); lineTo(5f, 0f); close() }
                    .transform(Matrix33.translate(5.5f, 50.5f)),
                paint,
            )
            drawPath(
                Path { moveTo(0f, 0f); quadTo(15f, 5f, 0f, 10f); close() }
                    .transform(Matrix33.translate(20.5f, 50.5f)),
                paint,
            )
            drawPath(
                Path { moveTo(0f, 0f); cubicTo(-5f, 0f, -5f, 5f, 0f, 10f); close() }
                    .transform(Matrix33.translate(40.5f, 50.5f)),
                paint,
            )
        }

        val pathImg = pathSurface.makeImageSnapshot()
        canvas.drawImage(
            pathImg,
            Rect.fromXYWH(0f, 0f, GRID_WH.toFloat(), GRID_WH.toFloat()),
        )

        canvas.save()
        canvas.scale(SCALE.toFloat(), SCALE.toFloat())
        canvas.drawImage(
            pathImg,
            Rect.fromXYWH(15f, 0f, GRID_WH.toFloat(), GRID_WH.toFloat()),
        )
        canvas.translate(15f, 0f)
        drawHairlineGrid(canvas)
        canvas.restore()
    }
}

private const val GRID_WH = 70
private const val SCALE = 4

private fun drawHairlineGrid(canvas: GmCanvas) {
    val gridPaint = Paint(
        color = Color(0xFF444444u),
        style = PaintStyle.STROKE,
        strokeWidth = 0f,
    )
    for (y in 0..GRID_WH) {
        canvas.drawLine(0f, y.toFloat(), GRID_WH.toFloat(), y.toFloat(), gridPaint)
    }
    for (x in 0..GRID_WH) {
        canvas.drawLine(x.toFloat(), 0f, x.toFloat(), GRID_WH.toFloat(), gridPaint)
    }
}
