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
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/gradients_no_texture.cpp::GradientsNoTextureGM`
 * (640 x 615). 4x5 grid of gradient cells.
 * @see https://github.com/google/skia/blob/main/gm/gradients_no_texture.cpp
 */
class GradientsNoTextureGm : SkiaGm {
    override val name = "gradients_no_texture"
    override val renderFamily = RenderFamily.GRADIENT
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 640
    override val height = 615

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0xDD / 255f, 0xDD / 255f, 0xDD / 255f)

        val baseColors = listOf(Color.RED, Color.fromRGBA(0f, 1f, 0f), Color.BLUE, Color.WHITE)
        val gradData = listOf(
            listOf(baseColors[0]),
            baseColors.take(2),
            baseColors.take(3),
            baseColors.take(4),
        )

        val p0 = Point(0f, 0f)
        val p1 = Point(50f, 50f)
        val tm = TileMode.CLAMP
        val rect = Rect.fromLTRB(0f, 0f, 50f, 50f)

        fun evenlyStopped(colors: List<Color>): List<GradientStop> =
            colors.mapIndexed { i, c -> GradientStop(if (colors.size > 1) i.toFloat() / (colors.size - 1) else 0f, c) }

        val paint = Paint(antiAlias = true)

        canvas.translate(20f, 20f)
        for (i in gradData.indices) {
            canvas.save()
            for (j in 0 until 5) {
                val shader = when (j) {
                    0 -> Shader.LinearGradient(
                        start = p0, end = p1,
                        stops = evenlyStopped(gradData[i]),
                        tileMode = tm,
                    )
                    1 -> {
                        val cx = (p0.x + p1.x) * 0.5f; val cy = (p0.y + p1.y) * 0.5f
                        Shader.RadialGradient(
                            center = Point(cx, cy), radius = cx,
                            stops = evenlyStopped(gradData[i]),
                            tileMode = tm,
                        )
                    }
                    2 -> {
                        val cx = (p0.x + p1.x) * 0.5f; val cy = (p0.y + p1.y) * 0.5f
                        Shader.SweepGradient(
                            center = Point(cx, cy), startAngle = 0f, endAngle = 360f,
                            stops = evenlyStopped(gradData[i]),
                            tileMode = tm,
                        )
                    }
                    3 -> {
                        val cx = (p0.x + p1.x) * 0.5f; val cy = (p0.y + p1.y) * 0.5f
                        val c0 = Point(cx, cy)
                        val c1 = Point(p0.x + 0.6f * (p1.x - p0.x), p0.y + 0.25f * (p1.y - p0.y))
                        val r1 = (p1.x - p0.x) / 7f; val r0 = (p1.x - p0.x) / 2f
                        Shader.ConicalGradient(
                            start = c1, startRadius = r1, end = c0, endRadius = r0,
                            stops = evenlyStopped(gradData[i]),
                            tileMode = tm,
                        )
                    }
                    else -> {
                        val r0 = (p1.x - p0.x) / 10f; val r1 = (p1.x - p0.x) / 3f
                        val c0 = Point(p0.x + r0, p0.y + r0)
                        val c1 = Point(p1.x - r1, p1.y - r1)
                        Shader.ConicalGradient(
                            start = c1, startRadius = r1, end = c0, endRadius = r0,
                            stops = evenlyStopped(gradData[i]),
                            tileMode = tm,
                        )
                    }
                }
                canvas.drawRect(rect, paint.copy(shader = shader))
                canvas.translate(0f, rect.height + 20f)
            }
            canvas.restore()
            canvas.translate(rect.width + 20f, 0f)
        }
    }
}
