package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/grayscalejpg.cpp::grayscalejpg`
 * (`DEF_SIMPLE_GM(grayscalejpg, canvas, 128, 128)`).
 *
 * Regression for [crbug.com/436079](https://crbug.com/436079) :
 * decoding a grayscale-encoded JPEG. The codec must promote the
 * single-channel grayscale samples back to RGB so the raster sink
 * can composite them as 8888.
 *
 * C++ original:
 * ```cpp
 * DEF_SIMPLE_GM(grayscalejpg, canvas, 128, 128) {
 *     const char kResource[] = "images/grayscale.jpg";
 *     sk_sp<SkImage> image(ToolUtils::GetResourceAsImage(kResource));
 *     if (image) {
 *         canvas->drawImage(image, 0.0f, 0.0f);
 *     } else {
 *         SkDebugf("\nCould not decode file '%s'. Did you forget"
 *                  " to set the resourcePath?\n", kResource);
 *     }
 * }
 * ```
 */
public class GrayscaleJpgGM : GM() {
    override fun getName(): String = "grayscalejpg"
    override fun getISize(): SkISize = SkISize.Make(128, 128)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val image = ToolUtils.GetResourceAsImage("images/grayscale.jpg") ?: return
        c.drawImage(image, 0f, 0f)
    }
}
