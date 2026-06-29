package org.skia.tests

import org.graphiks.kanvas.codec.Codec
import org.skia.foundation.SkEncodedImageFormat
import org.skia.core.SkCanvas
import org.graphiks.kanvas.codec.jpeg.JpegEncoder
import org.graphiks.kanvas.codec.png.PngEncoder
import org.skia.encode.SkWebpEncoder
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkImage
import org.graphiks.math.SkISize
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/encode_platform.cpp::EncodePlatformGM`.
 *
 * Lays out a 5-column × 3-row matrix of `256×256` images, each
 * column re-encoding the source through one of the supported
 * format / quality combinations (PNG-100, JPEG-100, WEBP-lossless,
 * WEBP-lossy-80, PNG-100 again — five entries, mirroring upstream's
 * `gRecs` table). Rows show the same encoded blob round-tripped
 * through three different source-pixel layouts : opaque (mandrill_256),
 * premul (yellow_rose subset), unpremul (yellow_rose subset, alpha
 * inverted from premul to unpremul). The GM exists to validate that
 * every encoder accepts every alpha layout without crashing or
 * returning bytes that decode wrong.
 *
 * The Kotlin port differs from upstream in two minor ways :
 *  1. Our [SkBitmap] doesn't track an alpha-type tag, so the "premul"
 *     and "unpremul" rows render the same source pixels — visually
 *     indistinguishable, matching upstream when the encode path is
 *     alpha-preserving (PNG, WEBP-lossless).
 *  2. WEBP-lossy ([SkWebpEncoder.Compression.kLossy]) is currently a
 *     documented unsupported path in `SUPPORTED_CODECS.md`, so the
 *     WEBP-lossy column short-circuits to a blank cell.
 *
 * C++ original:
 * ```cpp
 * static const struct {
 *     SkEncodedImageFormat format;
 *     int                  quality;
 * } gRecs[] = {
 *     { SkEncodedImageFormat::kPNG,  100},
 *     { SkEncodedImageFormat::kJPEG, 100},
 *     { SkEncodedImageFormat::kWEBP, 100}, // Lossless
 *     { SkEncodedImageFormat::kWEBP,  80}, // Lossy
 *     { SkEncodedImageFormat::kPNG,  100},
 * };
 *
 * DrawResult onDraw(SkCanvas* canvas, SkString* errorMsg) override {
 *     SkBitmap opaqueBm, premulBm, unpremulBm;
 *     ToolUtils::GetResourceAsBitmap("images/mandrill_256.png", &opaqueBm);
 *     SkBitmap tmp;
 *     ToolUtils::GetResourceAsBitmap("images/yellow_rose.png", &tmp);
 *     tmp.extractSubset(&premulBm, SkIRect::MakeWH(256, 256));
 *     unpremulBm.allocPixels(premulBm.info().makeAlphaType(kUnpremul_SkAlphaType));
 *     premulBm.readPixels(unpremulBm.pixmap());
 *
 *     for (const auto& rec : gRecs) {
 *         auto opaqueImage   = SkImages::DeferredFromEncodedData(encode_data(rec.format, opaqueBm,   rec.quality));
 *         auto premulImage   = SkImages::DeferredFromEncodedData(encode_data(rec.format, premulBm,   rec.quality));
 *         auto unpremulImage = SkImages::DeferredFromEncodedData(encode_data(rec.format, unpremulBm, rec.quality));
 *         canvas->drawImage(opaqueImage,   0, 0);
 *         canvas->drawImage(premulImage,   0, 256);
 *         canvas->drawImage(unpremulImage, 0, 512);
 *         canvas->translate(256, 0);
 *     }
 * }
 * ```
 */
public class EncodePlatformGM : GM() {

    private data class Rec(val format: SkEncodedImageFormat, val quality: Int)

    private val gRecs = listOf(
        Rec(SkEncodedImageFormat.kPNG, 100),
        Rec(SkEncodedImageFormat.kJPEG, 100),
        Rec(SkEncodedImageFormat.kWEBP, 100), // lossless
        Rec(SkEncodedImageFormat.kWEBP, 80),  // lossy (STUB → blank cell)
        Rec(SkEncodedImageFormat.kPNG, 100),
    )

    override fun getName(): String = "encode-platform"
    override fun getISize(): SkISize = SkISize.Make(256 * gRecs.size, 256 * 3)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        // Source : `mandrill_256.png` is a fully-opaque 256×256 PNG (no
        // alpha channel in the source bytes, but our codec returns 8888
        // unpremul ARGB with alpha = 255).
        val opaqueImg = ToolUtils.GetResourceAsImage("images/mandrill_256.png") ?: return
        val opaqueBm = imageToBitmap(opaqueImg)

        // Source : `yellow_rose.png` carries genuine alpha. Upstream
        // extracts a 256×256 subset starting at (0, 0); we do the same.
        val roseImg = ToolUtils.GetResourceAsImage("images/yellow_rose.png") ?: return
        val premulBm = SkBitmap(256, 256)
        val cropW = minOf(256, roseImg.width)
        val cropH = minOf(256, roseImg.height)
        for (y in 0 until cropH) {
            for (x in 0 until cropW) {
                premulBm.setPixel(x, y, roseImg.peekPixel(x, y))
            }
        }
        // Our SkBitmap doesn't tag a per-bitmap alpha-type, so the
        // "unpremul" row uses the same pixel buffer (matches the visual
        // upstream emits whenever the encoder preserves alpha verbatim).
        val unpremulBm = premulBm

        var x = 0f
        for (rec in gRecs) {
            drawIfDecodable(c, encodeData(rec.format, opaqueBm, rec.quality), x, 0f)
            drawIfDecodable(c, encodeData(rec.format, premulBm, rec.quality), x, 256f)
            drawIfDecodable(c, encodeData(rec.format, unpremulBm, rec.quality), x, 512f)
            x += 256f
        }
    }

    private fun encodeData(format: SkEncodedImageFormat, bitmap: SkBitmap, quality: Int): ByteArray? {
        return when (format) {
            SkEncodedImageFormat.kPNG -> PngEncoder.encode(bitmap)
            SkEncodedImageFormat.kJPEG -> JpegEncoder.encode(
                bitmap, JpegEncoder.Options(quality = quality),
            )
            SkEncodedImageFormat.kWEBP -> {
                val compression = if (quality >= 100) {
                    SkWebpEncoder.Compression.kLossless
                } else {
                    SkWebpEncoder.Compression.kLossy
                }
                SkWebpEncoder.Encode(
                    bitmap,
                    SkWebpEncoder.Options(compression = compression, quality = quality.toFloat()),
                )
            }
            else -> null
        }
    }

    private fun drawIfDecodable(canvas: SkCanvas, bytes: ByteArray?, x: Float, y: Float) {
        val img = decodeImage(bytes ?: return) ?: return
        canvas.drawImage(img, x, y)
    }

    private fun decodeImage(bytes: ByteArray): SkImage? {
        val codec = Codec.MakeFromData(bytes) ?: return null
        val (bitmap, result) = codec.getImage()
        if (result != Codec.Result.kSuccess || bitmap == null) return null
        return bitmap.asImage()
    }

    private fun imageToBitmap(image: SkImage): SkBitmap {
        val bm = SkBitmap(image.width, image.height)
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                bm.setPixel(x, y, image.peekPixel(x, y))
            }
        }
        return bm
    }
}
