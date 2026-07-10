package org.graphiks.kanvas.codec.png

import org.graphiks.kanvas.codec.CodecDecoderProvider
import org.graphiks.kanvas.codec.Codec
import org.graphiks.kanvas.color.ColorModel
import org.graphiks.kanvas.color.ColorProfile
import org.graphiks.kanvas.color.ColorProfiles
import org.graphiks.math.SkcmsMatrix3x3
import org.graphiks.math.SkcmsTransferFunction
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.SkEncodedImageFormat
import org.skia.foundation.SkImageInfo
import org.skia.foundation.skcms.SkNamedGamut
import org.skia.foundation.skcms.SkcmsICCProfile
import org.skia.foundation.skcms.skcmsParse
import java.io.ByteArrayOutputStream
import java.util.Collections
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
 * through the shared typed metadata parser. Resolved colour signals have PNG
 * precedence `cICP > iCCP > sRGB > cHRM+gAMA`; refused metadata is retained
 * as a diagnostic but never applied. `getICCProfile()` reports only an ICC
 * profile embedded in `iCCP`, independently of the selected colour signal.
 */
public class PngCodec private constructor(
    private val png: ParsedPng,
    diagnostics: List<PngDiagnostic>,
) : Codec() {

    /** Non-fatal metadata refusals retained while opening this static PNG. */
    public val diagnostics: List<PngDiagnostic> = Collections.unmodifiableList(ArrayList(diagnostics))

    private val cachedInfo: SkImageInfo by lazy {
        val isF16 = png.bitDepth == 16
        SkImageInfo.Make(
            width = png.width,
            height = png.height,
            colorType = if (isF16) SkColorType.kRGBA_F16Norm else SkColorType.kRGBA_8888,
            alphaType = if (isF16) SkAlphaType.kPremul else SkAlphaType.kUnpremul,
            colorSpace = png.resolvedColorProfile?.let(SkColorSpace::makeProfileAware) ?: SkColorSpace.makeSRGB(),
        )
    }

    override fun getInfo(): SkImageInfo = cachedInfo

    override fun getEncodedFormat(): SkEncodedImageFormat = SkEncodedImageFormat.kPNG

    override fun getICCProfile(): SkcmsICCProfile? = png.embeddedIccProfile

    override fun getPixels(info: SkImageInfo, dst: SkBitmap): Result {
        if (info.width != cachedInfo.width || info.height != cachedInfo.height) {
            return Result.kInvalidScale
        }
        if (info.colorSpace !== cachedInfo.colorSpace || info.alphaType != cachedInfo.alphaType) {
            return Result.kInvalidConversion
        }
        if (dst.width != info.width || dst.height != info.height) {
            return Result.kInvalidParameters
        }
        if (dst.colorType != info.colorType) {
            return Result.kInvalidParameters
        }
        if (dst.colorSpace !== info.colorSpace) {
            return Result.kInvalidParameters
        }
        if (!canDecodeTo(info.colorType)) {
            return Result.kInvalidConversion
        }
        if (isOpaqueColorType(info.colorType) && sourceMayContainAlpha()) {
            return Result.kInvalidConversion
        }

        val expected = png.inflatedBytes
        val inflated = try {
            inflate(png.idat, expected)
        } catch (_: IncompleteZlibStreamException) {
            return Result.kIncompleteInput
        } catch (_: DataFormatException) {
            return Result.kErrorInInput
        }
        if (inflated.size < expected) return Result.kIncompleteInput
        if (inflated.size > expected) return Result.kErrorInInput

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
                dst.setPixel(dstX, y, palette.getOrElse(index) { OPAQUE_BLACK })
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

    private fun isOpaqueColorType(colorType: SkColorType): Boolean =
        colorType == SkColorType.kRGB_565 || colorType == SkColorType.kGray_8

    private fun sourceMayContainAlpha(): Boolean = when (png.colorType) {
        COLOR_GRAYSCALE_ALPHA,
        COLOR_RGBA,
            -> true
        COLOR_GRAYSCALE,
        COLOR_RGB,
            -> png.transparency != null
        COLOR_PALETTE -> png.palette?.any { color -> (color ushr 24) != 0xFF } == true
        else -> false
    }

    public companion object Decoder : Codec.Decoder {
        override val name: String = "png"

        override fun matches(data: ByteArray): Boolean = hasPngSignature(data)

        override fun make(data: ByteArray): Codec? = when (val result = open(data)) {
            is PngCodecOpenResult.Success -> result.codec
            is PngCodecOpenResult.Failure -> null
        }

        /** Opens a static PNG while retaining the shared container or metadata diagnostic on refusal. */
        public fun open(data: ByteArray): PngCodecOpenResult {
            val container = when (val result = PngContainerParser.parse(data)) {
                is PngContainerParseResult.Success -> result.container
                is PngContainerParseResult.Failure -> return PngCodecOpenResult.Failure(result.diagnostic)
            }
            val metadata = PngMetadataParser.parse(data, container, PngMetadataLimits.Default)
            val activeColorSignals = when {
                metadata.cICP is PngMetadataValue.Resolved<PngCicpMetadata> -> setOf("cICP")
                metadata.iCCP is PngMetadataValue.Resolved<PngIccProfileMetadata> -> setOf("iCCP")
                metadata.sRGB is PngMetadataValue.Resolved<PngSrgbMetadata> -> setOf("sRGB")
                metadata.cHRM is PngMetadataValue.Resolved<PngChromaticitiesMetadata> &&
                    metadata.gAMA is PngMetadataValue.Resolved<PngGammaMetadata> -> setOf("cHRM", "gAMA")
                else -> emptySet()
            }
            container.metadataDiagnostics.firstOrNull { diagnostic ->
                diagnostic.chunkType == "tRNS" || diagnostic.chunkType in activeColorSignals
            }?.let { diagnostic -> return PngCodecOpenResult.Failure(diagnostic) }

            val structurallyRefusedColorSignals = container.metadataDiagnostics
                .mapNotNull(PngDiagnostic::chunkType)
                .filterTo(HashSet()) { it in COLOR_PROFILE_CHUNK_TYPES }
            val colors = resolveColorProfiles(data, metadata, structurallyRefusedColorSignals)
            val png = parse(data, container, colors) ?: return PngCodecOpenResult.Failure(
                diagnostic = metadata.diagnostics.firstOrNull() ?: PngDiagnostic(
                    code = "png.codec.decode.unsupported",
                    offset = 0L,
                    message = "PNG raster data cannot be decoded by the static codec",
                ),
            )
            return PngCodecOpenResult.Success(PngCodec(png, metadata.diagnostics + colors.diagnostics))
        }

        private fun parse(
            data: ByteArray,
            container: PngContainer,
            colors: ColorResolution,
        ): ParsedPng? {
            val header = decodeHeader(container.header) ?: return null
            if (container.totalIdatBytes !in 1L..Int.MAX_VALUE.toLong()) return null
            val idat = ByteArray(container.totalIdatBytes.toInt())
            var idatOffset = 0
            var palette: IntArray? = null
            var transparency: Transparency? = null
            var sawIdat = false

            for (chunk in container.chunks) {
                val dataOffset = chunk.payloadRange.startInclusive.toInt()
                val length = chunk.payloadRange.size.toInt()
                when (chunk.type) {
                    "IHDR" -> Unit
                    "IDAT" -> {
                        if (header.colorType == COLOR_PALETTE && palette == null) return null
                        data.copyInto(idat, idatOffset, dataOffset, dataOffset + length)
                        idatOffset += length
                        sawIdat = true
                    }
                    "PLTE" -> {
                        if (sawIdat || palette != null) return null
                        if (header.colorType == COLOR_GRAYSCALE || header.colorType == COLOR_GRAYSCALE_ALPHA) return null
                        palette = parsePalette(data, dataOffset, length) ?: return null
                    }
                    "tRNS" -> {
                        if (sawIdat || transparency != null) return null
                        transparency = parseTransparency(
                            data = data,
                            offset = dataOffset,
                            length = length,
                            header = header,
                            palette = palette,
                        ) ?: return null
                    }
                    "iCCP", "gAMA", "cHRM", "sRGB", "cICP" -> Unit
                    "IEND" -> Unit
                }
            }

            val h = header
            if (!sawIdat || idatOffset != idat.size) return null
            val finalPalette = if (h.colorType == COLOR_PALETTE) {
                paletteWithTransparency(palette, transparency as? Transparency.Palette) ?: return null
            } else {
                null
            }
            if (finalPalette != null && finalPalette.size > (1 shl h.bitDepth)) return null
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
                idat = idat,
                palette = finalPalette,
                transparency = if (h.colorType == COLOR_GRAYSCALE || h.colorType == COLOR_RGB) transparency else null,
                embeddedIccProfile = colors.embeddedIccProfile,
                resolvedColorProfile = colors.resolvedColorProfile,
            )
        }

        private fun decodeHeader(header: PngHeader): Header? {
            val width = header.width
            val height = header.height
            val bitDepth = header.bitDepth
            val colorType = header.colorType
            val interlace = header.interlaceMethod
            if (width !in 1..MAX_DIMENSION || height !in 1..MAX_DIMENSION) return null
            if (!isSupportedColorDepth(colorType, bitDepth)) return null
            val pixelCount = width.toLong() * height.toLong()
            if (pixelCount > MAX_OUTPUT_PIXELS) return null
            val bitmapBytes = pixelCount * if (bitDepth == 16) F16_BITMAP_BYTES_PER_PIXEL else BITMAP_BYTES_PER_PIXEL
            if (bitmapBytes > MAX_OUTPUT_ALLOCATION_BYTES) return null
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

        private fun parseEmbeddedIccp(data: ByteArray, record: PngChunkRecord): SkcmsICCProfile? {
            val offset = record.payloadRange.startInclusive.toInt()
            val length = record.payloadRange.size.toInt()
            val end = offset + length
            var nameEnd = offset
            while (nameEnd < end && data[nameEnd] != 0.toByte()) nameEnd++
            val nameLength = nameEnd - offset
            if (nameLength !in 1..79) return null
            if (nameEnd + 2 > end) return null
            if ((data[nameEnd + 1].toInt() and 0xFF) != 0) return null
            return try {
                skcmsParse(inflateAll(data.copyOfRange(nameEnd + 2, end), maxSize = MAX_ICC_PROFILE_SIZE))
            } catch (_: DataFormatException) {
                null
            }
        }

        private fun resolveColorProfiles(
            data: ByteArray,
            metadata: PngMetadata,
            structurallyRefusedColorSignals: Set<String>,
        ): ColorResolution {
            val embeddedIcc = if ("iCCP" in structurallyRefusedColorSignals) {
                null
            } else {
                (metadata.iCCP as? PngMetadataValue.Resolved<PngIccProfileMetadata>)
                    ?.let { parseEmbeddedIccp(data, it.record) }
            }
            val cicp = metadata.cICP as? PngMetadataValue.Resolved<PngCicpMetadata>
            if (cicp != null) return resolveCicpProfile(cicp, embeddedIcc)

            val iccp = metadata.iCCP as? PngMetadataValue.Resolved<PngIccProfileMetadata>
            if (iccp != null) {
                return ColorResolution(
                    embeddedIccProfile = embeddedIcc,
                    resolvedColorProfile = SkcmsICCProfile.fromColorProfile(iccp.value.profile),
                    diagnostics = emptyList(),
                )
            }

            if (metadata.sRGB is PngMetadataValue.Resolved<PngSrgbMetadata>) {
                return ColorResolution(
                    embeddedIccProfile = embeddedIcc,
                    resolvedColorProfile = SkcmsICCProfile.fromColorProfile(ColorProfiles.sRGB()),
                    diagnostics = emptyList(),
                )
            }

            val chromaticities = metadata.cHRM as? PngMetadataValue.Resolved<PngChromaticitiesMetadata>
            val gamma = metadata.gAMA as? PngMetadataValue.Resolved<PngGammaMetadata>
            if (chromaticities != null && gamma != null) {
                return resolveChromaticityGamma(chromaticities, gamma, embeddedIcc)
            }

            return ColorResolution(
                embeddedIccProfile = embeddedIcc,
                resolvedColorProfile = null,
                diagnostics = emptyList(),
            )
        }

        private fun resolveCicpProfile(
            cicp: PngMetadataValue.Resolved<PngCicpMetadata>,
            embeddedIcc: SkcmsICCProfile?,
        ): ColorResolution = when (cicp.value.profileResolution) {
            PngCicpProfileResolution.RGB_PROFILE -> {
                if (cicp.value.info.fullRange) {
                    ColorResolution(
                        embeddedIccProfile = embeddedIcc,
                        resolvedColorProfile = SkcmsICCProfile.fromColorProfile(requireNotNull(cicp.value.profile)),
                        diagnostics = emptyList(),
                    )
                } else {
                    ColorResolution(
                        embeddedIccProfile = embeddedIcc,
                        resolvedColorProfile = SkcmsICCProfile.fromColorProfile(
                            ColorProfile.unsupported(CICP_NARROW_RANGE_UNSUPPORTED),
                        ),
                        diagnostics = listOf(
                            PngDiagnostic(
                                code = CICP_NARROW_RANGE_UNSUPPORTED,
                                offset = cicp.record.rawRange.startInclusive,
                                chunkType = cicp.record.type,
                                message = "PNG cICP narrow-range samples require a range transform that this codec does not implement",
                                severity = PngDiagnosticSeverity.REFUSAL,
                            ),
                        ),
                    )
                }
            }

            PngCicpProfileResolution.GRAYSCALE_INFO_ONLY -> ColorResolution(
                embeddedIccProfile = embeddedIcc,
                resolvedColorProfile = null,
                diagnostics = listOf(
                    PngDiagnostic(
                        code = "png.metadata.cICP.color-model.mismatch",
                        offset = cicp.record.rawRange.startInclusive,
                        chunkType = cicp.record.type,
                        message = "PNG cICP RGB profile semantics are not applied to grayscale samples",
                        severity = PngDiagnosticSeverity.REFUSAL,
                    ),
                ),
            )
        }

        private fun resolveChromaticityGamma(
            chromaticities: PngMetadataValue.Resolved<PngChromaticitiesMetadata>,
            gamma: PngMetadataValue.Resolved<PngGammaMetadata>,
            embeddedIcc: SkcmsICCProfile?,
        ): ColorResolution {
            val matrix = chromaticitiesToXyzD50(chromaticities.value)
            val exponent = GAMMA_SCALE / gamma.value.encodedGamma.toDouble()
            val profile = if (matrix != null && exponent.isFinite() && exponent > 0.0 && exponent <= Float.MAX_VALUE) {
                ColorProfile(
                    colorModel = ColorModel.RGB,
                    toXyzD50 = matrix,
                    transferFunction = SkcmsTransferFunction(
                        g = exponent.toFloat(),
                        a = 1f,
                        b = 0f,
                        c = 0f,
                        d = 0f,
                        e = 0f,
                        f = 0f,
                    ),
                )
            } else {
                ColorProfile.unsupported(CHRM_GAMMA_UNSUPPORTED)
            }
            val diagnostics = if (profile.unsupportedCode == null) {
                emptyList()
            } else {
                listOf(
                    PngDiagnostic(
                        code = CHRM_GAMMA_UNSUPPORTED,
                        offset = chromaticities.record.rawRange.startInclusive,
                        chunkType = chromaticities.record.type,
                        message = "PNG cHRM+gAMA could not be represented as a finite matrix/TRC profile",
                        severity = PngDiagnosticSeverity.REFUSAL,
                    ),
                )
            }
            return ColorResolution(
                embeddedIccProfile = embeddedIcc,
                resolvedColorProfile = SkcmsICCProfile.fromColorProfile(profile),
                diagnostics = diagnostics,
            )
        }

        private fun chromaticitiesToXyzD50(chromaticities: PngChromaticitiesMetadata): SkcmsMatrix3x3? {
            val white = chromaticityToXyz(chromaticities.whitePoint) ?: return null
            val red = chromaticityToXyz(chromaticities.red) ?: return null
            val green = chromaticityToXyz(chromaticities.green) ?: return null
            val blue = chromaticityToXyz(chromaticities.blue) ?: return null
            val primaries = doubleArrayOf(
                red[0], green[0], blue[0],
                red[1], green[1], blue[1],
                red[2], green[2], blue[2],
            )
            val scale = invert3x3(primaries)?.times(white) ?: return null
            if (scale.any { !it.isFinite() || it <= 0.0 }) return null
            val sourceToXyz = DoubleArray(9) { index -> primaries[index] * scale[index % 3] }
            val d50 = doubleArrayOf(
                SkNamedGamut.kSRGB[0, 0].toDouble() + SkNamedGamut.kSRGB[0, 1] + SkNamedGamut.kSRGB[0, 2],
                SkNamedGamut.kSRGB[1, 0].toDouble() + SkNamedGamut.kSRGB[1, 1] + SkNamedGamut.kSRGB[1, 2],
                SkNamedGamut.kSRGB[2, 0].toDouble() + SkNamedGamut.kSRGB[2, 1] + SkNamedGamut.kSRGB[2, 2],
            )
            val sourceCone = BRADFORD.times(white)
            val targetCone = BRADFORD.times(d50)
            if (sourceCone.any { !it.isFinite() || it == 0.0 }) return null
            val diagonal = DoubleArray(9)
            for (index in 0 until 3) diagonal[index * 3 + index] = targetCone[index] / sourceCone[index]
            val adapted = (
                BRADFORD_INVERSE * Matrix3x3D(diagonal) * BRADFORD * Matrix3x3D(sourceToXyz)
                ).toArray()
            if (adapted.any { !it.isFinite() || it < -Float.MAX_VALUE || it > Float.MAX_VALUE }) return null
            return SkcmsMatrix3x3.of(
                adapted[0].toFloat(), adapted[1].toFloat(), adapted[2].toFloat(),
                adapted[3].toFloat(), adapted[4].toFloat(), adapted[5].toFloat(),
                adapted[6].toFloat(), adapted[7].toFloat(), adapted[8].toFloat(),
            )
        }

        private fun chromaticityToXyz(chromaticity: PngChromaticity): DoubleArray? {
            val x = chromaticity.x.toDouble() / GAMMA_SCALE
            val y = chromaticity.y.toDouble() / GAMMA_SCALE
            val z = 1.0 - x - y
            if (!x.isFinite() || !y.isFinite() || x <= 0.0 || y <= 0.0 || z < 0.0) return null
            return doubleArrayOf(x / y, 1.0, z / y)
        }

        private fun hasPngSignature(data: ByteArray): Boolean {
            if (data.size < PNG_SIGNATURE.size) return false
            for (i in PNG_SIGNATURE.indices) {
                if (data[i] != PNG_SIGNATURE[i]) return false
            }
            return true
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
        val embeddedIccProfile: SkcmsICCProfile?,
        val resolvedColorProfile: SkcmsICCProfile?,
    )

    private data class ColorResolution(
        val embeddedIccProfile: SkcmsICCProfile?,
        val resolvedColorProfile: SkcmsICCProfile?,
        val diagnostics: List<PngDiagnostic>,
    )
}

/** Typed open result for callers that need an explicit static-PNG refusal diagnostic. */
public sealed interface PngCodecOpenResult {
    public data class Success(public val codec: PngCodec) : PngCodecOpenResult

    public data class Failure(public val diagnostic: PngDiagnostic) : PngCodecOpenResult
}

public class PngKotlinDecoderProvider : CodecDecoderProvider {
    override fun decoders(): List<Codec.Decoder> = listOf(PngCodec.Decoder)
}

private val PNG_SIGNATURE = byteArrayOf(
    0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
)
private val COLOR_PROFILE_CHUNK_TYPES: Set<String> = setOf("cICP", "iCCP", "sRGB", "cHRM", "gAMA")
private const val COLOR_GRAYSCALE: Int = 0
private const val COLOR_RGB: Int = 2
private const val COLOR_PALETTE: Int = 3
private const val COLOR_GRAYSCALE_ALPHA: Int = 4
private const val COLOR_RGBA: Int = 6
private const val INTERLACE_NONE: Int = 0
private const val INTERLACE_ADAM7: Int = 1
private const val MAX_DIMENSION: Int = 100_000
private const val MAX_ICC_PROFILE_SIZE: Int = 16 * 1024 * 1024
private const val MAX_OUTPUT_PIXELS: Long = Int.MAX_VALUE.toLong() / 4L
private const val MAX_OUTPUT_ALLOCATION_BYTES: Long = Int.MAX_VALUE.toLong()
private const val BITMAP_BYTES_PER_PIXEL: Long = 4L
private const val F16_BITMAP_BYTES_PER_PIXEL: Long = 20L
private const val GAMMA_SCALE: Double = 100_000.0
private const val CICP_NARROW_RANGE_UNSUPPORTED: String = "png.cicp.narrow-range.unsupported"
private const val CHRM_GAMMA_UNSUPPORTED: String = "png.chrm-gamma.unsupported"
private const val OPAQUE_BLACK: Int = 0xFF000000.toInt()

private class Matrix3x3D(private val values: DoubleArray) {
    init {
        require(values.size == 9)
    }

    operator fun times(other: Matrix3x3D): Matrix3x3D = Matrix3x3D(DoubleArray(9) { index ->
        val row = index / 3
        val column = index % 3
        values[row * 3] * other.values[column] +
            values[row * 3 + 1] * other.values[3 + column] +
            values[row * 3 + 2] * other.values[6 + column]
    })

    operator fun times(vector: DoubleArray): DoubleArray {
        require(vector.size == 3)
        return DoubleArray(3) { row ->
            values[row * 3] * vector[0] + values[row * 3 + 1] * vector[1] + values[row * 3 + 2] * vector[2]
        }
    }

    fun toArray(): DoubleArray = values.copyOf()
}

private fun invert3x3(values: DoubleArray): Matrix3x3D? {
    val a = values[0]
    val b = values[1]
    val c = values[2]
    val d = values[3]
    val e = values[4]
    val f = values[5]
    val g = values[6]
    val h = values[7]
    val i = values[8]
    val determinant = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g)
    if (!determinant.isFinite() || determinant == 0.0) return null
    val reciprocal = 1.0 / determinant
    val inverse = doubleArrayOf(
        (e * i - f * h) * reciprocal,
        (c * h - b * i) * reciprocal,
        (b * f - c * e) * reciprocal,
        (f * g - d * i) * reciprocal,
        (a * i - c * g) * reciprocal,
        (c * d - a * f) * reciprocal,
        (d * h - e * g) * reciprocal,
        (b * g - a * h) * reciprocal,
        (a * e - b * d) * reciprocal,
    )
    return inverse.takeIf { matrix -> matrix.all { it.isFinite() } }?.let(::Matrix3x3D)
}

private val BRADFORD: Matrix3x3D = Matrix3x3D(
    doubleArrayOf(
        0.8951, 0.2664, -0.1614,
        -0.7502, 1.7135, 0.0367,
        0.0389, -0.0685, 1.0296,
    ),
)

private val BRADFORD_INVERSE: Matrix3x3D = Matrix3x3D(
    doubleArrayOf(
        0.9869929, -0.1470543, 0.1599627,
        0.4323053, 0.5183603, 0.0492912,
        -0.0085287, 0.0400428, 0.9684867,
    ),
)

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
                count > 0 -> {
                    out.write(buffer, 0, count)
                    if (out.size() > expectedSize) throw DataFormatException("inflated data exceeds PNG scanline size")
                }
                inflater.needsInput() -> throw IncompleteZlibStreamException()
                inflater.needsDictionary() -> throw DataFormatException("PNG IDAT requires an unsupported zlib dictionary")
                else -> throw DataFormatException("zlib stream made no progress")
            }
        }
        if (inflater.remaining != 0) throw DataFormatException("PNG IDAT contains bytes after the zlib stream")
        return out.toByteArray()
    } finally {
        inflater.end()
    }
}

private class IncompleteZlibStreamException : Exception()

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

private fun argb(a: Int, r: Int, g: Int, b: Int): Int =
    ((a and 0xFF) shl 24) or
        ((r and 0xFF) shl 16) or
        ((g and 0xFF) shl 8) or
        (b and 0xFF)
