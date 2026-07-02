package org.graphiks.kanvas.skia.gm.gradient

import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/bug6643.cpp::bug6643`.
 * Sweep gradient (transparent → green → transparent) rendered as a direct shader.
 * Original upstream uses a picture-shader round-trip; this port applies the sweep
 * gradient directly since Kanvas does not expose Shader.Picture.
 * @see https://github.com/google/skia/blob/main/gm/bug6643.cpp
 */
class Bug6643Gm : SkiaGm {
    override val name = "bug6643"
    override val renderFamily = RenderFamily.GRADIENT
    override val minSimilarity = 0.0
    override val width = 200
    override val height = 200

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(1f, 1f, 1f, 1f)

        val gradient = Shader.SweepGradient(
            center = Point(100f, 100f),
            stops = listOf(
                GradientStop(0f, Color.TRANSPARENT),
                GradientStop(0.5f, Color.GREEN),
                GradientStop(1f, Color.TRANSPARENT),
            ),
            tileMode = TileMode.CLAMP,
        )

        val paint = Paint(shader = gradient, antiAlias = true)
        canvas.drawRect(Rect.fromXYWH(0f, 0f, 200f, 200f), paint)
    }
}
