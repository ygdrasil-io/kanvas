package org.graphiks.kanvas.skia.gm.blur

/**
 * Port of Skia's `gm/simpleblurroundrect.cpp`.
 * Renders round-rects with varying blur radii and corner radii, with solid and gradient fills.
 * @see https://github.com/google/skia/blob/main/gm/simpleblurroundrect.cpp
 */

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.pipeline.BlurStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.CornerRadii
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.RRect
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.types.Color

class SimpleBlurRoundRectGm : SkiaGm {
    override val name = "simpleblurroundrect"
    override val renderFamily = RenderFamily.BLUR
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 64.3
    override val width = 1000
    override val height = 500

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.scale(1.5f, 1.5f)
        canvas.translate(50f, 50f)

        val blurRadii = floatArrayOf(1f, 5f, 10f, 20f)
        val cornerRadii = floatArrayOf(1f, 5f, 10f, 20f)
        val r = Rect(0f, 0f, 25f, 25f)

        for (row in blurRadii.indices) {
            canvas.save()
            canvas.translate(0f, (r.height + 50f) * row)
            for (pair in cornerRadii.indices) {
                val paint = Paint(
                    color = Color.BLACK,
                    maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, 0.57735f * blurRadii[row] + 0.5f),
                )
                val cr = cornerRadii[pair]
                val rrect = RRect(r, CornerRadii(cr, cr), CornerRadii(cr, cr), CornerRadii(cr, cr), CornerRadii(cr, cr))
                canvas.drawPath(Path { }.apply { addRRect(rrect) }, paint)
                canvas.translate(r.width + 50f, 0f)

                val shaded = paint.copy(shader = makeRadial())
                canvas.drawPath(Path { }.apply { addRRect(rrect) }, shaded)
                canvas.translate(r.width + 50f, 0f)
            }
            canvas.restore()
        }
    }

    private fun makeRadial(): Shader.ConicalGradient {
        val colors = listOf(Color.RED, Color.GREEN)
        val positions = floatArrayOf(0.25f, 0.75f)
        return Shader.ConicalGradient(
            start = Point(60f, 25f),
            startRadius = 100f / 7f,
            end = Point(50f, 50f),
            endRadius = 50f,
            stops = colors.indices.map { i -> GradientStop(positions[i], colors[i]) },
            tileMode = TileMode.CLAMP,
        )
    }
}
