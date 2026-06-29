package org.skia.tests

import org.graphiks.kanvas.codec.SkCodec
import org.skia.core.SkCanvas
import org.skia.encode.SkJpegEncoder
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkImage
import org.graphiks.math.SkISize
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/encode_alpha_jpeg.cpp::EncodeJpegAlphaOptsGM`.
 *
 * Loads `images/rainbow-gradient.png` and encodes it through
 * [SkJpegEncoder] eight times at the four corners of a 4×2 grid :
 * the columns vary the source pixel layout (8888 premul, 8888
 * unpremul, F16 premul, F16 unpremul) and the rows vary the
 * [SkJpegEncoder.Options.alphaOption] dispatch ([AlphaOption.kIgnore]
 * on top, [AlphaOption.kBlendOnBlack] on the bottom).
 *
 * The "premul vs unpremul" axis exists in upstream because libjpeg-turbo
 * processes the source bytes directly — a premultiplied source needs an
 * explicit unpremul step before the chroma conversion. Our ImageIO path
 * always reads through [SkBitmap.getPixel] (non-premul ARGB), which is
 * effectively an "unpremul-then-encode" pipeline regardless of the
 * source's storage layout, so the two columns within a colour-type
 * group end up bit-identical. The visual difference between the two
 * colour-type groups is also small : F16 carries ~5 extra mantissa
 * bits per channel, but the JPEG quantizer already rounds them to the
 * nearest 8-bit value before chroma conversion, so the encoded
 * bitstreams differ at most by a quantizer step. The reference PNG
 * captures all 8 cells from the upstream libjpeg-turbo encoder ;
 * pixel similarity stays in the JPEG-comparison band (~80 %).
 *
 * C++ original:
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
 *
 *     auto img0 = encode_pixmap_and_make_image(src, SkJpegEncoder::AlphaOption::kIgnore);
 *     auto img1 = encode_pixmap_and_make_image(src, SkJpegEncoder::AlphaOption::kBlendOnBlack);
 *     canvas->drawImage(img0, 0.0f, 0.0f);
 *     canvas->drawImage(img1, 0.0f, 100.0f);
 *
 *     // …repeat 3 more times for the unpremul / F16 premul / F16 unpremul pixmaps…
 *     return DrawResult::kOk;
 * }
 * ```
 */
public class EncodeAlphaJpegGM : GM() {

    override fun getName(): String = "encode-alpha-jpeg"
    override fun getISize(): SkISize = SkISize.Make(400, 200)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val srcImg = ToolUtils.GetResourceAsImage("images/rainbow-gradient.png") ?: return

        // Source bitmap (kRGBA_8888 / unpremul / sRGB) — kanvas-skia's
        // SkBitmap doesn't yet expose a per-bitmap alpha-type or F16
        // round-trip, so the four columns upstream (8888 premul, 8888
        // unpremul, F16 premul, F16 unpremul) all collapse onto the
        // same source pixel set. The four x-translations are still
        // applied so the layout matches the reference PNG cell layout.
        val src = SkBitmap(srcImg.width, srcImg.height)
        for (y in 0 until srcImg.height) {
            for (x in 0 until srcImg.width) {
                src.setPixel(x, y, srcImg.peekPixel(x, y))
            }
        }

        val xCoords = floatArrayOf(0f, 100f, 200f, 300f)
        for (xLeft in xCoords) {
            val img0 = encodeAndDecode(src, SkJpegEncoder.AlphaOption.kIgnore) ?: continue
            val img1 = encodeAndDecode(src, SkJpegEncoder.AlphaOption.kBlendOnBlack) ?: continue
            c.drawImage(img0, xLeft, 0f)
            c.drawImage(img1, xLeft, 100f)
        }
    }

    /**
     * Encode [src] as JPEG with the given [alphaOption], decode the
     * blob back into an [SkImage], and return it. Returns `null` if
     * the encode or decode fails.
     */
    private fun encodeAndDecode(src: SkBitmap, alphaOption: SkJpegEncoder.AlphaOption): SkImage? {
        val bytes = SkJpegEncoder.Encode(
            src,
            SkJpegEncoder.Options(alphaOption = alphaOption),
        ) ?: return null
        val codec = SkCodec.MakeFromData(bytes) ?: return null
        val (bitmap, result) = codec.getImage()
        if (result != SkCodec.Result.kSuccess || bitmap == null) return null
        return bitmap.asImage()
    }
}
