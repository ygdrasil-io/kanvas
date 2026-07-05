package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/backdrop_imagefilter_croprect.cpp::DEF_SIMPLE_GM(backdrop_imagefilter_croprect_rotated, 600, 500)`.
 * @see https://github.com/google/skia/blob/main/gm/backdrop_imagefilter_croprect.cpp
 */
class BackdropImagefilterCroprectRotatedGm : SkiaGm {
    override val name = "backdrop_imagefilter_croprect_rotated"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 600
    override val height = 500

    override fun draw(canvas: GmCanvas, width0: Int, height0: Int) {
        canvas.translate(140f, -180f)
        canvas.rotate(30f)
        drawBackdropFilterGm(canvas, 32f, 32f) { crop -> makeBlurFilter(crop) }
    }
}

/**
 * Port of Skia's `gm/backdrop_imagefilter_croprect.cpp::DEF_SIMPLE_GM(backdrop_imagefilter_croprect_persp, 600, 500)`.
 * @see https://github.com/google/skia/blob/main/gm/backdrop_imagefilter_croprect.cpp
 */
class BackdropImagefilterCroprectPerspGm : SkiaGm {
    override val name = "backdrop_imagefilter_croprect_persp"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 600
    override val height = 500

    override fun draw(canvas: GmCanvas, width0: Int, height0: Int) {
        val persp = Matrix33.makeAll(
            1f, 8f / 25f, 0f,
            0f, 1f, 0f,
            0f, 0.001f, 1f,
        )
        canvas.concat(persp)
        drawBackdropFilterGm(canvas, 32f, 32f) { crop -> makeBlurFilter(crop) }
    }
}

/**
 * Port of Skia's `gm/backdrop_imagefilter_croprect.cpp::DEF_SIMPLE_GM(backdrop_imagefilter_croprect_nested, 600, 500)`.
 * @see https://github.com/google/skia/blob/main/gm/backdrop_imagefilter_croprect.cpp
 */
class BackdropImagefilterCroprectNestedGm : SkiaGm {
    override val name = "backdrop_imagefilter_croprect_nested"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 600
    override val height = 500

    override fun draw(canvas: GmCanvas, width0: Int, height0: Int) {
        canvas.translate(15f, 10f)
        canvas.clipRect(Rect.fromLTRB(0f, 0f, 600f, 500f))
        canvas.saveLayer(bounds = null, paint = Paint(color = Color(0x80000000u)))
        drawBackdropFilterGm(canvas, 0f, 0f) { crop -> makeInvertFilter(crop) }
        canvas.restore()
    }
}

/**
 * Port of Skia's `gm/backdrop_imagefilter_croprect.cpp::DEF_SIMPLE_GM(backdrop_layer_tilemode, 512, 128)`.
 * @see https://github.com/google/skia/blob/main/gm/backdrop_imagefilter_croprect.cpp
 */
class BackdropLayerTilemodeGm : SkiaGm {
    override val name = "backdrop_layer_tilemode"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 512
    override val height = 128

    override fun draw(canvas: GmCanvas, width0: Int, height0: Int) {
        drawBackdropTileMode(canvas)
        canvas.translate(128f, 0f)
        drawBackdropTileMode(canvas)
        canvas.translate(128f, 0f)
        drawBackdropTileMode(canvas)
        canvas.translate(128f, 0f)
        drawBackdropTileMode(canvas)
    }

    private fun drawBackdropTileMode(canvas: GmCanvas) {
        canvas.save()
        canvas.clipRect(Rect.fromLTRB(0f, 0f, 128f, 128f))
        canvas.saveLayer(bounds = null, paint = null)

        var y = 0
        while (y < 128) {
            val fillColor = if (y % 16 != 0) Color.RED else Color.WHITE
            canvas.drawRect(Rect.fromXYWH(0f, y.toFloat(), 128f, 8f), Paint(color = fillColor))
            y += 8
        }

        val blur = ImageFilter.Blur(32f, 32f)
        canvas.saveLayer(bounds = null, paint = Paint(imageFilter = blur))
        canvas.restore()
        canvas.restore()
        canvas.restore()
    }
}
