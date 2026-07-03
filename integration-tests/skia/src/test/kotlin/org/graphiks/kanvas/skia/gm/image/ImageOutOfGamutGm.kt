package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.Bitmap
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/bitmappremul.cpp :: image_out_of_gamut`.
 *
 * Draws two 31×31 images whose pixels have out-of-gamut premul colours
 * (RGB > A), exercising both RGBA_8888 and BGRA_8888 colour types.
 *
 * The raw uint32 value `0x40000000 | ((x * 8) << 8) | (y * 8)` is
 * decoded per the colour type's memory order:
 *
 *  - RGBA_8888: R = y*8, G = x*8, B = 0, A = 0x40
 *  - BGRA_8888: B = y*8, G = x*8, R = 0, A = 0x40
 * @see https://github.com/google/skia/blob/main/gm/bitmappremul.cpp
 */
class ImageOutOfGamutGm : SkiaGm {
    override val name = "image_out_of_gamut"
    override val renderFamily = RenderFamily.IMAGE
    override val minSimilarity = 0.0
    override val tolerance = 2
    override val width = 2 * K_BOX_SIZE + 3 * K_PADDING
    override val height = K_BOX_SIZE + 2 * K_PADDING

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0.5f, 0.5f, 0.5f)

        val rgba = makeOutOfGamutImage(ColorType.RGBA_8888)
        val bgra = makeOutOfGamutImage(ColorType.BGRA_8888)

        canvas.translate(K_PADDING.toFloat(), K_PADDING.toFloat())
        canvas.drawImage(rgba, Rect(0f, 0f, K_BOX_SIZE.toFloat(), K_BOX_SIZE.toFloat()))
        canvas.translate((K_BOX_SIZE + K_PADDING).toFloat(), 0f)
        canvas.drawImage(bgra, Rect(0f, 0f, K_BOX_SIZE.toFloat(), K_BOX_SIZE.toFloat()))
    }

    private fun makeOutOfGamutImage(colorType: ColorType): Image {
        val bm = Bitmap(K_BOX_SIZE, K_BOX_SIZE, colorType)
        for (y in 0 until K_BOX_SIZE) {
            for (x in 0 until K_BOX_SIZE) {
                val color = when (colorType) {
                    ColorType.RGBA_8888 -> Color.fromRGBA(
                        (y * 8).coerceAtMost(255) / 255f,
                        (x * 8).coerceAtMost(255) / 255f,
                        0f,
                        0x40 / 255f,
                    )
                    ColorType.BGRA_8888 -> Color.fromRGBA(
                        0f,
                        (x * 8).coerceAtMost(255) / 255f,
                        (y * 8).coerceAtMost(255) / 255f,
                        0x40 / 255f,
                    )
                    else -> Color.TRANSPARENT
                }
                bm.setPixel(x, y, color)
            }
        }
        return bm.toImage()
    }

    private companion object {
        const val K_BOX_SIZE = 31
        const val K_PADDING = 5
    }
}
