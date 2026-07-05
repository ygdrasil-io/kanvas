package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.StrokeJoin
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/stroke_rect_shader.cpp`.
 * Stroked rects with all join flavours under a linear gradient shader.
 * @see https://github.com/google/skia/blob/main/gm/stroke_rect_shader.cpp
 */
class StrokeRectShaderGm : SkiaGm {
    override val name = "stroke_rect_shader"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 47.8
    override val width = 690
    override val height = 300

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val rect = Rect.fromLTRB(0f, 0f, 100f, 100f)
        val stops = listOf(
            GradientStop(0f, Color.RED),
            GradientStop(1f, Color.BLUE),
        )
        val shader = Shader.LinearGradient(
            start = Point(rect.left, rect.top),
            end = Point(rect.right, rect.bottom),
            stops = stops,
            tileMode = TileMode.CLAMP,
        )

        canvas.translate(rect.center.x, rect.center.y)
        val pad = 20f

        for (aa in arrayOf(false, true)) {
            val paint = Paint(
                shader = shader,
                style = PaintStyle.STROKE,
                antiAlias = aa,
            )
            canvas.save()

            val strokeWidth = 10f
            var p = paint.copy(strokeWidth = strokeWidth, strokeJoin = StrokeJoin.BEVEL)
            canvas.drawRect(rect, p)
            canvas.translate(rect.width + pad, 0f)

            p = paint.copy(strokeWidth = strokeWidth, strokeJoin = StrokeJoin.MITER)
            canvas.drawRect(rect, p)
            canvas.translate(rect.width + pad, 0f)

            p = paint.copy(strokeWidth = strokeWidth, strokeJoin = StrokeJoin.MITER, strokeMiter = 0.01f)
            canvas.drawRect(rect, p)
            canvas.translate(rect.width + pad, 0f)

            p = paint.copy(strokeWidth = strokeWidth, strokeJoin = StrokeJoin.ROUND)
            canvas.drawRect(rect, p)
            canvas.translate(rect.width + pad, 0f)

            p = paint.copy(strokeWidth = 0f)
            canvas.drawRect(rect, p)

            canvas.restore()
            canvas.translate(0f, rect.height + pad)
        }
    }
}
