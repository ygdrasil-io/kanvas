package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.math.color.ColorMatrixF32

import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.math.geometry.RectF32

/**
 * Port of Skia's gm/colorfilteralpha8.cpp.
 * Tests color filter matrix applied to A8 image.
 * @see https://github.com/google/skia/blob/main/gm/colorfilteralpha8.cpp
 */
class ColorFilterAlpha8Gm : SkiaGm {
    override val name = "colorfilteralpha8"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 400
    override val height = 400

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(1f, 0f, 0f, 1f)

        val pixels = ByteArray(200 * 200) { 0x88.toByte() }
        val image = Image.fromPixels(200, 200, pixels, ColorType.ALPHA_8)

        val opaqueGrayMatrix = floatArrayOf(
            0f, 0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f, 0f,
            0f, 0f, 0f, 0f, 1f,
        )
        val paint = Paint(colorFilter = ColorFilter.Matrix(ColorMatrixF32.of(opaqueGrayMatrix)))
        canvas.drawImage(image, RectF32(100f, 100f, 300f, 300f), paint)
    }
}
