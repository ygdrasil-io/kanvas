package org.graphiks.kanvas.skia.gm.gradient

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's gm/alphagradients.cpp.
 * 12-row grid of (start, end) colour pairs each drawn through a
 * linear gradient + black stroke, exercising alpha-modulated interpolation.
 * @see https://github.com/google/skia/blob/main/gm/alphagradients.cpp
 */
class AlphaGradientsGm : SkiaGm {
    override val name = "alphagradients"
    override val renderFamily = RenderFamily.GRADIENT
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 640
    override val height = 480

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val white = Color.fromRGBA(1f, 1f, 1f, 1f)
        val red = Color.fromRGBA(1f, 0f, 0f, 1f)
        val blue = Color.fromRGBA(0f, 0f, 1f, 1f)

        val pairs = listOf(
            white to Color.fromRGBA(0f, 0f, 0f, 0f),
            white to Color.fromRGBA(1f, 0f, 0f, 0f),
            white to Color.fromRGBA(1f, 1f, 0f, 0f),
            white to Color.fromRGBA(1f, 1f, 1f, 0f),
            red to Color.fromRGBA(0f, 0f, 0f, 0f),
            red to Color.fromRGBA(1f, 0f, 0f, 0f),
            red to Color.fromRGBA(1f, 1f, 0f, 0f),
            red to Color.fromRGBA(1f, 1f, 1f, 0f),
            blue to Color.fromRGBA(0f, 0f, 0f, 0f),
            blue to Color.fromRGBA(1f, 0f, 0f, 0f),
            blue to Color.fromRGBA(1f, 1f, 0f, 0f),
            blue to Color.fromRGBA(1f, 1f, 1f, 0f),
        )

        val r = Rect(0f, 0f, 300f, 30f)
        canvas.translate(10f, 10f)

        for (col in 0..1) {
            canvas.save()
            for ((c0, c1) in pairs) {
                drawGrad(canvas, r, c0, c1)
                canvas.translate(0f, r.height + 8f)
            }
            canvas.restore()
            canvas.translate(r.width + 10f, 0f)
        }
    }

    private fun drawGrad(canvas: GmCanvas, r: Rect, c0: Color, c1: Color) {
        val stops = listOf(
            GradientStop(0f, c0),
            GradientStop(1f, c1),
        )
        val paint = Paint(shader = Shader.LinearGradient(
            start = Point(r.left, r.top), end = Point(r.right, r.bottom),
            stops = stops, tileMode = TileMode.CLAMP,
        ))
        canvas.drawRect(r, paint)
        val strokePaint = Paint(color = Color.BLACK, style = org.graphiks.kanvas.paint.PaintStyle.STROKE)
        canvas.drawRect(r, strokePaint)
    }
}
