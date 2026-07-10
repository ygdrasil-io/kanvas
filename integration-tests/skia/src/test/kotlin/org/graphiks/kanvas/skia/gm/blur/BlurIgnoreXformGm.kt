package org.graphiks.kanvas.skia.gm.blur

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.pipeline.BlurStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.CornerRadii
import org.graphiks.kanvas.types.RRect
import org.graphiks.kanvas.types.Rect

private data class MatrixScale(val scale: Float, val name: String)

private val kMatrixScales = arrayOf(
    MatrixScale(1.0f, "Identity"),
    MatrixScale(0.5f, "Scale = 0.5"),
    MatrixScale(2.0f, "Scale = 2.0"),
)
private val kBlurFlagNames = arrayOf("none", "IgnoreTransform")

private fun convertRadiusToSigma(radius: Float): Float =
    if (radius > 0f) 0.57735f * radius + 0.5f else 0f

private val font = Font(
    typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!,
    size = 12f,
)

private fun drawBlurIgnoreXform(canvas: GmCanvas, drawType: String) {
    val blurFilter2 = MaskFilter.Blur(BlurStyle.NORMAL, convertRadiusToSigma(20f))
    val basePaint = Paint(antiAlias = true, color = Color.BLACK)

    canvas.translate(10f, 25f)
    canvas.save()
    canvas.translate(80f, 0f)
    for (i in 0 until 2) {
        canvas.save()
        canvas.translate(i * 150f, 0f)
        for (scale in kMatrixScales) {
            canvas.save()
            canvas.scale(scale.scale, scale.scale)
            val kRadius = 20f
            val coord = 50f * 1f / scale.scale
            val rect = Rect.fromXYWH(coord - kRadius, coord - kRadius, 2 * kRadius, 2 * kRadius)
            val rrect = RRect(Rect(rect.left, rect.top, rect.right, rect.bottom), CornerRadii(kRadius / 2f, kRadius / 2f))

            for (j in 0 until 2) {
                canvas.save()
                canvas.translate(10f * (1 - j), 10f * (1 - j))
                val bp = basePaint.copy(maskFilter = if (i == 1) blurFilter2 else null)
                when (drawType) {
                    "circle" -> canvas.drawCircle(coord, coord, kRadius, bp)
                    "rect" -> canvas.drawRect(rect, bp)
                    "rrect" -> canvas.drawRRect(rrect, bp)
                }
                canvas.restore()
            }
            canvas.restore()
            canvas.translate(0f, 150f)
        }
        canvas.restore()
    }
    canvas.restore()

    canvas.translate(10f, 0f)
    canvas.save()
    for (i in 0 until 2) {
        canvas.drawString(kBlurFlagNames[i], 100f, 0f, font, Paint())
        canvas.translate(130f, 0f)
    }
    canvas.restore()
    for (scale in kMatrixScales) {
        canvas.drawString(scale.name, 0f, 50f, font, Paint())
        canvas.translate(0f, 150f)
    }
}

/**
 * Port of Skia's `gm/blurignorexform.cpp::BlurIgnoreXformGM`.
 * Circle variant.
 * @see https://github.com/google/skia/blob/main/gm/blurignorexform.cpp
 */
class BlurIgnoreXformCircleGm : SkiaGm {
    override val name = "blur_ignore_xform_circle"
    override val renderFamily = RenderFamily.BLUR
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 375
    override val height = 475

    override fun draw(canvas: GmCanvas, width0: Int, height0: Int) {
        drawBlurIgnoreXform(canvas, "circle")
    }
}

/**
 * Port of Skia's `gm/blurignorexform.cpp::BlurIgnoreXformGM`.
 * Rect variant.
 * @see https://github.com/google/skia/blob/main/gm/blurignorexform.cpp
 */
class BlurIgnoreXformRectGm : SkiaGm {
    override val name = "blur_ignore_xform_rect"
    override val renderFamily = RenderFamily.BLUR
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 375
    override val height = 475

    override fun draw(canvas: GmCanvas, width0: Int, height0: Int) {
        drawBlurIgnoreXform(canvas, "rect")
    }
}

/**
 * Port of Skia's `gm/blurignorexform.cpp::BlurIgnoreXformGM`.
 * RRect variant.
 * @see https://github.com/google/skia/blob/main/gm/blurignorexform.cpp
 */
class BlurIgnoreXformRRectGm : SkiaGm {
    override val name = "blur_ignore_xform_rrect"
    override val renderFamily = RenderFamily.BLUR
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 375
    override val height = 475

    override fun draw(canvas: GmCanvas, width0: Int, height0: Int) {
        drawBlurIgnoreXform(canvas, "rrect")
    }
}
