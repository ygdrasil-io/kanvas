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
 * Port of Skia's gm/gradients_no_texture.cpp::LinearGradientGM.
 * 100 stacked 5-px-tall linear-gradient bars with growing width.
 * @see https://github.com/google/skia/blob/main/gm/gradients_no_texture.cpp
 */
class LinearGradientGm : SkiaGm {
    override val name = "linear_gradient"
    override val renderFamily = RenderFamily.GRADIENT
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 500
    override val height = 500

    private val kWidthBump = 30f
    private val kHeight = 5f
    private val kMinWidth = 540f
    private val kCount = 100

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val white = Color.fromRGBA(1f, 1f, 1f, 1f)
        val green = Color.fromRGBA(0f, 0.3216f, 0f, 1f)

        for (i in 0 until kCount) {
            val p0 = Point(0f, 0f)
            val p1 = Point(500f + i * kWidthBump, 0f)
            val currentWidth = kMinWidth + i * kWidthBump
            val unitPos = floatArrayOf(0f, 50f, 70f, 500f, 540f)
            val pos = FloatArray(6)
            for (inner in unitPos.indices) {
                pos[inner] = unitPos[inner] / currentWidth
            }
            pos[5] = 1f

            val stops = listOf(
                GradientStop(pos[0], white),
                GradientStop(pos[1], white),
                GradientStop(pos[2], green),
                GradientStop(pos[3], green),
                GradientStop(pos[4], white),
                GradientStop(pos[5], white),
            )

            val paint = Paint(shader = Shader.LinearGradient(
                start = p0, end = p1,
                stops = stops, tileMode = TileMode.CLAMP,
            ))
            canvas.drawRect(
                Rect(0f, i * kHeight, currentWidth, (i + 1) * kHeight),
                paint
            )
        }
    }
}
