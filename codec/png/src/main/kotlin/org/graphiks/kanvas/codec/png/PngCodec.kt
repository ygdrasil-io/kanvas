package org.graphiks.kanvas.codec.png

import org.graphiks.kanvas.codec.CodecDecoderProvider
import org.graphiks.kanvas.codec.Codec
import org.graphiks.kanvas.color.ColorModel
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.SkEncodedImageFormat
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkICC
import org.skia.foundation.skcms.SkNamedGamut
import org.skia.foundation.skcms.SkNamedTransferFn
import org.skia.foundation.skcms.SkcmsICCProfile
import org.skia.foundation.skcms.skcmsParse
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.DataFormatException
import java.util.zip.Inflater

/**
 * First pure-Kotlin PNG decoder slice.
 *
 * Supports the baseline PNG path used by small fixtures and many generated
 * assets: non-interlaced and Adam7-interlaced 8-bit grayscale (colour type 0),
 * 8-bit RGB (colour type 2), indexed colour (colour type 3, bit depths
 * 1/2/4/8), 8-bit grayscale+alpha (colour type 4), RGBA (colour type 6), and
 * 16-bit grayscale/RGB/grayscale+alpha/RGBA. It handles `tRNS` transparency
 * for grayscale, RGB, and indexed colour PNGs. It parses `iCCP` chunks
 * best-effort: malformed chunks reject the PNG, parseable profiles become the
 * image color space, and structurally-valid but unsupported profiles retain an
 * explicit refusal state. Colour metadata chunks `gAMA`, `cHRM`, and `sRGB` are recognized and
 * structurally validated; `sRGB` and `gAMA` synthesize an ICC profile when no
 * `iCCP` chunk is present, `cHRM` is validated but does not synthesize a
 * profile.
 */
