package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.skcms.SkNamedGamut
import org.skia.foundation.skcms.SkNamedTransferFn
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/makecolorspace.cpp::makecolortypeandspace`
 * (`DEF_SIMPLE_GM_BG(makecolortypeandspace, canvas, 128*3, 128*4, SK_ColorWHITE)`).
 *
 * Draws a 3×4 grid. For two source images (`mandrill_128.png` and
 * `color_wheel.png`) it iterates twice — first with the lazy (codec-decoded)
 * image, then with a raster copy — and for each source draws:
 *  - col 0: the unmodified image
 *  - col 1: converted to RGB-565 in the Rec.2020 wide-gamut space via
 *    `SkImage.makeColorTypeAndColorSpace`
 *  - col 2: converted to Gray-8 in its original space via
 *    `SkImage.makeColorTypeAndColorSpace`
 *
 * kanvas-skia stores images internally as 8888 pixels, but
 * `makeColorTypeAndColorSpace` quantizes through the requested colour
 * type before tagging the output colour space, which is enough to cover
 * the RGB-565 and Gray-8 variants exercised here.
 *
 * C++ original: `gm/makecolorspace.cpp` — `DEF_SIMPLE_GM_BG(makecolortypeandspace…)`.
 */
public class MakeColorTypeAndSpaceGM : GM() {
    override fun getName(): String = "makecolortypeandspace"
    override fun getISize(): SkISize = SkISize.Make(128 * 3, 128 * 4)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val images = arrayOf(
            ToolUtils.GetResourceAsImage("images/mandrill_128.png"),
            ToolUtils.GetResourceAsImage("images/color_wheel.png"),
        )

        val rec2020 = SkColorSpace.makeRGB(SkNamedTransferFn.kSRGB, SkNamedGamut.kRec2020) ?: return

        // Iterate twice: lazy images then raster images (no-op distinction on
        // kanvas-skia's CPU backend — every SkImage is already raster-backed).
        for (lazy in listOf(true, false)) {
            for (j in images.indices) {
                val image = images[j] ?: continue

                // Unmodified.
                c.drawImage(image, 0f, 0f)

                // 565 in Rec.2020 wide-gamut.
                val image565 = image.makeColorTypeAndColorSpace(SkColorType.kRGB_565, rec2020)
                if (image565 != null) {
                    c.drawImage(image565, 128f, 0f)
                }

                // Gray-8 in the original space.
                val imageGray = image.makeColorTypeAndColorSpace(
                    SkColorType.kGray_8, image.colorSpace,
                )
                if (imageGray != null) {
                    c.drawImage(imageGray, 256f, 0f)
                }

                // On the GPU path upstream promotes lazy → texture here;
                // kanvas-skia's makeRasterImage() is a no-op identity.
                images[j] = image.makeRasterImage()

                c.translate(0f, 128f)
            }
        }
    }
}
