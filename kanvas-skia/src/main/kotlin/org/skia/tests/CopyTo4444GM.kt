package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkColorType
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.math.SkISize
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/copy_to_4444.cpp::CopyTo4444GM`
 * (`DEF_GM(return new CopyTo4444GM;)`, name `"copyTo4444"`, 360 × 180).
 *
 * Decodes `images/dog.jpg` into an 8888 [SkBitmap], draws it at `(0, 0)`,
 * then converts to [SkColorType.kARGB_4444] storage and draws the
 * converted image to the right of the original.
 *
 * **Adaptation** — upstream's `ToolUtils::copy_to(&bm4444, kARGB_4444, bm)`
 * is a [SkBitmap.readPixels]-style call that performs an in-place
 * 8888 → 4444 colourtype conversion (with the rasterizer's automatic
 * dither during quantisation). `:kanvas-skia` doesn't expose a public
 * `bitmap-to-bitmap readPixels` of that shape, so we recreate the
 * conversion by drawing the 8888 image onto a fresh 4444-backed
 * [SkCanvas] with [SkBlendMode.kSrc] — the same pattern used by
 * [BitmapPremulGM.makeArgb4444Gradient]. No dither (our 4444 storage
 * is not dithered), which produces minor banding deltas vs upstream
 * on the dog's gradient background.
 *
 * C++ original :
 * ```cpp
 * class CopyTo4444GM : public skiagm::GM {
 *     SkString getName() const override { return SkString("copyTo4444"); }
 *     SkISize getISize() override { return {360, 180}; }
 *
 *     DrawResult onDraw(SkCanvas* canvas, SkString* errorMsg) override {
 *         SkBitmap bm, bm4444;
 *         if (!ToolUtils::GetResourceAsBitmap("images/dog.jpg", &bm)) {
 *             *errorMsg = "Could not decode the file. ...";
 *             return DrawResult::kFail;
 *         }
 *         canvas->drawImage(bm.asImage(), 0, 0);
 *
 *         // This should dither or we will see artifacts in the
 *         // background of the image.
 *         SkAssertResult(ToolUtils::copy_to(&bm4444, kARGB_4444_SkColorType, bm));
 *         canvas->drawImage(bm4444.asImage(), SkIntToScalar(bm.width()), 0);
 *         return DrawResult::kOk;
 *     }
 * };
 * DEF_GM( return new CopyTo4444GM; )
 * ```
 */
public class CopyTo4444GM : GM() {

    override fun getName(): String = "copyTo4444"

    override fun getISize(): SkISize = SkISize.Make(360, 180)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val image = ToolUtils.GetResourceAsImage("images/dog.jpg") ?: return

        c.drawImage(image, 0f, 0f, SkSamplingOptions.Default, null)

        val bm4444 = copyTo4444(image)
        c.drawImage(bm4444.asImage(), image.width.toFloat(), 0f, SkSamplingOptions.Default, null)
    }

    /**
     * Mirrors `ToolUtils::copy_to(dst, kARGB_4444_SkColorType, src)` —
     * returns a fresh [SkBitmap] in [SkColorType.kARGB_4444] containing
     * the pixels of [src] quantised to 4-bits-per-channel. We achieve
     * the conversion by drawing [src] into a 4444-backed [SkCanvas]
     * with [SkBlendMode.kSrc] (no dithering — our 4444 backing has no
     * dither path).
     */
    private fun copyTo4444(src: SkImage): SkBitmap {
        val info = SkImageInfo.Make(src.width, src.height, SkColorType.kARGB_4444, SkAlphaType.kPremul)
        val bm = SkBitmap.allocPixels(info)
        val paint = SkPaint().apply { blendMode = SkBlendMode.kSrc }
        SkCanvas(bm).drawImage(src, 0f, 0f, SkSamplingOptions.Default, paint)
        return bm
    }
}
