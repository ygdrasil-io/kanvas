package org.skia.codec.png

import org.skia.codec.SkCodec
import org.skia.foundation.SkEncodedImageFormat
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.SkImageInfo
import org.skia.foundation.skcms.SkcmsICCProfile
import org.skia.foundation.skcms.skcmsParse
import java.awt.image.BufferedImage
import java.awt.image.DataBufferUShort
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.util.zip.Inflater
import javax.imageio.ImageIO

/**
 * PNG decoder — the D3.1 implementation of the [SkCodec] facade.
 *
 * Mirrors Skia's
 * [`SkPngDecoder`](https://github.com/google/skia/blob/main/include/codec/SkPngDecoder.h),
 * but instead of binding to libpng we delegate the bitstream parse to
 * the JVM's built-in [javax.imageio.ImageIO]. That keeps the LOC budget
 * of the slice manageable (~400) and lets us reuse the heavily-tested
 * Java PNG reader for the long tail of PNG colour types — paletted,
 * gray, alpha+RGB, 8-bit and 16-bit-per-channel.
 *
 * Two responsibilities live here that the JVM stack does not give us
 * for free :
 *
 *  1. **ICC profile extraction** — `ImageIO.read` silently *applies*
 *     the embedded `iCCP` chunk during decode, baking the conversion
 *     into 8-bit pixels and dropping the profile. The DM reference
 *     PNGs in `kanvas-skia/src/test/resources/original-888/` are
 *     tagged "DM unified Rec.2020", and we want the working colour
 *     space preserved verbatim. So we walk the encoded bytes ourselves
 *     and pull the iCCP chunk before handing the rest off to ImageIO.
 *
 *  2. **16-bit-per-channel preservation** — when ImageIO does see a
 *     ≥16-bpc PNG, it stores the raw samples in a `DataBufferUShort`,
 *     but the convenient `BufferedImage::getRGB` accessor truncates to
 *     8 bits. We bypass `getRGB` and read the raster directly, which
 *     gives us the full precision the F16 working space needs.
 *
 * **Validation strategy** : `original-888/<name>.png` references decoded
 * by this codec must round-trip pixel-identical to the legacy
 * `BufferedImage`-based loader (which the `TestUtils.loadReferenceBitmap`
 * call site previously implemented inline). The matching test lives at
 * `kanvas-skia/src/test/kotlin/org/skia/codec/png/SkPngCodecTest.kt`.
 */
