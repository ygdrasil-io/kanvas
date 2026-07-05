package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's gm/colorfilterimagefilter.cpp (colorfilterimagefilter_layer).
 * Tests saveLayer with image filter containing a grayscale color matrix.
 * @see https://github.com/google/skia/blob/main/gm/colorfilterimagefilter.cpp
 */
class ColorFilterImageFilterLayerGm : SkiaGm {
    override val name = "colorfilterimagefilter_layer"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 32
    override val height = 32

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val matrix = floatArrayOf(
            0.2126f, 0.7152f, 0.0722f, 0f, 0f,
            0.2126f, 0.7152f, 0.0722f, 0f, 0f,
            0.2126f, 0.7152f, 0.0722f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
        val cf = ColorFilter.Matrix(matrix)
        val paint = Paint(imageFilter = ImageFilter.ColorFilter(cf, null))
        canvas.saveLayer(null, paint)
        canvas.drawColor(1f, 0f, 0f, 1f)
        canvas.restore()
    }
}
