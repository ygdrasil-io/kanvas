package org.graphiks.kanvas.skia.gm.gradient

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/gradients.cpp` (RadialGradient2GM).
 * Reproduces b/7671058: sweep and radial gradients with InPremul flag.
 * @see https://github.com/google/skia/blob/main/gm/gradients.cpp
 */
class RadialGradient2Gm : SkiaGm {
    override val name = "radial_gradient2"
    override val renderFamily = RenderFamily.GRADIENT
    override val minSimilarity = 50.9
    override val width = 800
    override val height = 400

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val sweepColors = listOf(
            Color.fromRGBA(1f, 0f, 0f, 1f),
            Color.fromRGBA(1f, 1f, 0f, 1f),
            Color.fromRGBA(0f, 1f, 0f, 1f),
            Color.fromRGBA(0f, 1f, 1f, 1f),
            Color.fromRGBA(0f, 0f, 1f, 1f),
            Color.fromRGBA(1f, 0f, 1f, 1f),
            Color.fromRGBA(1f, 0f, 0f, 1f),
        )
        val colors1 = listOf(
            Color.fromRGBA(1f, 1f, 1f, 1f),
            Color.fromRGBA(0f, 0f, 0f, 0f),
        )
        val colors2 = listOf(
            Color.fromRGBA(0f, 0f, 0f, 1f),
            Color.fromRGBA(0f, 0f, 0f, 0f),
        )

        val cx = 200f
        val cy = 200f
        val radius = 150f
        val center = Point(cx, cy)
        val tm = TileMode.CLAMP

        val sweepStops = sweepColors.indices.map { i ->
            GradientStop(i.toFloat() / (sweepColors.size - 1), sweepColors[i])
        }
        val whiteStops = listOf(
            GradientStop(0f, colors1[0]),
            GradientStop(1f, colors1[1]),
        )
        val blackStops = listOf(
            GradientStop(0f, colors2[0]),
            GradientStop(1f, colors2[1]),
        )

        for (iteration in 0 until 2) {
            val paint1 = Paint(
                shader = Shader.SweepGradient(
                    center = center,
                    stops = sweepStops,
                    tileMode = tm,
                ),
            )
            val paint2 = Paint(
                shader = Shader.RadialGradient(
                    center = center,
                    radius = radius,
                    stops = whiteStops,
                    tileMode = tm,
                ),
            )
            val paint3 = Paint(
                shader = Shader.RadialGradient(
                    center = center,
                    radius = radius,
                    stops = blackStops,
                    tileMode = tm,
                ),
            )

            canvas.drawCircle(cx, cy, radius, paint1)
            canvas.drawCircle(cx, cy, radius, paint3)
            canvas.drawCircle(cx, cy, radius, paint2)

            canvas.translate(400f, 0f)
        }
    }
}
