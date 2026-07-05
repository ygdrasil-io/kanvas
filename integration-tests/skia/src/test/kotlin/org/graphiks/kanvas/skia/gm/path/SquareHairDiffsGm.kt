package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.canvas.Canvas
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/hairlines.cpp::squarehair_diffs`.
 * Overlays Butt/Square/Round cap hairlines in R/G/B channels.
 * @see https://github.com/google/skia/blob/main/gm/hairlines.cpp
 */
class SquareHairDiffsGm : SkiaGm {
    override val name = "squarehair_diffs"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 600
    override val height = 720

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val aliases = booleanArrayOf(false, true)
        val widths = floatArrayOf(0f, 1f, 1.001f)
        val caps = arrayOf(StrokeCap.BUTT, StrokeCap.SQUARE, StrokeCap.ROUND)
        val colors = listOf(Color.RED, Color.GREEN, Color.BLUE)

        for (alias in aliases) {
            for (w in widths) {
                canvas.drawRect(Rect.fromLTRB(120f, 0f, 600f, 100f), Paint(color = Color.BLACK))

                for (i in 0..2) {
                    val surface = Surface(120, 25)
                    surface.canvas {
                        val paint = Paint(
                            antiAlias = alias,
                            strokeWidth = w,
                            strokeCap = caps[i],
                            color = colors[i],
                            style = PaintStyle.STROKE,
                        )
                        drawSquareHairTests(this, paint)
                    }
                    val img = surface.makeImageSnapshot()
                    canvas.drawImage(img, Rect.fromXYWH(0f, 30f * i, 120f, 25f))

                    canvas.save()
                    canvas.scale(4f, 4f)
                    canvas.drawImage(img, Rect.fromXYWH(30f, 0f, 120f, 25f), Paint(blendMode = BlendMode.PLUS))
                    canvas.restore()
                }

                canvas.translate(0f, 120f)
            }
            canvas.translate(0f, 20f)
        }
    }

    private fun drawSquareHairTests(canvas: Canvas, paint: Paint) {
        canvas.drawPath(Path { moveTo(10f, 10f); lineTo(20f, 10f) }, paint)
        canvas.drawPath(Path { moveTo(10f, 15f); lineTo(20f, 15f); close() }, paint)
        canvas.drawPath(Path { moveTo(10f, 20.5f); lineTo(20f, 20.5f) }, paint)
        canvas.drawPath(Path { moveTo(30f, 10f); lineTo(30f, 20f) }, paint)
        canvas.drawPath(Path { moveTo(35.5f, 10f); lineTo(35.5f, 20f) }, paint)
        canvas.drawPath(Path { moveTo(40f, 10f); lineTo(50f, 20f) }, paint)

        canvas.drawPath(
            Path {
                moveTo(60f, 10f)
                quadTo(60f, 20f, 70f, 20f)
                lineTo(70f, 10f)
                lineTo(80f, 10f)
            },
            paint,
        )

        canvas.drawPath(
            Path {
                moveTo(90f, 10f)
                cubicTo(90f, 20f, 100f, 20f, 100f, 10f)
                lineTo(110f, 10f)
            },
            paint,
        )
    }
}