public class PngCodec private constructor(
    private val png: ParsedPng,
) : Codec() {

    private val cachedInfo: SkImageInfo by lazy {
        val isF16 = png.bitDepth == 16
        SkImageInfo.Make(
            width = png.width,
            height = png.height,
            colorType = if (isF16) SkColorType.kRGBA_F16Norm else SkColorType.kRGBA_8888,
            alphaType = if (isF16) SkAlphaType.kPremul else SkAlphaType.kUnpremul,
            colorSpace = png.iccProfile?.let(SkColorSpace::makeProfileAware) ?: SkColorSpace.makeSRGB(),
        )
    }

    override fun getInfo(): SkImageInfo = cachedInfo

    override fun getEncodedFormat(): SkEncodedImageFormat = SkEncodedImageFormat.kPNG

    override fun getICCProfile(): SkcmsICCProfile? = png.iccProfile

    override fun getPixels(info: SkImageInfo, dst: SkBitmap): Result {
        if (dst.width != info.width || dst.height != info.height) {
            return Result.kInvalidParameters
        }
        if (dst.colorType != info.colorType) {
            return Result.kInvalidParameters
        }
        if (!canDecodeTo(info.colorType)) {
            return Result.kInvalidConversion
        }

        val expected = png.inflatedBytes
        val inflated = try {
            inflate(png.idat, expected)
        } catch (_: DataFormatException) {
            return Result.kErrorInInput
        }
        if (inflated.size < expected) return Result.kIncompleteInput

        return if (png.interlace == INTERLACE_ADAM7) {
            decodeAdam7(inflated, dst)
        } else {
            decodeScanlines(inflated, dst)
        }
    }

    private fun decodeScanlines(inflated: ByteArray, dst: SkBitmap): Result {
        val bpp = png.filterBytesPerPixel
        val rowBytes = png.rowBytes
        val previous = ByteArray(rowBytes)
        val current = ByteArray(rowBytes)
        var src = 0
        for (y in 0 until png.height) {
            val filter = inflated[src++].toInt() and 0xFF
            if (src + rowBytes > inflated.size) return Result.kIncompleteInput
            for (x in 0 until rowBytes) current[x] = inflated[src++]
            if (!unfilter(filter, current, previous, bpp)) return Result.kErrorInInput

            val result = if (png.bitDepth == 16) {
                decodeF16Row(current, y, dst)
            } else {
                decode8888Row(current, y, dst)
            }
            if (result != Result.kSuccess) return result

            current.copyInto(previous)
        }
        return Result.kSuccess
    }

    private fun decodeAdam7(inflated: ByteArray, dst: SkBitmap): Result {
        var src = 0
        for (pass in ADAM7_PASSES) {
            val passWidth = adam7Size(png.width, pass.xStart, pass.xStep)
            val passHeight = adam7Size(png.height, pass.yStart, pass.yStep)
            if (passWidth == 0 || passHeight == 0) continue

            val rowBytes = rowBytesFor(passWidth, png.bitsPerPixel)
            val previous = ByteArray(rowBytes)
            val current = ByteArray(rowBytes)
            for (passY in 0 until passHeight) {
                if (src >= inflated.size) return Result.kIncompleteInput
                val filter = inflated[src++].toInt() and 0xFF
                if (src + rowBytes > inflated.size) return Result.kIncompleteInput
                for (x in 0 until rowBytes) current[x] = inflated[src++]
                if (!unfilter(filter, current, previous, png.filterBytesPerPixel)) return Result.kErrorInInput

                val y = pass.yStart + passY * pass.yStep
                for (passX in 0 until passWidth) {
                    val x = pass.xStart + passX * pass.xStep
                    val result = if (png.bitDepth == 16) {
                        decodeF16Pixel(current, passX, x, y, dst)
                    } else {
                        decode8888Pixel(current, passX, x, y, dst)
                    }
                    if (result != Result.kSuccess) return result
                }
                current.copyInto(previous)
            }
        }
        return Result.kSuccess
    }

    private fun decode8888Row(current: ByteArray, y: Int, dst: SkBitmap): Result {
        for (x in 0 until png.width) {
            val result = decode8888Pixel(current, x, x, y, dst)
            if (result != Result.kSuccess) return result
        }
        return Result.kSuccess
    }

    private fun decode8888Pixel(current: ByteArray, sourceX: Int, dstX: Int, y: Int, dst: SkBitmap): Result {
        var p = sourceOffset(sourceX)
        when (png.colorType) {
            COLOR_GRAYSCALE -> {
                val sample = if (png.bitDepth == 8) {
                    current[p].toInt() and 0xFF
                } else {
                    readPackedSample(current, sourceX, png.bitDepth)
                }
                val gray = scaleSample(sample, png.bitDepth)
                val alpha = if (png.transparency.isTransparentGray(sample)) 0x00 else 0xFF
                dst.setPixel(dstX, y, argb(alpha, gray, gray, gray))
            }
            COLOR_RGB -> {
                val r = current[p++].toInt() and 0xFF
                val g = current[p++].toInt() and 0xFF
                val b = current[p].toInt() and 0xFF
                val alpha = if (png.transparency.isTransparentRgb(r, g, b)) 0x00 else 0xFF
                dst.setPixel(dstX, y, argb(alpha, r, g, b))
            }
            COLOR_PALETTE -> {
                val index = if (png.bitDepth == 8) {
                    current[p].toInt() and 0xFF
                } else {
                    readPackedSample(current, sourceX, png.bitDepth)
                }
                val palette = png.palette ?: return Result.kErrorInInput
                if (index >= palette.size) return Result.kErrorInInput
                dst.setPixel(dstX, y, palette[index])
            }
            COLOR_GRAYSCALE_ALPHA -> {
                val gray = current[p++].toInt() and 0xFF
                val alpha = current[p].toInt() and 0xFF
                dst.setPixel(dstX, y, argb(alpha, gray, gray, gray))
            }
            COLOR_RGBA -> {
                val r = current[p++].toInt() and 0xFF
                val g = current[p++].toInt() and 0xFF
                val b = current[p++].toInt() and 0xFF
                val a = current[p].toInt() and 0xFF
                dst.setPixel(dstX, y, argb(a, r, g, b))
            }
            else -> return Result.kErrorInInput
        }
        return Result.kSuccess
    }

    private fun sourceOffset(sourceX: Int): Int =
        when {
            png.bitDepth < 8 && (png.colorType == COLOR_GRAYSCALE || png.colorType == COLOR_PALETTE) -> 0
            else -> sourceX * png.bitsPerPixel / 8
        }

    private fun decodeF16Row(current: ByteArray, y: Int, dst: SkBitmap): Result {
        for (x in 0 until png.width) {
            val result = decodeF16Pixel(current, x, x, y, dst)
            if (result != Result.kSuccess) return result
        }
        return Result.kSuccess
    }

    private fun decodeF16Pixel(current: ByteArray, sourceX: Int, dstX: Int, y: Int, dst: SkBitmap): Result {
        val p = sourceOffset(sourceX)
        var r: Float
        var g: Float
        var b: Float
        val a: Float
        val inv65535 = 1f / 65535f
        when (png.colorType) {
            COLOR_GRAYSCALE -> {
                val graySample = readU16BE(current, p)
                val gray = graySample * inv65535
                r = gray
                g = gray
                b = gray
                a = if (png.transparency.isTransparentGray(graySample)) 0f else 1f
            }
            COLOR_RGB -> {
                val rSample = readU16BE(current, p)
                val gSample = readU16BE(current, p + 2)
                val bSample = readU16BE(current, p + 4)
                r = rSample * inv65535
                g = gSample * inv65535
                b = bSample * inv65535
                a = if (png.transparency.isTransparentRgb(rSample, gSample, bSample)) 0f else 1f
            }
            COLOR_GRAYSCALE_ALPHA -> {
                val gray = readU16BE(current, p) * inv65535
                a = readU16BE(current, p + 2) * inv65535
                r = gray
                g = gray
                b = gray
            }
            COLOR_RGBA -> {
                r = readU16BE(current, p) * inv65535
                g = readU16BE(current, p + 2) * inv65535
                b = readU16BE(current, p + 4) * inv65535
                a = readU16BE(current, p + 6) * inv65535
            }
            else -> return Result.kErrorInInput
        }
        dst.setPixelF16(dstX, y, r * a, g * a, b * a, a)
        return Result.kSuccess
    }

    private fun canDecodeTo(colorType: SkColorType): Boolean =
        colorType == cachedInfo.colorType ||
            colorType == SkColorType.kRGBA_8888 ||
            colorType == SkColorType.kRGBA_F16Norm ||
            (
                png.bitDepth < 16 &&
                    (
                        colorType == SkColorType.kBGRA_8888 ||
                            colorType == SkColorType.kARGB_4444 ||
                            colorType == SkColorType.kAlpha_8 ||
                            colorType == SkColorType.kRGB_565 ||
                            colorType == SkColorType.kGray_8
                        )
                )

    internal companion object Decoder : Codec.Decoder {
        override val name: String = "png"

        override fun matches(data: ByteArray): Boolean = hasPngSignature(data)

        override fun make(data: ByteArray): Codec? {
            if (!hasPngSignature(data)) return null
            val png = parse(data) ?: return null
            return PngCodec(png)
        }

        private fun parse(data: ByteArray): ParsedPng? {
            var offset = PNG_SIGNATURE.size
            var header: Header? = null
            val idat = ByteArrayOutputStream()
            var palette: IntArray? = null
            var transparency: Transparency? = null
            var iccProfile: SkcmsICCProfile? = null
            var sawIccp = false
            var sawGamma = false
            var sawChrm = false
            var sawSrgb = false
            var sawIdat = false
            var sawNonIdatAfterIdat = false
            var sawIend = false

            while (offset + CHUNK_OVERHEAD <= data.size) {
                val length = readI32BE(data, offset)
                if (length < 0) return null
                val typeOffset = offset + 4
                val dataOffset = typeOffset + 4
                val crcOffset = dataOffset + length
                if (crcOffset.toLong() + 4L > data.size.toLong()) return null

                val type = readType(data, typeOffset)
                if (!crcMatches(data, typeOffset, length + 4, crcOffset)) return null
                when (type) {
                    TYPE_IHDR -> {
                        if (header != null || offset != PNG_SIGNATURE.size || length != 13) return null
                        header = parseHeader(data, dataOffset) ?: return null
                    }
                    TYPE_IDAT -> {
                        if (header == null || sawIend || sawNonIdatAfterIdat) return null
                        if (header.colorType == COLOR_PALETTE && palette == null) return null
                        idat.write(data, dataOffset, length)
                        sawIdat = true
                    }
                    TYPE_PLTE -> {
                        if (header == null || sawIdat || sawIend || palette != null) return null
                        if (header.colorType == COLOR_GRAYSCALE || header.colorType == COLOR_GRAYSCALE_ALPHA) return null
                        palette = parsePalette(data, dataOffset, length) ?: return null
                    }
                    TYPE_TRNS -> {
                        if (header == null || sawIdat || sawIend || transparency != null) return null
                        transparency = parseTransparency(
                            data = data,
                            offset = dataOffset,
                            length = length,
                            header = header,
                            palette = palette,
                        ) ?: return null
                    }
                    TYPE_ICCP -> {
                        if (header == null || sawIdat || sawIend || sawIccp) return null
                        sawIccp = true
                        val profileBytes = parseIccp(data, dataOffset, length) ?: return null
                        iccProfile = skcmsParse(profileBytes)
                        if (iccProfile != null &&
                            !iccMatchesColorType(iccProfile.colorProfile.colorModel, header.colorType)
                        ) {
                            return null
                        }
                    }
                    TYPE_GAMA -> {
                        if (header == null || sawIdat || sawIend || sawGamma || palette != null) return null
                        if (length != 4) return null
                        sawGamma = true
                    }
                    TYPE_CHRM -> {
                        if (header == null || sawIdat || sawIend || sawChrm || palette != null) return null
                        if (length != 32) return null
                        sawChrm = true
                    }
                    TYPE_SRGB -> {
                        if (header == null || sawIdat || sawIend || sawSrgb || palette != null) return null
                        if (length != 1) return null
                        val intent = data[dataOffset].toInt() and 0xFF
                        if (intent > 3) return null
                        sawSrgb = true
                    }
                    TYPE_IEND -> {
                        if (length != 0 || header == null) return null
                        sawIend = true
                        offset = crcOffset + 4
                        break
                    }
                    else -> {
                        if (isCritical(type)) return null
                        if (sawIdat) sawNonIdatAfterIdat = true
                    }
                }
                offset = crcOffset + 4
            }

            val h = header ?: return null
            if (!sawIdat || !sawIend || offset != data.size) return null
            val finalPalette = if (h.colorType == COLOR_PALETTE) {
                paletteWithTransparency(palette, transparency as? Transparency.Palette) ?: return null
            } else {
                null
            }
            if (finalPalette != null && finalPalette.size > (1 shl h.bitDepth)) return null
            val finalIcc = iccProfile ?: synthesizeIcc(sawSrgb, sawGamma)
            return ParsedPng(
                width = h.width,
                height = h.height,
                bitDepth = h.bitDepth,
                colorType = h.colorType,
                rowBytes = h.rowBytes,
                bitsPerPixel = h.bitsPerPixel,
                filterBytesPerPixel = h.filterBytesPerPixel,
                inflatedBytes = h.inflatedBytes,
                interlace = h.interlace,
                idat = idat.toByteArray(),
                palette = finalPalette,
                transparency = if (h.colorType == COLOR_GRAYSCALE || h.colorType == COLOR_RGB) transparency else null,
                iccProfile = finalIcc,
            )
        }

        private fun parseHeader(data: ByteArray, offset: Int): Header? {
            val width = readI32BE(data, offset)
            val height = readI32BE(data, offset + 4)
            val bitDepth = data[offset + 8].toInt() and 0xFF
            val colorType = data[offset + 9].toInt() and 0xFF
            val compression = data[offset + 10].toInt() and 0xFF
            val filter = data[offset + 11].toInt() and 0xFF
            val interlace = data[offset + 12].toInt() and 0xFF
            if (width !in 1..MAX_DIMENSION || height !in 1..MAX_DIMENSION) return null
            if (!isSupportedColorDepth(colorType, bitDepth)) return null
            if (compression != 0 || filter != 0 || interlace !in INTERLACE_NONE..INTERLACE_ADAM7) return null
            val bitsPerPixel = bitsPerPixel(colorType, bitDepth)
            val rowBytes = rowBytesFor(width, bitsPerPixel).toLong()
            if (rowBytes > Int.MAX_VALUE) return null
            val expected = inflatedBytes(width, height, bitsPerPixel, interlace) ?: return null
            if (expected > Int.MAX_VALUE) return null
            return Header(
                width = width,
                height = height,
                bitDepth = bitDepth,
                colorType = colorType,
                rowBytes = rowBytes.toInt(),
                bitsPerPixel = bitsPerPixel,
                filterBytesPerPixel = filterBytesPerPixel(colorType, bitDepth),
                inflatedBytes = expected.toInt(),
                interlace = interlace,
            )
        }

        private fun iccMatchesColorType(colorModel: ColorModel, colorType: Int): Boolean = when (colorType) {
            COLOR_GRAYSCALE, COLOR_GRAYSCALE_ALPHA -> colorModel == ColorModel.GRAY
            COLOR_RGB, COLOR_PALETTE, COLOR_RGBA -> colorModel == ColorModel.RGB
            else -> false
        }

        private fun isSupportedColorDepth(colorType: Int, bitDepth: Int): Boolean =
            when (colorType) {
                COLOR_GRAYSCALE -> bitDepth == 1 || bitDepth == 2 || bitDepth == 4 || bitDepth == 8 || bitDepth == 16
                COLOR_RGB -> bitDepth == 8 || bitDepth == 16
                COLOR_PALETTE -> bitDepth == 1 || bitDepth == 2 || bitDepth == 4 || bitDepth == 8
                COLOR_GRAYSCALE_ALPHA -> bitDepth == 8 || bitDepth == 16
                COLOR_RGBA -> bitDepth == 8 || bitDepth == 16
                else -> false
            }

        private fun bitsPerPixel(colorType: Int, bitDepth: Int): Int =
            when (colorType) {
                COLOR_GRAYSCALE, COLOR_PALETTE -> bitDepth
                COLOR_RGB -> bitDepth * 3
                COLOR_GRAYSCALE_ALPHA -> bitDepth * 2
                COLOR_RGBA -> bitDepth * 4
                else -> 0
            }

        private fun filterBytesPerPixel(colorType: Int, bitDepth: Int): Int =
            when (colorType) {
                COLOR_RGB -> 3 * bytesPerSample(bitDepth)
                COLOR_GRAYSCALE_ALPHA -> 2 * bytesPerSample(bitDepth)
                COLOR_RGBA -> 4 * bytesPerSample(bitDepth)
                COLOR_GRAYSCALE -> bytesPerSample(bitDepth)
                else -> 1
            }

        private fun bytesPerSample(bitDepth: Int): Int = if (bitDepth == 16) 2 else 1

        private fun parsePalette(data: ByteArray, offset: Int, length: Int): IntArray? {
            if (length == 0 || length % 3 != 0) return null
            val entries = length / 3
            if (entries > 256) return null
            return IntArray(entries) { i ->
                val p = offset + i * 3
                argb(
                    a = 0xFF,
                    r = data[p].toInt() and 0xFF,
                    g = data[p + 1].toInt() and 0xFF,
                    b = data[p + 2].toInt() and 0xFF,
                )
            }
        }

        private fun parseTransparency(
            data: ByteArray,
            offset: Int,
            length: Int,
            header: Header,
            palette: IntArray?,
        ): Transparency? =
            when (header.colorType) {
                COLOR_GRAYSCALE -> {
                    if (length != 2) return null
                    val sample = readU16BE(data, offset)
                    if (sample >= (1 shl header.bitDepth)) return null
                    Transparency.Gray(sample)
                }
                COLOR_RGB -> {
                    if (length != 6) return null
                    val maxSample = if (header.bitDepth == 16) 0xFFFF else 0xFF
                    val r = readU16BE(data, offset)
                    val g = readU16BE(data, offset + 2)
                    val b = readU16BE(data, offset + 4)
                    if (r > maxSample || g > maxSample || b > maxSample) return null
                    Transparency.Rgb(r, g, b)
                }
                COLOR_PALETTE -> {
                    if (palette == null || length > palette.size) return null
                    Transparency.Palette(data.copyOfRange(offset, offset + length))
                }
                else -> null
            }

        private fun paletteWithTransparency(palette: IntArray?, transparency: Transparency.Palette?): IntArray? {
            val colors = palette?.copyOf() ?: return null
            if (transparency != null) {
                for (i in transparency.alpha.indices) {
                    colors[i] = (colors[i] and 0x00FFFFFF) or ((transparency.alpha[i].toInt() and 0xFF) shl 24)
                }
            }
            return colors
        }

        private fun parseIccp(data: ByteArray, offset: Int, length: Int): ByteArray? {
            val end = offset + length
            var nameEnd = offset
            while (nameEnd < end && data[nameEnd] != 0.toByte()) nameEnd++
            val nameLength = nameEnd - offset
            if (nameLength !in 1..79) return null
            if (nameEnd + 2 > end) return null
            if ((data[nameEnd + 1].toInt() and 0xFF) != 0) return null
            return try {
                inflateAll(data.copyOfRange(nameEnd + 2, end), maxSize = MAX_ICC_PROFILE_SIZE)
            } catch (_: DataFormatException) {
                null
            }
        }

        private fun synthesizeIcc(sawSrgb: Boolean, sawGamma: Boolean): SkcmsICCProfile? {
            if (sawSrgb) {
                return skcmsParse(SkICC.WriteToICC(SkNamedTransferFn.kSRGB, SkNamedGamut.kSRGB))
            }
            if (sawGamma) {
                return skcmsParse(SkICC.WriteToICC(SkNamedTransferFn.kSRGB, SkNamedGamut.kSRGB))
            }
            return null
        }

        private fun hasPngSignature(data: ByteArray): Boolean {
            if (data.size < PNG_SIGNATURE.size) return false
            for (i in PNG_SIGNATURE.indices) {
                if (data[i] != PNG_SIGNATURE[i]) return false
            }
            return true
        }

        private fun crcMatches(data: ByteArray, offset: Int, length: Int, expectedOffset: Int): Boolean {
            val crc = CRC32()
            crc.update(data, offset, length)
            return crc.value.toInt() == readI32BE(data, expectedOffset)
        }
    }

    private data class Header(
        val width: Int,
        val height: Int,
        val bitDepth: Int,
        val colorType: Int,
        val rowBytes: Int,
        val bitsPerPixel: Int,
        val filterBytesPerPixel: Int,
        val inflatedBytes: Int,
        val interlace: Int,
    )

    private data class ParsedPng(
        val width: Int,
        val height: Int,
        val bitDepth: Int,
        val colorType: Int,
        val rowBytes: Int,
        val bitsPerPixel: Int,
        val filterBytesPerPixel: Int,
        val inflatedBytes: Int,
        val interlace: Int,
        val idat: ByteArray,
        val palette: IntArray?,
        val transparency: Transparency?,
        val iccProfile: SkcmsICCProfile?,
    )
}

