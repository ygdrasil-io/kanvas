package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/image.cpp::crbug_404394639`.
 * Large image (500x40000) with linear gradient, scaled to 500x500.
 * @see https://github.com/google/skia/blob/main/gm/image.cpp
 */
class Crbug404394639Gm : SkiaGm {
    override val name = "crbug_404394639"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 500
    override val height = 500

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val sourceWidth = 500
        val sourceHeight = 40000
        val surf = Surface(sourceWidth, sourceHeight)
        surf.canvas {
            val shader = Shader.LinearGradient(
                start = Point(0f, 0f),
                end = Point(0f, sourceHeight.toFloat()),
                stops = listOf(
                    GradientStop(0f, Color(0xFF00FFFFu)),
                    GradientStop(1f, Color(0xFFFF00FFu)),
                ),
                tileMode = TileMode.CLAMP,
            )
            drawRect(
                Rect.fromXYWH(0f, 0f, sourceWidth.toFloat(), sourceHeight.toFloat()),
                Paint(shader = shader),
            )
        }
        val largeImage = surf.makeImageSnapshot()
        canvas.drawImageRect(
            largeImage,
            Rect.fromXYWH(0f, 0f, largeImage.width.toFloat(), largeImage.height.toFloat()),
            Rect.fromXYWH(0f, 0f, 500f, 500f),
        )
    }
}
