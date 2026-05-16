package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/make_raster_image.cpp::makeRasterImage`
 * (`DEF_SIMPLE_GM(makeRasterImage, canvas, 128, 128)` — note the
 * camelCase registered name, which differs from the snake_case
 * filename).
 *
 * Loads `images/color_wheel.png` and immediately materialises a
 * raster-backed copy via `SkImage::makeRasterImage(nullptr)`. On the
 * upstream Ganesh path this forces a lazy / texture-backed image to
 * be read back into CPU memory ; on the kanvas-skia CPU sink every
 * [org.skia.foundation.SkImage] is already raster-backed (the codec
 * decodes straight into an [org.skia.foundation.SkBitmap]), so the
 * GM degenerates to "draw the decoded image at (0, 0)".
 *
 * C++ original:
 * ```cpp
 * DEF_SIMPLE_GM(makeRasterImage, canvas, 128, 128) {
 *     if (auto img = ToolUtils::GetResourceAsImage("images/color_wheel.png")) {
 *         canvas->drawImage(img->makeRasterImage(nullptr), 0, 0);
 *     }
 * }
 * ```
 */
public class MakeRasterImageGM : GM() {
    override fun getName(): String = "makeRasterImage"
    override fun getISize(): SkISize = SkISize.Make(128, 128)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val img = ToolUtils.GetResourceAsImage("images/color_wheel.png") ?: return
        // makeRasterImage is a no-op on the kanvas-skia CPU sink — img is
        // already a raster image (codec decoded into a raster bitmap).
        c.drawImage(img, 0f, 0f)
    }
}