public class SkPngCodec internal constructor(
    private val image: BufferedImage,
    private val iccProfile: SkcmsICCProfile?,
) : SkCodec() {

    private val cachedInfo: SkImageInfo by lazy { computeInfo() }

    private fun computeInfo(): SkImageInfo {
        val cs = iccProfile?.let { SkColorSpace.make(it) } ?: SkColorSpace.makeSRGB()
        val highBitDepth = image.raster.dataBuffer is DataBufferUShort &&
            image.raster.numBands >= 3
        return if (highBitDepth) {
            SkImageInfo.Make(
                width = image.width,
                height = image.height,
                colorType = SkColorType.kRGBA_F16Norm,
                alphaType = SkAlphaType.kPremul,
                colorSpace = cs,
            )
        } else {
            SkImageInfo.Make(
                width = image.width,
                height = image.height,
                colorType = SkColorType.kRGBA_8888,
                alphaType = SkAlphaType.kUnpremul,
                colorSpace = cs,
            )
        }
    }

    override fun getInfo(): SkImageInfo = cachedInfo

    override fun getEncodedFormat(): SkEncodedImageFormat = SkEncodedImageFormat.kPNG

    override fun getICCProfile(): SkcmsICCProfile? = iccProfile

    override fun getPixels(info: SkImageInfo, dst: SkBitmap): Result {
        if (dst.width != info.width || dst.height != info.height) {
            return Result.kInvalidParameters
        }
        if (dst.colorType != info.colorType) {
            return Result.kInvalidParameters
        }
        return when (info.colorType) {
            SkColorType.kRGBA_F16Norm -> decodeF16(dst)
            SkColorType.kRGBA_8888 -> decode8888(dst)
            else -> Result.kInvalidConversion
        }
    }

    /**
     * Decode a 16-bit-per-channel PNG into [dst]'s premul F16 storage.
     * Reads the raster sample-by-sample to skip ImageIO's 8-bit
     * `getRGB` quantisation and the embedded ICC profile application.
     */
    private fun decodeF16(dst: SkBitmap): Result {
        val raster = image.raster
        if (raster.dataBuffer !is DataBufferUShort || raster.numBands < 3) {
            return Result.kInvalidConversion
        }
        val numBands = raster.numBands
        val pixel = IntArray(numBands)
        val out = dst.pixelsF16
        val inv65535 = 1f / 65535f
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                raster.getPixel(x, y, pixel)
                val r = pixel[0] * inv65535
                val g = pixel[1] * inv65535
                val b = pixel[2] * inv65535
                val a = if (numBands >= 4) pixel[3] * inv65535 else 1f
                val o = (y * image.width + x) * 4
                // F16 storage is premultiplied (matches Skia's
                // `kRGBA_F16Norm` convention).
                out[o] = r * a
                out[o + 1] = g * a
                out[o + 2] = b * a
                out[o + 3] = a
            }
        }
        return Result.kSuccess
    }

    /**
     * Decode an 8-bit PNG (or one without high-precision samples) into
     * [dst]'s 8888 ARGB storage. [BufferedImage.getRGB] is documented
     * to return pixels in the default `TYPE_INT_ARGB` model regardless
     * of the underlying buffer type, and the values are **non-premul**
     * — exactly what `kRGBA_8888` expects. Going through `Graphics2D`
     * to materialise a `TYPE_INT_ARGB` intermediate (the legacy code
     * path) silently round-trips through the source-over compositor,
     * which can perturb bytes by 1-2 ulps on translucent pixels — see
     * the `8-bit decode preserves pixel values` test.
     */
    private fun decode8888(dst: SkBitmap): Result {
        image.getRGB(0, 0, image.width, image.height, dst.pixels, 0, image.width)
        return Result.kSuccess
    }

    /**
     * D3.1 registration record. Adheres to upstream's
     * `SkPngDecoder::Decoder()` factory pattern — the codec sniffs PNG
     * magic, parses iCCP (best-effort), then delegates to ImageIO. A
     * failure at any step returns `null` so the [SkCodec.MakeFromData]
     * dispatcher can fall through to the next registered format.
     */
    internal companion object Decoder : SkCodec.Decoder {

        override val name: String = "png"

        /**
         * The 8-byte PNG signature. Per the W3C spec
         * [§5.2 PNG signature](https://www.w3.org/TR/png-3/#5PNG-file-signature),
         * a valid PNG always starts with these bytes — `0x89` flags the
         * file as binary, `PNG` is the format name, and `\r\n\x1a\n`
         * detects line-ending mangling and accidental EOF interception.
         */
        private val PNG_SIGNATURE = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        )

        override fun matches(data: ByteArray): Boolean {
            if (data.size < PNG_SIGNATURE.size) return false
            for (i in PNG_SIGNATURE.indices) {
                if (data[i] != PNG_SIGNATURE[i]) return false
            }
            return true
        }

        override fun make(data: ByteArray): SkCodec? {
            val image = try {
                ImageIO.read(ByteArrayInputStream(data))
            } catch (_: Throwable) {
                null
            } ?: return null
            val profile = extractIccProfile(data)?.let { skcmsParse(it) }
            return SkPngCodec(image, profile)
        }

        /**
         * Walk a PNG looking for the `iCCP` chunk and return its
         * inflated ICC profile bytes, or `null` if the PNG has no ICC
         * profile / the chunk is malformed.
         *
         * Per the PNG spec, ancillary chunks before `IDAT` carry
         * metadata ; an `iCCP` chunk encodes
         * `<profile name>\0<compression method byte><deflate-compressed ICC>`,
         * so we strip the name + 2-byte preamble and inflate the rest.
         * We bail out as soon as we hit the first `IDAT` (no ICC
         * profile is permitted after the pixel data starts).
         */
        private fun extractIccProfile(pngBytes: ByteArray): ByteArray? {
            if (pngBytes.size < 8) return null
            for (i in PNG_SIGNATURE.indices) {
                if (pngBytes[i] != PNG_SIGNATURE[i]) return null
            }
            val dis = DataInputStream(pngBytes.inputStream())
            dis.skipBytes(8)
            while (dis.available() > 0) {
                val length = dis.readInt()
                val typeBytes = ByteArray(4).also { dis.readFully(it) }
                val type = String(typeBytes, Charsets.US_ASCII)
                val data = ByteArray(length).also { dis.readFully(it) }
                dis.readInt() // CRC, unused here.
                if (type == "iCCP") {
                    var nameEnd = 0
                    while (nameEnd < data.size && data[nameEnd] != 0.toByte()) nameEnd++
                    // skip name + null terminator + compression method byte
                    if (nameEnd + 2 > data.size) return null
                    val compressed = data.copyOfRange(nameEnd + 2, data.size)
                    val inflater = Inflater()
                    inflater.setInput(compressed)
                    val out = ByteArray(64 * 1024)
                    val len = inflater.inflate(out)
                    inflater.end()
                    return out.copyOfRange(0, len)
                } else if (type == "IDAT") {
                    return null
                }
            }
            return null
        }
    }
}
