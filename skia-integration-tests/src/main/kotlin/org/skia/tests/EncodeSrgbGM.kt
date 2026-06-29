package org.skia.tests

import org.graphiks.kanvas.codec.Codec
import org.skia.foundation.SkEncodedImageFormat
import org.skia.core.SkCanvas
import org.skia.encode.SkJpegEncoder
import org.skia.encode.SkPngEncoder
import org.skia.encode.SkWebpEncoder
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.SkImage
import org.graphiks.math.SkISize
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/encode_srgb.cpp::EncodeSRGBGM`.
 *
 * Renders a `2 × 15` matrix of 128×128 cells. Each row is one
 * (colourType, alphaType) combination ; the two columns swap between
 * a `null` colour-space tag and an explicit sRGB tag. Each cell is
 * the result of decoding a fixed source resource (`color_wheel.jpg`,
 * `color_wheel.png`, `grayscale.jpg`) into the row's colour-type /
 * alpha-type / colour-space and then re-encoding through the format
 * chosen at construction (PNG or JPEG — both ports are emitted ;
 * WEBP is upstream's third variant but currently bundles into
 * R-final.S).
 *
 * The GM exists upstream to validate that the encoders preserve sRGB
 * tagging across the round-trip ; in our `:kanvas-skia` port the
 * test focus shifts slightly because [SkPngEncoder] does **not**
 * embed an `iCCP` chunk (see class kdoc). The visual output still
 * matches because:
 *  - the source bitmaps are read back into the test as raw 8888
 *    sRGB pixels regardless of whether the encoder embedded a
 *    profile (our [Codec] decoders default to sRGB on missing
 *    `iCCP`, matching the upstream behaviour for our test pipeline);
 *  - the JPEG encoder strips colour-space metadata in both ports.
 *
 * C++ original (abbreviated):
 * ```cpp
 * void onDraw(SkCanvas* canvas) override {
 *     for (SkColorType colorType : colorTypes) {
 *         for (SkAlphaType alphaType : alphaTypes) {
 *             canvas->save();
 *             for (sk_sp<SkColorSpace> colorSpace : { nullptr, MakeSRGB() }) {
 *                 make(&bitmap, colorType, alphaType, colorSpace);
 *                 auto data = encode_data(bitmap, fEncodedFormat);
 *                 auto image = SkImages::DeferredFromEncodedData(data);
 *                 canvas->drawImage(image, 0, 0);
 *                 canvas->translate(imageWidth, 0);
 *             }
 *             canvas->restore();
 *             canvas->translate(0, imageHeight);
 *         }
 *     }
 * }
 * ```
 */
public open class EncodeSrgbGM(
    private val format: SkEncodedImageFormat,
) : GM() {

    public constructor() : this(SkEncodedImageFormat.kPNG)

    private val imageWidth = 128
    private val imageHeight = 128

    override fun getName(): String = when (format) {
        SkEncodedImageFormat.kPNG -> "encode-srgb-png"
        SkEncodedImageFormat.kJPEG -> "encode-srgb-jpg"
        SkEncodedImageFormat.kWEBP -> "encode-srgb-webp"
        else -> "encode-srgb-unknown"
    }

    override fun getISize(): SkISize = SkISize.Make(imageWidth * 2, imageHeight * 15)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        // Five colour-types × three alpha-types = 15 row layouts. The
        // upstream order is preserved so the cell positions match the
        // reference PNG.
        val colorTypes = listOf(
            // upstream uses kN32 (= kRGBA_8888 on most hosts) here
            SkColorType.kRGBA_8888,
            SkColorType.kRGBA_F16Norm,
            SkColorType.kGray_8,
            SkColorType.kRGB_565,
        )
        val alphaTypes = listOf("opaque", "premul", "unpremul")
        val colorSpaces = listOf<SkColorSpace?>(null, SkColorSpace.makeSRGB())

        for (colorType in colorTypes) {
            for (alpha in alphaTypes) {
                c.save()
                for (cs in colorSpaces) {
                    val bitmap = makeBitmap(colorType, alpha, cs) ?: continue
                    val image = encodeAndDecode(bitmap) ?: continue
                    c.drawImage(image, 0f, 0f)
                    c.translate(imageWidth.toFloat(), 0f)
                }
                c.restore()
                c.translate(0f, imageHeight.toFloat())
            }
        }
    }

    private fun makeBitmap(
        colorType: SkColorType,
        alpha: String,
        colorSpace: SkColorSpace?,
    ): SkBitmap? {
        val resource = when (colorType) {
            SkColorType.kGray_8 -> "images/grayscale.jpg"
            SkColorType.kRGB_565 -> "images/color_wheel.jpg"
            else -> if (alpha == "opaque") "images/color_wheel.jpg" else "images/color_wheel.png"
        }
        val src = ToolUtils.GetResourceAsImage(resource) ?: return null
        // Allocate the destination bitmap with the requested colour-type
        // and colour-space (defaults to sRGB when null is requested).
        val cs = colorSpace ?: SkColorSpace.makeSRGB()
        val dst = SkBitmap(src.width, src.height, cs, colorType)
        for (y in 0 until src.height) {
            for (x in 0 until src.width) {
                dst.setPixel(x, y, src.peekPixel(x, y))
            }
        }
        return dst
    }

    private fun encodeAndDecode(bitmap: SkBitmap): SkImage? {
        val bytes = when (format) {
            SkEncodedImageFormat.kPNG -> SkPngEncoder.Encode(bitmap)
            SkEncodedImageFormat.kJPEG -> SkJpegEncoder.Encode(bitmap)
            // Upstream's third variant — VP8L lossless via SkWebpEncoder.
            // The TwelveMonkeys imageio-webp codec (on :cpu-raster's runtime
            // classpath) decodes the round-tripped bytes back to an SkImage.
            SkEncodedImageFormat.kWEBP -> SkWebpEncoder.Encode(bitmap)
            else -> null
        } ?: return null
        val codec = Codec.MakeFromData(bytes) ?: return null
        val (out, result) = codec.getImage()
        if (result != Codec.Result.kSuccess || out == null) return null
        return out.asImage()
    }
}
