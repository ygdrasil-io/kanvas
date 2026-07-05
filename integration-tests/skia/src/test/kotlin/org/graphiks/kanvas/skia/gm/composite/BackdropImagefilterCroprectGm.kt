package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

fun makeInvertFilter(crop: Rect?): ImageFilter {
    val matrix = ColorFilter.Matrix(floatArrayOf(
        -1f, 0f, 0f, 0f, 1f,
        0f, -1f, 0f, 0f, 1f,
        0f, 0f, -1f, 0f, 1f,
        0f, 0f, 0f, 1f, 0f,
    ))
    return ImageFilter.ColorFilter(matrix)
}

fun makeBlurFilter(crop: Rect?): ImageFilter =
    ImageFilter.Blur(16f, 4f)

fun drawBackdropFilterGm(
    canvas: GmCanvas,
    outsetX: Float,
    outsetY: Float,
    factory: (Rect?) -> ImageFilter,
) {
    val originX = 150f
    val originY = 150f
    val clipRect = Rect.fromLTRB(-50f, -50f, 350f, 100f)
    val cropInLocal = Rect.fromLTRB(50f, 10f, 250f, 40f)
    val cropRect = Rect(
        cropInLocal.left - outsetX,
        cropInLocal.top - outsetY,
        cropInLocal.right + outsetX,
        cropInLocal.bottom + outsetY,
    )
    val imageFilter = factory(cropRect)

    var oy = originY
    for (i in 0 until 2) {
        canvas.save()
        canvas.translate(originX, oy)
        canvas.clipRect(clipRect)

        if (i == 0) {
            canvas.saveLayer(bounds = null, paint = Paint(imageFilter = imageFilter))
        }

        canvas.drawRect(Rect.fromLTRB(0f, 0f, 1000f, 1000f), Paint(
            color = if (i == 0) Color(0xFF00FFFFu) else Color(0xFFFF00FFu),
        ))
        canvas.drawRect(cropInLocal, Paint(
            color = if (i == 0) Color.RED else Color(0xFF00FF00u),
        ))

        if (i == 1) {
            canvas.saveLayer(bounds = null, paint = Paint(imageFilter = imageFilter))
        }

        canvas.restore()
        canvas.restore()
        oy += 150f
    }
}

/**
 * Port of Skia's `gm/backdrop_imagefilter_croprect.cpp::DEF_SIMPLE_GM(backdrop_imagefilter_croprect, 600, 500)`.
 * @see https://github.com/google/skia/blob/main/gm/backdrop_imagefilter_croprect.cpp
 */
class BackdropImagefilterCroprectGm : SkiaGm {
    override val name = "backdrop_imagefilter_croprect"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 600
    override val height = 500

    override fun draw(canvas: GmCanvas, width0: Int, height0: Int) {
        drawBackdropFilterGm(canvas, 0f, 0f) { crop ->
            makeInvertFilter(crop)
        }
    }
}
