package org.graphiks.kanvas.skia.gm.gradient

import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect

/** Port of Skia's `gm/gradients.cpp` (local perspective variant).
 *  Tests gradient rendering with local perspective transforms — draws
 *  linear and radial gradients with perspective matrices.
 *  @see https://github.com/google/skia/blob/main/gm/gradients.cpp
 */
class GradientsLocalPerspectiveGm : SkiaGm {
    override val name = "gradients_local_perspective"
    override val renderFamily = RenderFamily.GRADIENT
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 840
    override val height = 815

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val pts = arrayOf(Point(0f, 0f), Point(100f, 100f))
        val r = Rect(0f, 0f, 100f, 100f)
        var paint = Paint(antiAlias = true)

        val base5 = listOf(Color.RED, Color.GREEN, Color.BLUE, Color.WHITE, Color.BLACK)
        val gradDatas = listOf(
            listOf(GradientStop(0f, base5[0]), GradientStop(1f, base5[1])),
            listOf(GradientStop(0f, base5[0]), GradientStop(1f, base5[1])),
            listOf(GradientStop(0.25f, base5[0]), GradientStop(0.75f, base5[1])),
            base5.mapIndexed { i, c -> GradientStop(i / 4f, c) },
            listOf(
                GradientStop(0f, base5[0]), GradientStop(0.125f, base5[1]),
                GradientStop(0.5f, base5[2]), GradientStop(0.875f, base5[3]),
                GradientStop(1f, base5[4]),
            ),
            listOf(
                GradientStop(0f, Color.RED), GradientStop(0f, Color.GREEN),
                GradientStop(1f, Color.GREEN), GradientStop(1f, Color.BLUE),
            ),
        )

        canvas.translate(20f, 20f)
        for ((i, gd) in gradDatas.withIndex()) {
            canvas.save()
            val perspective = Matrix33.makeAll(
                sx = 1f, kx = (i + 1).toFloat() / 10f, tx = 0f,
                ky = 0f, sy = 1f, ty = 0f,
                p0 = 0f, p1 = (i + 1).toFloat() / 500f, p2 = 1f,
            )
            for (j in 0 until 5) {
                val shader: Shader? = when (j) {
                    0 -> Shader.LinearGradient(pts[0], pts[1], gd, TileMode.CLAMP)
                    1 -> Shader.RadialGradient(Point(50f, 50f), 50f, gd, TileMode.CLAMP)
                    2 -> Shader.SweepGradient(Point(50f, 50f), stops = gd, tileMode = TileMode.CLAMP)
                    3 -> Shader.ConicalGradient(
                        Point(50f, 50f), 20f,
                        Point(50f + 60f, 50f + 25f), 50f,
                        gd, TileMode.CLAMP,
                    )
                    4 -> Shader.ConicalGradient(
                        Point(50f + 10f, 50f + 10f), 10f,
                        Point(100f - 33.33f, 100f - 33.33f), 33.33f,
                        gd, TileMode.CLAMP,
                    )
                    else -> null
                }
                if (shader != null) {
                    paint = paint.copy(shader = Shader.WithLocalMatrix(shader, perspective))
                }
                canvas.drawRect(r, paint)
                canvas.translate(0f, 120f)
            }
            canvas.restore()
            canvas.translate(120f, 0f)
        }
    }
}