public class PngKotlinDecoderProvider : CodecDecoderProvider {
    override fun decoders(): List<Codec.Decoder> = listOf(PngCodec.Decoder)
}

private val PNG_SIGNATURE = byteArrayOf(
    0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
)
private const val CHUNK_OVERHEAD: Int = 12
private const val COLOR_GRAYSCALE: Int = 0
private const val COLOR_RGB: Int = 2
private const val COLOR_PALETTE: Int = 3
private const val COLOR_GRAYSCALE_ALPHA: Int = 4
private const val COLOR_RGBA: Int = 6
private const val INTERLACE_NONE: Int = 0
private const val INTERLACE_ADAM7: Int = 1
private const val MAX_DIMENSION: Int = 100_000
private const val TYPE_IHDR: Int = 0x49484452
private const val TYPE_IDAT: Int = 0x49444154
private const val TYPE_IEND: Int = 0x49454E44
private const val TYPE_CHRM: Int = 0x6348524D
private const val TYPE_GAMA: Int = 0x67414D41
private const val TYPE_ICCP: Int = 0x69434350
private const val TYPE_PLTE: Int = 0x504C5445
private const val TYPE_SRGB: Int = 0x73524742
private const val TYPE_TRNS: Int = 0x74524E53
private const val MAX_ICC_PROFILE_SIZE: Int = 16 * 1024 * 1024

