package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.FillType
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.paint.StrokeJoin
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/linepaths.cpp::linepath` (DEF_SIMPLE_GM,
 * 1240 × 390).
 *
 * Draws a simple line path into a clipped rect, demonstrating
 * different cap/join/fill/style combinations.
 *
 * @see https://github.com/google/skia/blob/main/gm/linepaths.cpp
 */
class LinePathGm : SkiaGm {
    override val name = "linepath"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 1240
    override val height = 390

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val path = Path {
            moveTo(25f, 15f)
            lineTo(75f, 15f)
        }

        val cellRect = Rect(0f, 0f, 100f, 30f)
        canvas.save()
        canvas.translate(10f, 30f)

        val fills = listOf(FillType.WINDING, FillType.EVEN_ODD)
        val styles = listOf(PaintStyle.FILL, PaintStyle.STROKE, PaintStyle.STROKE_AND_FILL)
        val caps = listOf(
            StrokeCap.BUTT to StrokeJoin.BEVEL,
            StrokeCap.ROUND to StrokeJoin.ROUND,
            StrokeCap.SQUARE to StrokeJoin.BEVEL,
        )

        var x = 0f
        for (capJoin in caps) {
            for (fill in fills) {
                for (style in styles) {
                    val paint = Paint(
                        color = Color.fromRGBA(0x00.toFloat(), 0x70.toFloat() / 255f, 0x00.toFloat(), 1f),
                        style = style,
                        strokeCap = capJoin.first,
                        strokeJoin = capJoin.second,
                        strokeWidth = 10f,
                    )
                    val filledPath = Path {
                        moveTo(25f, 15f)
                        lineTo(75f, 15f)
                    }.also { it.fillType = fill }
                    canvas.save()
                    canvas.clipRect(cellRect)
                    canvas.drawPath(filledPath, paint)
                    canvas.restore()

                    val rectPaint = Paint(
                        color = Color.BLACK,
                        style = PaintStyle.STROKE,
                        strokeWidth = 1f,
                        antiAlias = true,
                    )
                    canvas.drawRect(cellRect, rectPaint)

                    x += cellRect.width + 40f
                    if (x >= 300f) {
                        x = 0f
                        canvas.translate(0f, cellRect.height + 40f)
                    } else {
                        canvas.translate(cellRect.width + 40f, 0f)
                    }
                }
            }
        }
        canvas.restore()
    }
}
