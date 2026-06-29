package org.skia.tests

import org.graphiks.math.SkISize
import org.graphiks.kanvas.codec.Codec
import org.skia.core.SkCanvas
import org.skia.encode.SkJpegEncoder
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorType
import org.skia.foundation.SkImage
import org.skia.tools.ToolUtils

/**
 * Port of Skia's
 * [`gm/encode_alpha_jpeg.cpp::EncodeJpegAlphaOptsGM`](https://github.com/google/skia/blob/main/gm/encode_alpha_jpeg.cpp)
 * (400 × 200).
 *
 * Exercises [SkJpegEncoder.Options.alphaOption] ([SkJpegEncoder.AlphaOption.kIgnore]
 * vs [SkJpegEncoder.AlphaOption.kBlendOnBlack]) — JPEG itself has no
 * alpha channel, so the encoder either drops alpha entirely or composites
 * the source onto a black background before encoding.
 *
 * Upstream lays out a 4 × 2 grid : the four columns vary the source
 * pixmap's `(colorType, alphaType)` pairing — `(N32, Premul)`,
 * `(N32, Unpremul)`, `(F16, Premul)`, `(F16, Unpremul)` — and the two
 * rows vary the alpha option. The N32 / F16 axis is meant to probe the
 * libjpeg-turbo direct-source-encode path (which differs by source bit
 * depth) and the premul / unpremul axis the explicit unpremul step the
 * encoder applies before chroma conversion.
 *
 * **Kanvas-skia adaptation** :
 *  - [SkJpegEncoder] always reads pixels through [SkBitmap.getPixel],
 *    which yields non-premultiplied 8-bit ARGB regardless of the
 *    source's storage layout. The premul / unpremul axis therefore
 *    collapses (the two columns within a colour-type group are
 *    bit-identical).
 *  - The colour-type axis is preserved : columns 1-2 use a
 *    [SkColorType.kRGBA_8888] intermediate, columns 3-4 use
 *    [SkColorType.kRGBA_F16Norm]. F16 carries ~5 extra mantissa bits
 *    per channel, but the JPEG quantizer rounds them to 8-bit before
 *    chroma conversion — visible differences vs the 8888 columns are
 *    sub-quantizer-step.
 *
 * A second port of the same upstream GM (`EncodeAlphaJpegGM`) lives in
 * this package — that one collapses all four columns onto the same
 * 8888 source. This class keeps the F16 intermediate to track the
 * upstream column layout more faithfully ; both are kept registered so
 * the cross-backend ratchet sees both shapes.
 *
 * C++ original (excerpt) :
 * ```cpp
 * DrawResult onDraw(SkCanvas* canvas, SkString* errorMsg) override {
 *     sk_sp<SkImage> srcImg = ToolUtils::GetResourceAsImage("images/rainbow-gradient.png");
 *     if (!srcImg) { return DrawResult::kFail; }
 *     fStorage.reset(srcImg->width() * srcImg->height() *
 *             SkColorTypeBytesPerPixel(kRGBA_F16_SkColorType));
 *
 *     SkPixmap src;
 *     SkImageInfo info = SkImageInfo::MakeN32Premul(srcImg->width(), srcImg->height(),
 *             canvas->imageInfo().colorSpace() ? SkColorSpace::MakeSRGB() : nullptr);
 *     read_into_pixmap(&src, info, fStorage.get(), srcImg);
 *     auto img0 = encode_pixmap_and_make_image(src, SkJpegEncoder::AlphaOption::kIgnore);
 *     auto img1 = encode_pixmap_and_make_image(src, SkJpegEncoder::AlphaOption::kBlendOnBlack);
 *     canvas->drawImage(img0, 0.0f, 0.0f);
 *     canvas->drawImage(img1, 0.0f, 100.0f);
 *     // …repeat for unpremul / F16 premul / F16 unpremul …
 *     return DrawResult::kOk;
 * }
 * ```
 */
public class EncodeJpegAlphaOptsGM : GM() {

    override fun getName(): String = "encode-alpha-jpeg"
    override fun getISize(): SkISize = SkISize.Make(400, 200)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val srcImg = ToolUtils.GetResourceAsImage("images/rainbow-gradient.png") ?: return

        // Two source bitmaps mirroring upstream's two colour-type
        // intermediates — 8888 and F16. Each holds the same RGBA
        // gradient ; the storage shape is what matters for the encoder.
        val src8888 = bitmapFromImage(srcImg, SkColorType.kRGBA_8888)
        val srcF16 = bitmapFromImage(srcImg, SkColorType.kRGBA_F16Norm)

        // Column → source bitmap mapping. The two "premul vs unpremul"
        // duplicates within each colour-type group collapse onto the
        // same bitmap (see KDoc — kanvas-skia's encoder always reads
        // non-premultiplied through SkBitmap.getPixel).
        val sources = arrayOf(src8888, src8888, srcF16, srcF16)

        for ((column, bitmap) in sources.withIndex()) {
            val x = column * 100f
            val ignore = encodeAndDecode(bitmap, SkJpegEncoder.AlphaOption.kIgnore) ?: continue
            val blend = encodeAndDecode(bitmap, SkJpegEncoder.AlphaOption.kBlendOnBlack) ?: continue
            c.drawImage(ignore, x, 0f)
            c.drawImage(blend, x, 100f)
        }
    }

    /**
     * Build an [SkBitmap] of the requested [colorType] holding the
     * pixels of [src]. Mirrors upstream's `read_into_pixmap` helper —
     * the source image is copied pixel-by-pixel into a buffer whose
     * memory layout matches [colorType]. Falls back to a per-pixel
     * walk because [SkImage.readPixels] doesn't currently support the
     * F16 destination ([SkPixmap] supports only the 4 8-bit colour
     * types).
     */
    private fun bitmapFromImage(src: SkImage, colorType: SkColorType): SkBitmap {
        val out = SkBitmap(src.width, src.height, colorType = colorType)
        for (y in 0 until src.height) {
            for (x in 0 until src.width) {
                out.setPixel(x, y, src.peekPixel(x, y))
            }
        }
        return out
    }

    /**
     * Mirrors upstream's `encode_pixmap_and_make_image` : encode [src]
     * as JPEG with the requested [alphaOption], then decode the bytes
     * back into a fresh [SkImage]. Returns `null` if either leg of the
     * round-trip fails — the upstream contract is "skip the cell" for
     * codec errors.
     */
    private fun encodeAndDecode(
        src: SkBitmap,
        alphaOption: SkJpegEncoder.AlphaOption,
    ): SkImage? {
        val bytes = SkJpegEncoder.Encode(
            src,
            SkJpegEncoder.Options(alphaOption = alphaOption),
        ) ?: return null
        val codec = Codec.MakeFromData(bytes) ?: return null
        val (bitmap, result) = codec.getImage()
        if (result != Codec.Result.kSuccess || bitmap == null) return null
        return bitmap.asImage()
    }
}