private data class Adam7Pass(
    val xStart: Int,
    val yStart: Int,
    val xStep: Int,
    val yStep: Int,
)

private val ADAM7_PASSES = arrayOf(
    Adam7Pass(0, 0, 8, 8),
    Adam7Pass(4, 0, 8, 8),
    Adam7Pass(0, 4, 4, 8),
    Adam7Pass(2, 0, 4, 4),
    Adam7Pass(0, 2, 2, 4),
    Adam7Pass(1, 0, 2, 2),
    Adam7Pass(0, 1, 1, 2),
)

private fun adam7Size(size: Int, start: Int, step: Int): Int =
    if (size <= start) 0 else (size - start + step - 1) / step

private fun rowBytesFor(width: Int, bitsPerPixel: Int): Int =
    ((width.toLong() * bitsPerPixel.toLong() + 7L) / 8L).toInt()

private fun inflatedBytes(width: Int, height: Int, bitsPerPixel: Int, interlace: Int): Long? {
    if (interlace == INTERLACE_NONE) {
        return (rowBytesFor(width, bitsPerPixel).toLong() + 1L) * height.toLong()
    }
    var total = 0L
    for (pass in ADAM7_PASSES) {
        val passWidth = adam7Size(width, pass.xStart, pass.xStep)
        val passHeight = adam7Size(height, pass.yStart, pass.yStep)
        if (passWidth == 0 || passHeight == 0) continue
        total += (rowBytesFor(passWidth, bitsPerPixel).toLong() + 1L) * passHeight.toLong()
        if (total > Int.MAX_VALUE) return null
    }
    return total
}

