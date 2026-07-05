package org.graphiks.kanvas.skia.gm.blur

import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.pipeline.BlurStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color

/**
 * Port of Skia's `gm/blurcircles.cpp`.
 * 4×4 grid of blurred circles with varying blur radii and circle radii.
 * @see https://github.com/google/skia/blob/main/gm/blurcircles.cpp
 */
class BlurCirclesGm : SkiaGm {
    override val name = "blurcircles"
    override val renderFamily = RenderFamily.BLUR
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 81.6
    override val width = 950
    override val height = 950

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.scale(1.5f, 1.5f)
        canvas.translate(50f, 50f)

        val circleRadii = floatArrayOf(5f, 10f, 25f, 50f)

        for (i in blurFilters.indices) {
            canvas.save()
            canvas.translate(0f, 150f * i)
            for (j in circleRadii.indices) {
                val paint = Paint(
                    color = Color.BLACK,
                    maskFilter = blurFilters[i],
                )
                val cx = 50f; val cy = 50f
                canvas.save()
                canvas.drawCircle(cx, cy, circleRadii[j], paint)
                canvas.restore()
                canvas.translate(150f, 0f)
            }
            canvas.restore()
        }
    }

    private companion object {
        val blurFilters: List<MaskFilter.Blur> = run {
            val radii = floatArrayOf(1f, 5f, 10f, 20f)
            radii.map { MaskFilter.Blur(BlurStyle.NORMAL, convertRadiusToSigma(it)) }
        }

        fun convertRadiusToSigma(radius: Float): Float =
            if (radius > 0f) 0.57735f * radius + 0.5f else 0f
    }
}
