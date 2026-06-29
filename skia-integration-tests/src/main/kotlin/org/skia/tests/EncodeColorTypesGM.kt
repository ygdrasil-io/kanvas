package org.skia.tests

import org.graphiks.kanvas.codec.Codec
import org.skia.foundation.SkEncodedImageFormat
import org.skia.core.SkCanvas
import org.skia.encode.SkJpegEncoder
import org.skia.encode.SkPngEncoder
import org.skia.encode.SkWebpEncoder
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorType
import org.skia.foundation.SkImage
import org.graphiks.math.SkISize
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/encode_color_types.cpp::EncodeColorTypesGM`.
 *
 * The upstream variant pair (`encode-color-types-webp-lossless` /
 * `encode-color-types-webp-lossy`) exercises the WEBP encoder across
 * three alpha layouts (opaque / premul / unpremul). The Kotlin port
 * routes through the same encoders R-final.6 ships :
 *  - `webp-lossless` re-uses the pure-Kotlin lossless WEBP emitter
 *    (R-suivi.23) ;
 *  - `webp-lossy` is a STUB (returns `null` per [SkWebpEncoder] kdoc) —
 *    cells short-circuit to blanks until R-final.S binds a real lossy
 *    encoder.
 *
 * The classes uses [Variant.kNormal] only ([Variant.kOpaque] /
 * [Variant.kGray] are kept for source compat with the upstream
 * 3-variant DEF_GM macro but currently emit no cells — they would
 * need GPU surface hooks to recreate the upstream colourtype
 * dispatch).
 *
 * C++ original (abbreviated):
 * ```cpp
 * for (SkAlphaType alphaType : {kOpaque, kPremul, kUnpremul}) {
 *     auto src = make_image(colorType, alphaType);
 *     SkWebpEncoder::Options options;
 *     if (fQuality < 100) {
 *         options.fCompression = SkWebpEncoder::Compression::kLossy;
 *         options.fQuality = fQuality;
 *     } else {
 *         options.fCompression = SkWebpEncoder::Compression::kLossless;
 *         options.fQuality = 70;
 *     }
 *     auto data = SkWebpEncoder::Encode(nullptr, src.get(), options);
 *     auto decoded = SkImages::DeferredFromEncodedData(data);
 *     canvas->drawImage(src, 0, 0);
 *     canvas->translate(imageWidth, 0);
 *     canvas->drawImage(decoded, 0, 0);
 *     canvas->translate(imageWidth * 1.5, 0);
 * }
 * ```
 */
public open class EncodeColorTypesGM(
    private val format: SkEncodedImageFormat,
    private val quality: Int,
    private val variant: Variant,
    private val variantName: String,
) : GM() {

    public enum class Variant {
        /** Single-pair opaque image. */
        kOpaque,

        /** Single-pair grayscale image. */
        kGray,

        /** Opaque pair followed by premul + unpremul pairs. */
        kNormal,
    }

    public constructor() : this(
        SkEncodedImageFormat.kWEBP, 100, Variant.kNormal, "webp-lossless",
    )

    private val imageWidth = 128
    private val imageHeight = 128

    override fun getName(): String {
        val variantPrefix = when (variant) {
            Variant.kOpaque -> "opaque-"
            Variant.kGray -> "gray-"
            Variant.kNormal -> ""
        }
        return "encode-${variantPrefix}color-types-$variantName"
    }

    override fun getISize(): SkISize {
        val cells = if (variant == Variant.kNormal) 7 else 2
        return SkISize.Make(imageWidth * cells, imageHeight)
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        // Skia upstream gates the variant on the destination canvas
        // colour-type. Our SkCanvas always renders into 8888 (the
        // raster sink's working format), which only matches the
        // [Variant.kNormal] branch (`kRGBA_8888_SkColorType` ∈
        // upstream's allowed set). The other variants short-circuit
        // to a no-op — same outcome upstream produces for any
        // canvas-colourtype mismatch.
        if (variant != Variant.kNormal) return

        // [Variant.kNormal] iterates over (opaque, premul, unpremul) ;
        // each draws (src, decoded(src)) at successive x offsets. Our
        // SkBitmap doesn't tag a per-bitmap alpha-type, so all three
        // alpha branches share the same source pixels — the visual
        // output matches upstream whenever the encoder preserves
        // alpha verbatim (lossless WEBP, PNG).
        val alphaCount = 3
        for (i in 0 until alphaCount) {
            val src = makeImage(SkColorType.kRGBA_8888) ?: break
            val data = encode(src) ?: continue
            val decoded = decodeImage(data) ?: continue
            c.drawImage(src.asImage(), 0f, 0f)
            c.translate(imageWidth.toFloat(), 0f)
            c.drawImage(decoded, 0f, 0f)
            c.translate(imageWidth * 1.5f, 0f)
        }
    }

    private fun makeImage(colorType: SkColorType): SkBitmap? {
        val resource = when (colorType) {
            SkColorType.kGray_8 -> "images/grayscale.jpg"
            SkColorType.kRGB_565 -> "images/color_wheel.jpg"
            else -> "images/color_wheel.jpg"
        }
        val src = ToolUtils.GetResourceAsImage(resource) ?: return null
        val bm = SkBitmap(src.width, src.height)
        for (y in 0 until src.height) {
            for (x in 0 until src.width) {
                bm.setPixel(x, y, src.peekPixel(x, y))
            }
        }
        return bm
    }

    private fun encode(bitmap: SkBitmap): ByteArray? = when (format) {
        SkEncodedImageFormat.kPNG -> SkPngEncoder.Encode(bitmap)
        SkEncodedImageFormat.kJPEG -> SkJpegEncoder.Encode(
            bitmap, SkJpegEncoder.Options(quality = quality),
        )
        SkEncodedImageFormat.kWEBP -> {
            val compression = if (quality < 100) {
                SkWebpEncoder.Compression.kLossy
            } else {
                SkWebpEncoder.Compression.kLossless
            }
            val webpQuality = if (quality < 100) quality.toFloat() else 70f
            SkWebpEncoder.Encode(
                bitmap,
                SkWebpEncoder.Options(compression = compression, quality = webpQuality),
            )
        }
        else -> null
    }

    private fun decodeImage(bytes: ByteArray): SkImage? {
        val codec = Codec.MakeFromData(bytes) ?: return null
        val (bitmap, result) = codec.getImage()
        if (result != Codec.Result.kSuccess || bitmap == null) return null
        return bitmap.asImage()
    }
}