private fun inflate(data: ByteArray, expectedSize: Int): ByteArray {
    val inflater = Inflater()
    inflater.setInput(data)
    val out = ByteArrayOutputStream(expectedSize)
    val buffer = ByteArray(8192)
    try {
        while (!inflater.finished()) {
            val count = inflater.inflate(buffer)
            when {
                count > 0 -> out.write(buffer, 0, count)
                inflater.needsInput() || inflater.needsDictionary() -> break
            }
            if (out.size() > expectedSize) break
        }
        return out.toByteArray()
    } finally {
        inflater.end()
    }
}

private fun inflateAll(data: ByteArray, maxSize: Int): ByteArray {
    val inflater = Inflater()
    inflater.setInput(data)
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    try {
        while (!inflater.finished()) {
            val count = inflater.inflate(buffer)
            when {
                count > 0 -> {
                    out.write(buffer, 0, count)
                    if (out.size() > maxSize) throw DataFormatException("inflated data too large")
                }
                inflater.needsInput() || inflater.needsDictionary() -> throw DataFormatException("truncated deflate stream")
            }
        }
        return out.toByteArray()
    } finally {
        inflater.end()
    }
}

private fun unfilter(filter: Int, row: ByteArray, previous: ByteArray, bpp: Int): Boolean {
    for (i in row.indices) {
        val raw = row[i].toInt() and 0xFF
        val left = if (i >= bpp) row[i - bpp].toInt() and 0xFF else 0
        val up = previous[i].toInt() and 0xFF
        val upLeft = if (i >= bpp) previous[i - bpp].toInt() and 0xFF else 0
        val predictor = when (filter) {
            0 -> 0
            1 -> left
            2 -> up
            3 -> (left + up) / 2
            4 -> paeth(left, up, upLeft)
            else -> return false
        }
        row[i] = ((raw + predictor) and 0xFF).toByte()
    }
    return true
}

