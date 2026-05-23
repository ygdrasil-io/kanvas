package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkImage
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/makecolorspace.cpp::reinterpretcolorspace`
 * (`DEF_SIMPLE_GM_CAN_FAIL(reinterpretcolorspace, canvas, errorMsg, 128*3, 128*3)`).
 *
 * Draws a 3×3 grid. Rows are lazy (codec-decoded), raster, and GPU (or a
 * second raster copy on kanvas-skia). Within each row the three columns are:
 *  - col 0: original image — should look normal
 *  - col 1: image re-tagged into the colour-spin space via
 *    `SkImage.reinterpretColorSpace(spin)` — in tagged (colour-managed) configs
 *    this rotates colours RGB → GBR; in untagged configs it looks identical to
 *    col 0
 *  - col 2: image converted to the spin space via `SkImage.makeColorSpace(spin)`
 *    then re-tagged back to sRGB via `reinterpretColorSpace(sRGB)` — tests the
 *    composition of the two APIs; should appear backwards-spun (RGB → BRG)
 *
 * On the CPU backend [SkImage.reinterpretColorSpace] is a metadata-only
 * rewrap: pixels are shared, but future draws read them under the new
 * colour-space tag.
 *
 * C++ original: `gm/makecolorspace.cpp` — `DEF_SIMPLE_GM_CAN_FAIL(reinterpretcolorspace…)`.
 */
public class ReinterpretColorSpaceGM : GM() {
    override fun getName(): String = "reinterpretcolorspace"
    override fun getISize(): SkISize = SkISize.Make(128 * 3, 128 * 3)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val srgb = SkColorSpace.makeSRGB()
        val spin = srgb.makeColorSpin()

        val image = ToolUtils.GetResourceAsImage("images/color_wheel.png") ?: return

        // Row 0 — lazy images.
        c.drawImage(image, 0f, 0f)
        // Throws STUB.IMAGE_REINTERPRET_COLOR_SPACE:
        c.drawImage(image.reinterpretColorSpace(spin), 128f, 0f)
        val spun = image.makeColorSpace(spin)
        if (spun != null) {
            // Throws STUB.IMAGE_REINTERPRET_COLOR_SPACE:
            c.drawImage(spun.reinterpretColorSpace(srgb), 256f, 0f)
        }

        c.translate(0f, 128f)

        // Row 1 — raster images (makeRasterImage is identity on kanvas-skia).
        val rasterImage = image.makeRasterImage()
        c.drawImage(rasterImage, 0f, 0f)
        // Throws STUB.IMAGE_REINTERPRET_COLOR_SPACE:
        c.drawImage(rasterImage.reinterpretColorSpace(spin), 128f, 0f)
        val rasterSpun = rasterImage.makeColorSpace(spin)
        if (rasterSpun != null) {
            // Throws STUB.IMAGE_REINTERPRET_COLOR_SPACE:
            c.drawImage(rasterSpun.reinterpretColorSpace(srgb), 256f, 0f)
        }

        c.translate(0f, 128f)

        // Row 2 — GPU images (same as raster on kanvas-skia; no GPU upload path).
        val gpuImage = image.makeRasterImage()
        c.drawImage(gpuImage, 0f, 0f)
        // Throws STUB.IMAGE_REINTERPRET_COLOR_SPACE:
        c.drawImage(gpuImage.reinterpretColorSpace(spin), 128f, 0f)
        val gpuSpun = gpuImage.makeColorSpace(spin)
        if (gpuSpun != null) {
            // Throws STUB.IMAGE_REINTERPRET_COLOR_SPACE:
            c.drawImage(gpuSpun.reinterpretColorSpace(srgb), 256f, 0f)
        }
    }
}
