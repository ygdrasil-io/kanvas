package org.graphiks.kanvas.skia.gm.gradient

import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.math.geometry.Point2F32
import org.graphiks.kanvas.types.RRect
import org.graphiks.math.geometry.RectF32

/**
 * Port of Skia's `gm/testgradient.cpp`.
 * Mixed multi-primitive smoke test: linear gradient rect, oval, circle, stroked round rect.
 * @see https://github.com/google/skia/blob/main/gm/testgradient.cpp
 */
class TestGradientGm : SkiaGm {
    override val name = "testgradient"
    override val renderFamily = RenderFamily.GRADIENT
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 800
    override val height = 800

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(1f, 1f, 1f, 1f)

        val rect = RectF32(10f, 10f, 110f, 170f)

        val gradPaint = Paint(
            style = PaintStyle.FILL,
            antiAlias = true,
            strokeWidth = 4f,
            shader = Shader.LinearGradient(
                start = Point2F32(0f, 0f),
                end = Point2F32(256f, 256f),
                stops = listOf(
                    GradientStop(0f, Color.BLUE),
                    GradientStop(1f, Color(0xFFFFFF00u)),
                ),
                tileMode = TileMode.CLAMP,
            ),
        )
        canvas.drawRect(rect, gradPaint)

        val ovalRect = RectF32(rect.left + 40f, rect.top + 80f, rect.right + 40f, rect.bottom + 80f)
        val ovalPaint = Paint(style = PaintStyle.FILL, antiAlias = true, strokeWidth = 4f, color = Color(0xFFE6B89Cu))
        canvas.drawOval(ovalRect, ovalPaint)

        val solidPaint = Paint(style = PaintStyle.FILL, antiAlias = true, strokeWidth = 4f, color = Color(0xFF9CAFB7u))
        canvas.drawCircle(180f, 50f, 25f, solidPaint)

        val offsetRect = RectF32(rect.left + 80f, rect.top + 50f, rect.right + 80f, rect.bottom + 50f)
        val strokePaint = Paint(
            antiAlias = true, strokeWidth = 4f,
            color = Color(0xFF4281A4u),
            style = PaintStyle.STROKE,
        )
        canvas.drawRRect(RRect(offsetRect, 10f), strokePaint)
    }
}
