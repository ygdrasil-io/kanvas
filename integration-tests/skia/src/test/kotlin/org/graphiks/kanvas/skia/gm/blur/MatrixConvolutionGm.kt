package org.graphiks.kanvas.skia.gm.blur

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

/**
 * Port of Skia's `gm/matrixconvolution.cpp` — basic kernel variant (3×3).
 * Approximation using drawing primitives since MatrixConvolution filter is not yet available.
 * @see https://github.com/google/skia/blob/main/gm/matrixconvolution.cpp
 */
open class MatrixConvolutionGm(
    private val nameSuffix: String,
) : SkiaGm {
    override val name = "matrixconvolution$nameSuffix"
    override val renderFamily = RenderFamily.BLUR
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 500
    override val height = 300

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRect(Rect(0f, 0f, w, h), Paint(color = Color.BLACK))
        val grad = Shader.LinearGradient(
            Point(0f, 0f), Point(0f, 80f),
            listOf(GradientStop(0f, Color.WHITE), GradientStop(1f, colorTwo())),
        )
        val patternPaint = Paint(shader = grad)
        for (x in 0 until 3) {
            for (y in 0 until 3) {
                val ox = 10 + x * 100
                val oy = 10 + y * 100
                canvas.save()
                canvas.translate(ox.toFloat(), oy.toFloat())
                canvas.drawRect(Rect(0f, 0f, 80f, 80f), patternPaint)
                canvas.restore()
            }
        }
        canvas.save()
        canvas.translate(310f, 10f)
        canvas.drawRect(Rect(0f, 0f, 60f, 60f), Paint(color = Color.fromRGBA(0.3f, 0.3f, 0.3f, 1f)))
        canvas.restore()
        canvas.save()
        canvas.translate(310f, 110f)
        canvas.drawRect(Rect(0f, 0f, 60f, 60f), Paint(color = Color.fromRGBA(0.15f, 0.15f, 0.15f, 1f)))
        canvas.restore()
        canvas.save()
        canvas.translate(310f, 210f)
        canvas.drawRect(Rect(0f, 0f, 60f, 60f), Paint(color = Color.fromRGBA(0.4f, 0.4f, 0.4f, 1f)))
        canvas.restore()
    }

    protected open fun colorTwo(): Color = Color.fromRGBA(0.25f, 0.25f, 0.25f, 1f)
}


