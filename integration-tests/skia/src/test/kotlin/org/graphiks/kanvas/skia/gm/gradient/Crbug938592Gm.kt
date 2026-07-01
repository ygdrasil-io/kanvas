package org.graphiks.kanvas.skia.gm.gradient

import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/crbug_938592.cpp` (500 x 300).
 * Hard-stop linear gradient mirrored 4 ways via translate + scale.
 * @see https://github.com/google/skia/blob/main/gm/crbug_938592.cpp
 */
class Crbug938592Gm : SkiaGm {
    override val name = "crbug_938592"
    override val renderFamily = RenderFamily.GRADIENT
    override val minSimilarity = 83.0
    override val width = 500
    override val height = 300

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val shader = Shader.LinearGradient(
            start = Point(0f, 0f),
            end = Point(0f, 30f),
            stops = listOf(
                GradientStop(0f, Color.BLUE),
                GradientStop(9f / 20f, Color.BLUE),
                GradientStop(9f / 20f, Color.RED),
                GradientStop(11f / 20f, Color.RED),
                GradientStop(11f / 20f, Color.GREEN),
                GradientStop(20f / 20f, Color.GREEN),
            ),
            tileMode = TileMode.CLAMP,
        )
        val paint = Paint(shader = shader)

        val mirrorX = 400
        val mirrorY = 200
        canvas.translate(50f, 50f)
        for (i in 0 until 4) {
            canvas.save()
            if ((i and 0b01) != 0) {
                canvas.translate(0f, mirrorY.toFloat())
                canvas.scale(1f, -1f)
            }
            if ((i and 0b10) != 0) {
                canvas.translate(mirrorX.toFloat(), 0f)
                canvas.scale(-1f, 1f)
            }
            canvas.drawRect(Rect.fromLTRB(0f, 0f, 150f, 30f), paint)
            canvas.restore()
        }
    }
}
