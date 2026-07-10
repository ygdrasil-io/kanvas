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
 * Port of Skia's `gm/makecolorspace.cpp::makecolorspace`
 * (`DEF_SIMPLE_GM_CAN_FAIL(makecolorspace, canvas, errorMsg, 128*3, 128*4)`).
 *
 * Draws a 3×4 grid of 128×128 images: two rows of "lazy" (codec-decoded)
 * images and two rows of raster images. Within each pair of rows the
 * columns are:
 *  - col 0: original image
 *  - col 1: wide-gamut (Display P3 with sRGB gamma) via [Image.reinterpretColorSpace]
 *  - col 2: wide-gamut linear via [Image.reinterpretColorSpace]
 *
 * The first source image (`mandrill_128.png`) is opaque; the second
 * (`color_wheel.png`) is premultiplied.
 *
 * After the colour-space conversion, the image is reinterpreted back as
 * sRGB/sRGB linear so the re-tagged image is drawn under an sRGB/linear
 * glass. On Kanvas both operations are metadata-only (no pixel conversion).
 *
 * @see https://github.com/google/skia/blob/main/gm/makecolorspace.cpp
 */
class MakeColorSpaceGm : SkiaGm {
    override val name = "makecolorspace"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 384
    override val height = 512

    override fun draw(canvas: GmCanvas, width0: Int, height0: Int) {
        val wideGamut = ColorSpace("Wide Gamut (Display P3)", TransferFunction.SRGB, Gamut.DISPLAY_P3)
        val wideGamutLinear = ColorSpace("Linear Wide Gamut (Display P3)", TransferFunction.LINEAR, Gamut.DISPLAY_P3)

        val opaqueImage = loadImage("images/mandrill_128.png") ?: return
        val premulImage = loadImage("images/color_wheel.png") ?: return

        canvas.drawImage(opaqueImage, Rect(0f, 0f, 128f, 128f))
        drawColorSpace(canvas, opaqueImage, wideGamut, 128f, 0f)
        drawColorSpace(canvas, opaqueImage, wideGamutLinear, 256f, 0f)
        canvas.drawImage(premulImage, Rect(0f, 128f, 128f, 256f))
        drawColorSpace(canvas, premulImage, wideGamut, 128f, 128f)
        drawColorSpace(canvas, premulImage, wideGamutLinear, 256f, 128f)

        canvas.translate(0f, 256f)

        val opaqueRaster = loadImage("images/mandrill_128.png") ?: return
        val premulRaster = loadImage("images/color_wheel.png") ?: return

        canvas.drawImage(opaqueRaster, Rect(0f, 0f, 128f, 128f))
        drawColorSpace(canvas, opaqueRaster, wideGamut, 128f, 0f)
        drawColorSpace(canvas, opaqueRaster, wideGamutLinear, 256f, 0f)
        canvas.drawImage(premulRaster, Rect(0f, 128f, 128f, 256f))
        drawColorSpace(canvas, premulRaster, wideGamut, 128f, 128f)
        drawColorSpace(canvas, premulRaster, wideGamutLinear, 256f, 128f)
    }

    private fun drawColorSpace(
        canvas: GmCanvas,
        orig: Image,
        colorSpace: ColorSpace,
        x: Float,
        y: Float,
    ) {
        val xform = orig.reinterpretColorSpace(colorSpace)
        val srgb = if (colorSpace.transferFunction == TransferFunction.LINEAR) {
            ColorSpace.LINEAR_SRGB
        } else {
            ColorSpace.SRGB
        }
        val reinterpreted = xform.reinterpretColorSpace(srgb)
        canvas.drawImage(reinterpreted, Rect(x, y, x + 128f, y + 128f))
    }

    private fun loadImage(path: String): Image? {
        val bytes = this::class.java.classLoader?.getResourceAsStream(path)?.readBytes() ?: return null
        return Image.decode(bytes)
    }
}
