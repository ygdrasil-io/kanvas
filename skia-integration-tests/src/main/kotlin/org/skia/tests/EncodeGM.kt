package org.skia.tests

import org.graphiks.kanvas.codec.Codec
import org.skia.core.SkCanvas
import org.graphiks.kanvas.codec.jpeg.JpegEncoder
import org.graphiks.kanvas.codec.png.PngEncoder
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkFont
import org.skia.foundation.SkPaint
import org.graphiks.math.SkISize
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/encode.cpp::EncodeGM`.
 *
 * Loads `images/mandrill_512_q075.jpg`, re-encodes it twice — once as
 * PNG via [PngEncoder.encode], once as JPEG via [JpegEncoder.encode]
 * — then re-decodes both encoded blobs back into [SkImage]s and draws
 * them side-by-side at `(0, 0)` and `(512, 0)`. Below the images, the
 * legend "Images should look identical." is drawn with the portable
 * default font in `kAlias` edging.
 *
 * The GM exists to validate the encode → decode round-trip for the
 * PNG and JPEG encoders. PNG should be lossless ; JPEG-100 is
 * near-lossless. Differences from upstream come from the JPEG quality
 * setting — upstream uses `JpegEncoder::Options{}` which defaults to
 * `quality = 100` ; our [JpegEncoder.encode] default also chooses
 * `quality = 100`, so the encoded blobs round-trip with comparable
 * fidelity.
 *
 * C++ original:
 * ```cpp
 * void onDraw(SkCanvas* canvas) override {
 *     SkBitmap orig;
 *     ToolUtils::GetResourceAsBitmap("images/mandrill_512_q075.jpg", &orig);
 *     sk_sp<SkData> pngData = PngEncoder::encode(orig.pixmap(), {});
 *     sk_sp<SkData> jpgData = JpegEncoder::Encode(orig.pixmap(), {});
 *     sk_sp<SkImage> pngImage = SkImages::DeferredFromEncodedData(pngData);
 *     sk_sp<SkImage> jpgImage = SkImages::DeferredFromEncodedData(jpgData);
 *     canvas->drawImage(pngImage.get(), 0.0f, 0.0f);
 *     canvas->drawImage(jpgImage.get(), 512.0f, 0.0f);
 *     SkFont font = ToolUtils::DefaultPortableFont();
 *     font.setEdging(SkFont::Edging::kAlias);
 *     canvas->drawString("Images should look identical.", 450.0f, 550.0f, font, SkPaint());
 * }
 * ```
 */
public class EncodeGM : GM() {
    override fun getName(): String = "encode"
    override fun getISize(): SkISize = SkISize.Make(1024, 600)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        // Load source via GetResourceAsImage, then materialise as a
        // SkBitmap (our encoders work on SkBitmap, mirroring upstream's
        // pixmap-on-bitmap call site).
        val source = ToolUtils.GetResourceAsImage("images/mandrill_512_q075.jpg") ?: return
        val orig = SkBitmap(source.width, source.height)
        for (y in 0 until source.height) {
            for (x in 0 until source.width) {
                orig.setPixel(x, y, source.peekPixel(x, y))
            }
        }

        val pngData = PngEncoder.encode(orig) ?: return
        val jpgData = JpegEncoder.encode(orig) ?: return

        val pngImage = decodeImage(pngData) ?: return
        val jpgImage = decodeImage(jpgData) ?: return

        c.drawImage(pngImage, 0f, 0f)
        c.drawImage(jpgImage, 512f, 0f)

        val font = ToolUtils.DefaultPortableFont().apply { edging = SkFont.Edging.kAlias }
        c.drawString("Images should look identical.", 450f, 550f, font, SkPaint())
    }

    private fun decodeImage(bytes: ByteArray): org.skia.foundation.SkImage? {
        val codec = Codec.MakeFromData(bytes) ?: return null
        val (bitmap, result) = codec.getImage()
        if (result != Codec.Result.kSuccess || bitmap == null) return null
        return bitmap.asImage()
    }
}
