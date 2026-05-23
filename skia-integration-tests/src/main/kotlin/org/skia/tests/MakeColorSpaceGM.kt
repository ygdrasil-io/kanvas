package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.SkImage
import org.skia.foundation.skcms.SkNamedGamut
import org.skia.foundation.skcms.SkNamedTransferFn
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/makecolorspace.cpp::makecolorspace`
 * (`DEF_SIMPLE_GM_CAN_FAIL(makecolorspace, canvas, errorMsg, 128*3, 128*4)`).
 *
 * Draws a 3×4 grid of 128×128 images: two rows of "lazy" (codec-decoded)
 * images and two rows of raster images. Within each pair of rows the
 * columns are:
 *  - col 0: original image
 *  - col 1: wide-gamut (Adobe-RGB sRGB gamma) conversion via [SkImage.makeColorSpace]
 *  - col 2: wide-gamut linear conversion via [SkImage.makeColorSpace]
 *
 * The first source image (`mandrill_128.png`) is opaque; the second
 * (`color_wheel.png`) is premultiplied.
 *
 * After the colour-space conversion upstream calls
 * `xform->reinterpretColorSpace(srgb/srgbLinear)` so the re-tagged image
 * is drawn under an sRGB/linear glass — that API is unimplemented in
 * kanvas-skia (`STUB.IMAGE_REINTERPRET_COLOR_SPACE`). The GM body calls
 * `makeColorSpace` then `reinterpretColorSpace`; the body will throw on
 * the stub path, so the test is `@Disabled`.
 *
 * C++ original: `gm/makecolorspace.cpp` — `make_color_space` helper + GM body.
 */
public class MakeColorSpaceGM : GM() {
    override fun getName(): String = "makecolorspace"
    override fun getISize(): SkISize = SkISize.Make(128 * 3, 128 * 4)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val wideGamut = SkColorSpace.makeRGB(SkNamedTransferFn.kSRGB, SkNamedGamut.kAdobeRGB)
            ?: return
        val wideGamutLinear = wideGamut.makeLinearGamma()

        // Lazy (codec-decoded) images.
        val opaqueImage = ToolUtils.GetResourceAsImage("images/mandrill_128.png") ?: return
        val premulImage = ToolUtils.GetResourceAsImage("images/color_wheel.png") ?: return

        c.drawImage(opaqueImage, 0f, 0f)
        drawColorSpace(c, opaqueImage, wideGamut, 128f, 0f)
        drawColorSpace(c, opaqueImage, wideGamutLinear, 256f, 0f)
        c.drawImage(premulImage, 0f, 128f)
        drawColorSpace(c, premulImage, wideGamut, 128f, 128f)
        drawColorSpace(c, premulImage, wideGamutLinear, 256f, 128f)

        c.translate(0f, 256f)

        // Raster images (same pixels, same path on kanvas-skia CPU backend).
        val opaqueRaster = ToolUtils.GetResourceAsImage("images/mandrill_128.png") ?: return
        val premulRaster = ToolUtils.GetResourceAsImage("images/color_wheel.png") ?: return

        c.drawImage(opaqueRaster, 0f, 0f)
        drawColorSpace(c, opaqueRaster, wideGamut, 128f, 0f)
        drawColorSpace(c, opaqueRaster, wideGamutLinear, 256f, 0f)
        c.drawImage(premulRaster, 0f, 128f)
        drawColorSpace(c, premulRaster, wideGamut, 128f, 128f)
        drawColorSpace(c, premulRaster, wideGamutLinear, 256f, 128f)
    }

    /**
     * Mirrors upstream's `make_color_space` helper — converts [orig] to
     * [colorSpace] via [SkImage.makeColorSpace] then draws it at `(x, y)`.
     *
     * Upstream additionally calls `xform->reinterpretColorSpace(srgb)` after
     * the conversion so that the draw reads the converted pixels as if they
     * were sRGB — that step requires `STUB.IMAGE_REINTERPRET_COLOR_SPACE`
     * and will throw at runtime.
     */
    private fun drawColorSpace(
        canvas: SkCanvas,
        orig: SkImage,
        colorSpace: SkColorSpace,
        x: Float,
        y: Float,
    ) {
        val xform = orig.makeColorSpace(colorSpace) ?: return

        // Upstream reinterprets the xformed pixels as sRGB / sRGBLinear so the
        // GPU compositor renders the converted wide-gamut data under a standard
        // transfer function glass. This step throws on kanvas-skia — the test
        // is @Disabled.
        val srgb = if (colorSpace.gammaIsLinear()) {
            SkColorSpace.makeSRGBLinear()
        } else {
            SkColorSpace.makeSRGB()
        }
        @Suppress("UNUSED_VARIABLE")
        val reinterpreted = xform.reinterpretColorSpace(srgb) // throws STUB.IMAGE_REINTERPRET_COLOR_SPACE

        canvas.drawImage(reinterpreted, x, y)
    }
}
