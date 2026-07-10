package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.ColorSpace
import org.graphiks.kanvas.types.Gamut
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.types.TransferFunction

/**
 * Port of Skia's `gm/makecolorspace.cpp::reinterpretcolorspace`
 * (`DEF_SIMPLE_GM_CAN_FAIL(reinterpretcolorspace, canvas, errorMsg, 128*3, 128*3)`).
 *
 * Draws a 3×3 grid. Rows are lazy (codec-decoded), raster, and GPU (all
 * the same image source on Kanvas). Within each row the three columns are:
 *  - col 0: original image
 *  - col 1: image re-tagged into a different color space via
 *    [Image.reinterpretColorSpace] — metadata-only; pixels are shared
 *  - col 2: image re-tagged into the different space and then back to sRGB
 *    via [Image.reinterpretColorSpace] — round-trips the metadata tag
 *
 * @see https://github.com/google/skia/blob/main/gm/makecolorspace.cpp
 */
class ReinterpretColorSpaceGm : SkiaGm {
    override val name = "reinterpretcolorspace"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 384
    override val height = 384

    override fun draw(canvas: GmCanvas, width0: Int, height0: Int) {
        val srgb = ColorSpace.SRGB
        val spin = ColorSpace("ColorSpin", TransferFunction.SRGB, Gamut.DISPLAY_P3)

        val image = loadImage("images/color_wheel.png") ?: return

        canvas.drawImage(image, Rect(0f, 0f, 128f, 128f))
        canvas.drawImage(image.reinterpretColorSpace(spin), Rect(128f, 0f, 256f, 128f))
        val spun = image.reinterpretColorSpace(spin)
        canvas.drawImage(spun.reinterpretColorSpace(srgb), Rect(256f, 0f, 384f, 128f))

        canvas.translate(0f, 128f)

        canvas.drawImage(image, Rect(0f, 0f, 128f, 128f))
        canvas.drawImage(image.reinterpretColorSpace(spin), Rect(128f, 0f, 256f, 128f))
        canvas.drawImage(spun.reinterpretColorSpace(srgb), Rect(256f, 0f, 384f, 128f))

        canvas.translate(0f, 128f)

        canvas.drawImage(image, Rect(0f, 0f, 128f, 128f))
        canvas.drawImage(image.reinterpretColorSpace(spin), Rect(128f, 0f, 256f, 128f))
        canvas.drawImage(spun.reinterpretColorSpace(srgb), Rect(256f, 0f, 384f, 128f))
    }

    private fun loadImage(path: String): Image? {
        val bytes = this::class.java.classLoader?.getResourceAsStream(path)?.readBytes() ?: return null
        return Image.decode(bytes)
    }
}