private fun paeth(a: Int, b: Int, c: Int): Int {
    val p = a + b - c
    val pa = kotlin.math.abs(p - a)
    val pb = kotlin.math.abs(p - b)
    val pc = kotlin.math.abs(p - c)
    return when {
        pa <= pb && pa <= pc -> a
        pb <= pc -> b
        else -> c
    }
}

private fun readI32BE(bytes: ByteArray, offset: Int): Int =
    ((bytes[offset].toInt() and 0xFF) shl 24) or
        ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
        ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
        (bytes[offset + 3].toInt() and 0xFF)

private fun readType(bytes: ByteArray, offset: Int): Int = readI32BE(bytes, offset)

private fun readU16BE(bytes: ByteArray, offset: Int): Int =
    ((bytes[offset].toInt() and 0xFF) shl 8) or
        (bytes[offset + 1].toInt() and 0xFF)

private fun readPackedSample(row: ByteArray, x: Int, bitDepth: Int): Int {
    val bitOffset = x * bitDepth
    val value = row[bitOffset / 8].toInt() and 0xFF
    val shift = 8 - bitDepth - (bitOffset % 8)
    return (value ushr shift) and ((1 shl bitDepth) - 1)
}

private fun scaleSample(value: Int, bitDepth: Int): Int =
    if (bitDepth == 8) value else value * 255 / ((1 shl bitDepth) - 1)

private fun Transparency?.isTransparentGray(sample: Int): Boolean =
    this is Transparency.Gray && this.sample == sample

private fun Transparency?.isTransparentRgb(r: Int, g: Int, b: Int): Boolean =
    this is Transparency.Rgb && this.r == r && this.g == g && this.b == b

private sealed class Transparency {
    data class Gray(val sample: Int) : Transparency()
    data class Rgb(val r: Int, val g: Int, val b: Int) : Transparency()
    data class Palette(val alpha: ByteArray) : Transparency()
}

private fun isCritical(type: Int): Boolean =
    (((type ushr 24) and 0x20) == 0)

private fun argb(a: Int, r: Int, g: Int, b: Int): Int =
    ((a and 0xFF) shl 24) or
        ((r and 0xFF) shl 16) or
        ((g and 0xFF) shl 8) or
        (b and 0xFF)
