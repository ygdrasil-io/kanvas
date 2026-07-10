package org.graphiks.kanvas.skia.gm.color

import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect

/** Tests shader-based color rendering with linear gradients in each quadrant. */
class ShaderGm : SkiaGm {
    override val name = "shader"
    override val renderFamily = RenderFamily.COLOR
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 256
    override val height = 256

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val w2 = width / 2f
        val h2 = height / 2f
        val grad1 = Shader.LinearGradient(
            Point(0f, 0f), Point(w2, h2),
            listOf(GradientStop(0f, Color.RED), GradientStop(1f, Color.BLUE)),
        )
        val grad2 = Shader.LinearGradient(
            Point(w2, 0f), Point(width.toFloat(), h2),
            listOf(GradientStop(0f, Color.GREEN), GradientStop(1f, Color.fromRGBA(1f, 1f, 0f, 1f))),
        )
        val grad3 = Shader.LinearGradient(
            Point(0f, h2), Point(w2, height.toFloat()),
            listOf(GradientStop(0f, Color.fromRGBA(0f, 1f, 1f, 1f)), GradientStop(1f, Color.fromRGBA(1f, 0f, 1f, 1f))),
        )
        val grad4 = Shader.LinearGradient(
            Point(w2, h2), Point(width.toFloat(), height.toFloat()),
            listOf(GradientStop(0f, Color.WHITE), GradientStop(1f, Color.BLACK)),
        )
        canvas.drawRect(Rect(0f, 0f, w2, h2), Paint(shader = grad1))
        canvas.drawRect(Rect(w2, 0f, width.toFloat(), h2), Paint(shader = grad2))
        canvas.drawRect(Rect(0f, h2, w2, height.toFloat()), Paint(shader = grad3))
        canvas.drawRect(Rect(w2, h2, width.toFloat(), height.toFloat()), Paint(shader = grad4))
    }
}
