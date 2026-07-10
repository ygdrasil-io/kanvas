package org.graphiks.kanvas.codec.png

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.time.DateTimeException
import java.time.LocalDate
import java.util.Collections
import java.util.zip.DataFormatException
import java.util.zip.Inflater
import org.graphiks.kanvas.color.ColorModel
import org.graphiks.kanvas.color.ColorProfile
import org.graphiks.kanvas.color.ColorProfileParseResult
import org.graphiks.kanvas.color.cicp.CicpColorInfo
import org.graphiks.kanvas.color.cicp.toColorProfile
import org.graphiks.kanvas.color.icc.IccParseLimits
import org.graphiks.kanvas.color.icc.IccProfileParser

public data class PngMetadataLimits(
    public val maxTextBytes: Int = 1024 * 1024,
    public val maxInflatedTextBytes: Int = 1024 * 1024,
    public val maxIccProfileBytes: Int = 16 * 1024 * 1024,
    public val maxDecodedMetadataBytes: Int = 32 * 1024 * 1024,
    public val maxTextChunkCount: Int = 10_000,
    public val maxHistogramEntries: Int = 256,
    public val maxSuggestedPaletteCount: Int = 1_024,
    public val maxSuggestedPaletteEntries: Int = 65_536,
    public val maxSuggestedPaletteEntriesTotal: Int = 1_000_000,
) {
    init {
        require(maxTextBytes >= 0) { "maxTextBytes must be non-negative" }
        require(maxInflatedTextBytes >= 0) { "maxInflatedTextBytes must be non-negative" }
        require(maxIccProfileBytes >= 0) { "maxIccProfileBytes must be non-negative" }
        require(maxDecodedMetadataBytes >= 0) { "maxDecodedMetadataBytes must be non-negative" }
        require(maxTextChunkCount >= 0) { "maxTextChunkCount must be non-negative" }
        require(maxHistogramEntries >= 0) { "maxHistogramEntries must be non-negative" }
        require(maxSuggestedPaletteCount >= 0) { "maxSuggestedPaletteCount must be non-negative" }
        require(maxSuggestedPaletteEntries >= 0) { "maxSuggestedPaletteEntries must be non-negative" }
        require(maxSuggestedPaletteEntriesTotal >= 0) {
            "maxSuggestedPaletteEntriesTotal must be non-negative"
        }
    }

    public companion object {
        public val Default: PngMetadataLimits = PngMetadataLimits()
    }
}

/**
 * A typed metadata result associated with its authoritative raw chunk record.
 *
 * For an ancillary-edited [PngDocument], this record and any diagnostic offset
 * locate the bytes that its planned [PngDocument.save] output will contain.
 */
public sealed interface PngMetadataValue<out T> {
    public val record: PngChunkRecord

    public data class Resolved<out T>(
        override val record: PngChunkRecord,
        public val value: T,
    ) : PngMetadataValue<T>

    public data class Refused(
        override val record: PngChunkRecord,
        public val diagnostic: PngDiagnostic,
    ) : PngMetadataValue<Nothing>
}

public class PngMetadata internal constructor(
    public val iCCP: PngMetadataValue<PngIccProfileMetadata>?,
    public val sRGB: PngMetadataValue<PngSrgbMetadata>?,
    public val gAMA: PngMetadataValue<PngGammaMetadata>?,
    public val cHRM: PngMetadataValue<PngChromaticitiesMetadata>?,
    public val cICP: PngMetadataValue<PngCicpMetadata>?,
    public val mDCV: PngMetadataValue<PngMasteringDisplayColorVolumeMetadata>?,
    public val cLLI: PngMetadataValue<PngContentLightLevelMetadata>?,
    public val eXIf: PngMetadataValue<PngExifMetadata>?,
    public val pHYs: PngMetadataValue<PngPhysicalPixelDimensions>?,
    public val tIME: PngMetadataValue<PngModificationTime>?,
    tEXt: List<PngMetadataValue<PngTextMetadata>>,
    zTXt: List<PngMetadataValue<PngTextMetadata>>,
    iTXt: List<PngMetadataValue<PngInternationalTextMetadata>>,
    public val sBIT: PngMetadataValue<PngSignificantBitsMetadata>?,
    public val bKGD: PngMetadataValue<PngBackgroundColorMetadata>?,
    public val hIST: PngMetadataValue<PngHistogramMetadata>?,
    sPLT: List<PngMetadataValue<PngSuggestedPaletteMetadata>>,
    public val tRNS: PngMetadataValue<PngTransparencyMetadata>?,
    diagnostics: List<PngDiagnostic>,
) {
    public val tEXt: List<PngMetadataValue<PngTextMetadata>> = immutableList(tEXt)
    public val zTXt: List<PngMetadataValue<PngTextMetadata>> = immutableList(zTXt)
    public val iTXt: List<PngMetadataValue<PngInternationalTextMetadata>> = immutableList(iTXt)
    public val sPLT: List<PngMetadataValue<PngSuggestedPaletteMetadata>> = immutableList(sPLT)
    public val diagnostics: List<PngDiagnostic> = immutableList(diagnostics)
}

public data class PngIccProfileMetadata(
    public val profileName: String,
    public val profile: ColorProfile,
)

public data class PngSrgbMetadata(public val renderingIntent: Int)

public data class PngGammaMetadata(public val encodedGamma: Long)

public data class PngChromaticity(
    public val x: Long,
    public val y: Long,
)

public data class PngChromaticitiesMetadata(
    public val whitePoint: PngChromaticity,
    public val red: PngChromaticity,
    public val green: PngChromaticity,
    public val blue: PngChromaticity,
)

public enum class PngCicpProfileResolution {
    RGB_PROFILE,
    GRAYSCALE_INFO_ONLY,
}

public data class PngCicpMetadata(
    public val info: CicpColorInfo,
    public val profile: ColorProfile?,
    public val profileResolution: PngCicpProfileResolution,
)

public data class PngMasteringDisplayColorVolumeMetadata(
    public val primaries: List<PngChromaticity>,
    public val whitePoint: PngChromaticity,
    public val maximumLuminance: Long,
    public val minimumLuminance: Long,
)

public data class PngContentLightLevelMetadata(
    public val maximumContentLightLevel: Long,
    public val maximumFrameAverageLightLevel: Long,
)

public enum class PngExifByteOrder {
    LITTLE_ENDIAN,
    BIG_ENDIAN,
}

public data class PngExifMetadata(
    public val byteOrder: PngExifByteOrder,
    public val firstIfdOffset: Long,
)

public data class PngPhysicalPixelDimensions(
    public val pixelsPerUnitX: Long,
    public val pixelsPerUnitY: Long,
    public val unitSpecifier: Int,
)

public data class PngModificationTime(
    public val year: Int,
    public val month: Int,
    public val day: Int,
    public val hour: Int,
    public val minute: Int,
    public val second: Int,
)

public data class PngTextMetadata(
    public val keyword: String,
    public val text: String,
)

public data class PngInternationalTextMetadata(
    public val keyword: String,
    public val languageTag: String,
    public val translatedKeyword: String,
    public val text: String,
    public val isCompressed: Boolean,
)

public data class PngSignificantBitsMetadata(public val channelBits: List<Int>)

public data class PngBackgroundColorMetadata(
    public val grayscale: Int? = null,
    public val red: Int? = null,
    public val green: Int? = null,
    public val blue: Int? = null,
    public val paletteIndex: Int? = null,
)

public data class PngHistogramMetadata(public val frequencies: List<Int>)

public data class PngSuggestedPaletteEntry(
    public val red: Int,
    public val green: Int,
    public val blue: Int,
    public val alpha: Int,
    public val frequency: Int,
)

public data class PngSuggestedPaletteMetadata(
    public val name: String,
    public val sampleDepth: Int,
    public val entries: List<PngSuggestedPaletteEntry>,
)

public class PngTransparencyMetadata(
    public val grayscaleSample: Int? = null,
    public val redSample: Int? = null,
    public val greenSample: Int? = null,
    public val blueSample: Int? = null,
    paletteAlpha: List<Int>? = null,
) {
    public val paletteAlpha: List<Int>? = paletteAlpha?.let(::immutableList)
}

internal object PngMetadataParser {
    fun parse(
        data: ByteArray,
        container: PngContainer,
        limits: PngMetadataLimits,
        payloadSources: Map<Int, PngMetadataPayloadSource> = emptyMap(),
        recomputeStructuralDiagnostics: Boolean = false,
    ): PngMetadata = PngMetadataBuilder(
        data = data,
        container = container,
        limits = limits,
        payloadSources = payloadSources,
        recomputeStructuralDiagnostics = recomputeStructuralDiagnostics,
    ).parse()
}

/** A zero-copy payload backing used while metadata records project planned output ranges. */
internal class PngMetadataPayloadSource(
    val bytes: ByteArray,
    val offset: Int,
    val length: Int,
) {
    init {
        require(offset >= 0 && length >= 0 && offset <= bytes.size - length) {
            "PNG metadata payload source must be within its backing byte array"
        }
    }
}

private class PngMetadataBuilder(
    private val data: ByteArray,
    private val container: PngContainer,
    private val limits: PngMetadataLimits,
    private val payloadSources: Map<Int, PngMetadataPayloadSource>,
    recomputeStructuralDiagnostics: Boolean,
) {
    private var activePayload: MetadataPayload? = null
    private val structuralDiagnostics: List<PngDiagnostic> = if (recomputeStructuralDiagnostics) {
        collectStructuralDiagnostics()
    } else {
        container.metadataDiagnostics
    }
    private val diagnostics = ArrayList<PngDiagnostic>(structuralDiagnostics)
    private val decodedMetadataBudget = MetadataBudget(limits.maxDecodedMetadataBytes)
    private val text = ArrayList<PngMetadataValue<PngTextMetadata>>()
    private val compressedText = ArrayList<PngMetadataValue<PngTextMetadata>>()
    private val internationalText = ArrayList<PngMetadataValue<PngInternationalTextMetadata>>()
    private val suggestedPalettes = ArrayList<PngMetadataValue<PngSuggestedPaletteMetadata>>()
    private val paletteEntries: Int? = container.chunks.singleOrNull { it.type == "PLTE" }
        ?.payloadRange
        ?.size
        ?.let { size -> (size / 3L).toInt() }

    private var iccp: PngMetadataValue<PngIccProfileMetadata>? = null
    private var srgb: PngMetadataValue<PngSrgbMetadata>? = null
    private var gamma: PngMetadataValue<PngGammaMetadata>? = null
    private var chromaticities: PngMetadataValue<PngChromaticitiesMetadata>? = null
    private var cicp: PngMetadataValue<PngCicpMetadata>? = null
    private var masteringDisplay: PngMetadataValue<PngMasteringDisplayColorVolumeMetadata>? = null
    private var contentLight: PngMetadataValue<PngContentLightLevelMetadata>? = null
    private var exif: PngMetadataValue<PngExifMetadata>? = null
    private var physicalDimensions: PngMetadataValue<PngPhysicalPixelDimensions>? = null
    private var modificationTime: PngMetadataValue<PngModificationTime>? = null
    private var significantBits: PngMetadataValue<PngSignificantBitsMetadata>? = null
    private var background: PngMetadataValue<PngBackgroundColorMetadata>? = null
    private var histogram: PngMetadataValue<PngHistogramMetadata>? = null
    private var transparency: PngMetadataValue<PngTransparencyMetadata>? = null
    private var textChunkCount: Int = 0
    private var suggestedPaletteCount: Int = 0
    private var suggestedPaletteEntriesTotal: Int = 0

    fun parse(): PngMetadata {
        for (record in container.chunks) {
            when (record.type) {
                "iCCP" -> iccp = decode(record) { parseIccProfile(record) }
                "sRGB" -> srgb = decode(record) { parseSrgb(record) }
                "gAMA" -> gamma = decode(record) { parseGamma(record) }
                "cHRM" -> chromaticities = decode(record) { parseChromaticities(record) }
                "cICP" -> cicp = decode(record) { parseCicp(record) }
                "mDCV" -> masteringDisplay = decode(record) { parseMasteringDisplay(record) }
                "cLLI" -> contentLight = decode(record) { parseContentLight(record) }
                "eXIf" -> exif = decode(record) { parseExif(record) }
                "pHYs" -> physicalDimensions = decode(record) { parsePhysicalDimensions(record) }
                "tIME" -> modificationTime = decode(record) { parseModificationTime(record) }
                "tEXt" -> text += decode(record) { reserveTextChunk(); parseText(record) }
                "zTXt" -> compressedText += decode(record) { reserveTextChunk(); parseCompressedText(record) }
                "iTXt" -> internationalText += decode(record) { reserveTextChunk(); parseInternationalText(record) }
                "sBIT" -> significantBits = decode(record) { parseSignificantBits(record) }
                "bKGD" -> background = decode(record) { parseBackground(record) }
                "hIST" -> histogram = decode(record) { parseHistogram(record) }
                "sPLT" -> suggestedPalettes += decode(record) { parseSuggestedPalette(record) }
                "tRNS" -> transparency = decode(record) { parseTransparency(record) }
            }
        }
        return PngMetadata(
            iCCP = iccp,
            sRGB = srgb,
            gAMA = gamma,
            cHRM = chromaticities,
            cICP = cicp,
            mDCV = masteringDisplay,
            cLLI = contentLight,
            eXIf = exif,
            pHYs = physicalDimensions,
            tIME = modificationTime,
            tEXt = text,
            zTXt = compressedText,
            iTXt = internationalText,
            sBIT = significantBits,
            bKGD = background,
            hIST = histogram,
            sPLT = suggestedPalettes,
            tRNS = transparency,
            diagnostics = diagnostics,
        )
    }

    private fun parseIccProfile(record: PngChunkRecord): PngIccProfileMetadata {
        val payload = payload(record)
        val separator = zeroSeparator(payload.start, payload.end, KEYWORD_MAX_BYTES, "layout")
        val profileName = latin1Keyword(payload.start, separator)
        if (separator + 1 >= payload.end) abort("layout", "iCCP has no compression method or profile")
        if (u8(separator + 1) != 0) abort("compression.method", "iCCP compression method must be zero")
        val profileBytes = inflate(
            start = separator + 2,
            end = payload.end,
            maxBytes = limits.maxIccProfileBytes,
            subject = "profile",
        )
        val profile = when (val result = IccProfileParser.parse(profileBytes, IccParseLimits(maxBytes = limits.maxIccProfileBytes))) {
            is ColorProfileParseResult.Success -> result.profile
            is ColorProfileParseResult.Failure -> abort("resolution.${result.code}", result.message)
        }
        val expectedModel = when (container.header.colorType) {
            0, 4 -> ColorModel.GRAY
            2, 3, 6 -> ColorModel.RGB
            else -> abort("color-model.unsupported", "PNG color type is unsupported for iCCP resolution")
        }
        if (profile.colorModel != expectedModel) {
            abort("color-model.mismatch", "iCCP profile color model does not match the PNG color type")
        }
        return PngIccProfileMetadata(profileName, profile)
    }

    private fun parseSrgb(record: PngChunkRecord): PngSrgbMetadata {
        val payload = payload(record)
        requireLength(payload, 1)
        val intent = u8(payload.start)
        if (intent !in 0..3) abort("intent.invalid", "sRGB rendering intent must be between zero and three")
        return PngSrgbMetadata(intent)
    }

    private fun parseGamma(record: PngChunkRecord): PngGammaMetadata {
        val payload = payload(record)
        requireLength(payload, 4)
        val encoded = u32(payload.start)
        if (encoded == 0L) abort("value.invalid", "gAMA image gamma must be non-zero")
        return PngGammaMetadata(encoded)
    }

    private fun parseChromaticities(record: PngChunkRecord): PngChromaticitiesMetadata {
        val payload = payload(record)
        requireLength(payload, 32)
        fun chromaticity(offset: Int): PngChromaticity = PngChromaticity(u32(offset), u32(offset + 4))
        return PngChromaticitiesMetadata(
            whitePoint = chromaticity(payload.start),
            red = chromaticity(payload.start + 8),
            green = chromaticity(payload.start + 16),
            blue = chromaticity(payload.start + 24),
        )
    }

    private fun parseCicp(record: PngChunkRecord): PngCicpMetadata {
        val payload = payload(record)
        requireLength(payload, 4)
        val matrix = u8(payload.start + 2)
        val fullRange = u8(payload.start + 3)
        if (matrix != 0) abort("matrix.unsupported", "PNG cICP matrix coefficients must be zero")
        if (fullRange !in 0..1) abort("range.invalid", "PNG cICP full-range flag must be zero or one")
        val info = CicpColorInfo(
            primaries = u8(payload.start),
            transfer = u8(payload.start + 1),
            matrix = matrix,
            fullRange = fullRange == 1,
        )
        if (container.header.colorType in setOf(0, 4)) {
            return PngCicpMetadata(
                info = info,
                profile = null,
                profileResolution = PngCicpProfileResolution.GRAYSCALE_INFO_ONLY,
            )
        }
        if (container.header.colorType !in setOf(2, 3, 6)) {
            abort("color-model.unsupported", "PNG cICP resolution does not support this PNG color type")
        }
        val profile = when (val result = info.toColorProfile()) {
            is ColorProfileParseResult.Success -> result.profile
            is ColorProfileParseResult.Failure -> abort("resolution.${result.code}", result.message)
        }
        return PngCicpMetadata(info, profile, PngCicpProfileResolution.RGB_PROFILE)
    }

    private fun parseMasteringDisplay(record: PngChunkRecord): PngMasteringDisplayColorVolumeMetadata {
        val payload = payload(record)
        requireLength(payload, 24)
        val primaries = listOf(
            PngChromaticity(u16(payload.start), u16(payload.start + 2)),
            PngChromaticity(u16(payload.start + 4), u16(payload.start + 6)),
            PngChromaticity(u16(payload.start + 8), u16(payload.start + 10)),
        )
        if (primaries[0].x < maxOf(primaries[1].x, primaries[2].x) || primaries[1].y < primaries[2].y) {
            abort(
                "primaries.order",
                "mDCV primaries must place the greatest x first and the greatest remaining y second",
            )
        }
        return PngMasteringDisplayColorVolumeMetadata(
            primaries = immutableList(primaries),
            whitePoint = PngChromaticity(u16(payload.start + 12), u16(payload.start + 14)),
            maximumLuminance = u32(payload.start + 16),
            minimumLuminance = u32(payload.start + 20),
        )
    }

    private fun parseContentLight(record: PngChunkRecord): PngContentLightLevelMetadata {
        val payload = payload(record)
        requireLength(payload, 8)
        return PngContentLightLevelMetadata(u32(payload.start), u32(payload.start + 4))
    }

    private fun parseExif(record: PngChunkRecord): PngExifMetadata {
        val payload = payload(record)
        if (payload.length < 8) abort("header.length", "eXIf TIFF header must contain at least eight bytes")
        return when {
            u8(payload.start) == 'I'.code && u8(payload.start + 1) == 'I'.code &&
                u8(payload.start + 2) == 42 && u8(payload.start + 3) == 0 ->
                PngExifMetadata(PngExifByteOrder.LITTLE_ENDIAN, u32LittleEndian(payload.start + 4))

            u8(payload.start) == 'M'.code && u8(payload.start + 1) == 'M'.code &&
                u8(payload.start + 2) == 0 && u8(payload.start + 3) == 42 ->
                PngExifMetadata(PngExifByteOrder.BIG_ENDIAN, u32(payload.start + 4))

            else -> abort("header.invalid", "eXIf must begin with a TIFF byte-order marker and version 42")
        }
    }

    private fun parsePhysicalDimensions(record: PngChunkRecord): PngPhysicalPixelDimensions {
        val payload = payload(record)
        requireLength(payload, 9)
        val unit = u8(payload.start + 8)
        if (unit !in 0..1) abort("unit.invalid", "pHYs unit specifier must be zero or one")
        return PngPhysicalPixelDimensions(u32(payload.start), u32(payload.start + 4), unit)
    }

    private fun parseModificationTime(record: PngChunkRecord): PngModificationTime {
        val payload = payload(record)
        requireLength(payload, 7)
        val year = u16(payload.start).toInt()
        val month = u8(payload.start + 2)
        val day = u8(payload.start + 3)
        val hour = u8(payload.start + 4)
        val minute = u8(payload.start + 5)
        val second = u8(payload.start + 6)
        if (hour !in 0..23 || minute !in 0..59 || second !in 0..60) {
            abort("value.invalid", "tIME contains an invalid clock value")
        }
        try {
            LocalDate.of(year, month, day)
        } catch (_: DateTimeException) {
            abort("date.invalid", "tIME contains an invalid calendar date")
        }
        return PngModificationTime(year, month, day, hour, minute, second)
    }

    private fun parseText(record: PngChunkRecord): PngTextMetadata {
        val payload = payload(record)
        val separator = zeroSeparator(payload.start, payload.end, KEYWORD_MAX_BYTES, "layout")
        return PngTextMetadata(latin1Keyword(payload.start, separator), latin1Text(separator + 1, payload.end))
    }

    private fun parseCompressedText(record: PngChunkRecord): PngTextMetadata {
        val payload = payload(record)
        val separator = zeroSeparator(payload.start, payload.end, KEYWORD_MAX_BYTES, "layout")
        if (separator + 1 >= payload.end) abort("layout", "zTXt has no compression method or text")
        if (u8(separator + 1) != 0) abort("compression.method", "zTXt compression method must be zero")
        val inflated = inflate(separator + 2, payload.end, maximumInflatedTextBytes(), "text")
        return PngTextMetadata(
            latin1Keyword(payload.start, separator),
            latin1Text(inflated, 0, inflated.size, alreadyReserved = true),
        )
    }

    private fun parseInternationalText(record: PngChunkRecord): PngInternationalTextMetadata {
        val payload = payload(record)
        val keywordEnd = zeroSeparator(payload.start, payload.end, KEYWORD_MAX_BYTES, "layout")
        if (keywordEnd + 2 >= payload.end) abort("layout", "iTXt lacks compression fields")
        val compressionFlag = u8(keywordEnd + 1)
        val compressionMethod = u8(keywordEnd + 2)
        if (compressionFlag !in 0..1) abort("compression.flag", "iTXt compression flag must be zero or one")
        if (compressionFlag == 1 && compressionMethod != 0) {
            abort("compression.method", "iTXt compression method must be zero when compressed")
        }
        val languageStart = keywordEnd + 3
        val languageEnd = zeroSeparator(languageStart, payload.end, limits.maxTextBytes, "text.limit")
        val translatedStart = languageEnd + 1
        val translatedEnd = zeroSeparator(translatedStart, payload.end, limits.maxTextBytes, "text.limit")
        val textStart = translatedEnd + 1
        val text = if (compressionFlag == 1) {
            val textBytes = inflate(textStart, payload.end, maximumInflatedTextBytes(), "text")
            utf8(textBytes, 0, textBytes.size, alreadyReserved = true)
        } else {
            utf8(textStart, payload.end)
        }
        return PngInternationalTextMetadata(
            keyword = latin1Keyword(payload.start, keywordEnd),
            languageTag = languageTag(languageStart, languageEnd),
            translatedKeyword = utf8(translatedStart, translatedEnd),
            text = text,
            isCompressed = compressionFlag == 1,
        )
    }

    private fun parseSignificantBits(record: PngChunkRecord): PngSignificantBitsMetadata {
        val payload = payload(record)
        val channels = when (container.header.colorType) {
            0 -> 1
            2, 3 -> 3
            4 -> 2
            6 -> 4
            else -> abort("color-type.unsupported", "sBIT has no layout for this PNG color type")
        }
        requireLength(payload, channels)
        val maximum = if (container.header.colorType == 3) 8 else container.header.bitDepth
        val values = (0 until channels).map { index -> u8(payload.start + index) }
        if (values.any { it !in 1..maximum }) {
            abort("value.invalid", "sBIT values must be between one and the sample depth")
        }
        return PngSignificantBitsMetadata(immutableList(values))
    }

    private fun parseBackground(record: PngChunkRecord): PngBackgroundColorMetadata {
        val payload = payload(record)
        val maximum = (1 shl container.header.bitDepth) - 1
        fun normalizedSample(offset: Int): Int = u16(offset).toInt() and maximum
        return when (container.header.colorType) {
            0, 4 -> {
                requireLength(payload, 2)
                PngBackgroundColorMetadata(grayscale = normalizedSample(payload.start))
            }

            2, 6 -> {
                requireLength(payload, 6)
                PngBackgroundColorMetadata(
                    red = normalizedSample(payload.start),
                    green = normalizedSample(payload.start + 2),
                    blue = normalizedSample(payload.start + 4),
                )
            }

            3 -> {
                requireLength(payload, 1)
                val entries = paletteEntries ?: abort("palette.required", "bKGD indexed PNG requires a valid PLTE chunk")
                val index = u8(payload.start)
                if (index >= entries) abort("palette.index", "bKGD palette index is outside PLTE")
                PngBackgroundColorMetadata(paletteIndex = index)
            }

            else -> abort("color-type.unsupported", "bKGD has no layout for this PNG color type")
        }
    }

    private fun parseHistogram(record: PngChunkRecord): PngHistogramMetadata {
        val payload = payload(record)
        val entries = paletteEntries ?: abort("palette.required", "hIST requires a valid PLTE chunk")
        if (entries > limits.maxHistogramEntries) {
            abort("entries.limit", "hIST entry count exceeds the configured limit")
        }
        if (payload.length != entries * 2) abort("length", "hIST must contain one 16-bit frequency per PLTE entry")
        reserveDecoded(entries.toLong() * HISTOGRAM_ENTRY_BUDGET_BYTES)
        val frequencies = ArrayList<Int>(entries)
        repeat(entries) { index -> frequencies += u16(payload.start + index * 2).toInt() }
        return PngHistogramMetadata(immutableList(frequencies))
    }

    private fun parseSuggestedPalette(record: PngChunkRecord): PngSuggestedPaletteMetadata {
        val payload = payload(record)
        val nameEnd = zeroSeparator(payload.start, payload.end, KEYWORD_MAX_BYTES, "layout")
        val name = latin1Keyword(payload.start, nameEnd)
        if (nameEnd + 1 >= payload.end) abort("layout", "sPLT has no sample depth")
        val sampleDepth = u8(nameEnd + 1)
        val entrySize = when (sampleDepth) {
            8 -> 6
            16 -> 10
            else -> abort("sample-depth.invalid", "sPLT sample depth must be eight or sixteen")
        }
        val entriesStart = nameEnd + 2
        val remaining = payload.end - entriesStart
        if (remaining % entrySize != 0) abort("layout", "sPLT entries do not match the sample depth")
        val entryCount = remaining / entrySize
        if (entryCount > limits.maxSuggestedPaletteEntries) {
            abort("entries.limit", "sPLT entry count exceeds the configured limit")
        }
        reserveSuggestedPalette(entryCount)
        var previousFrequency = Int.MAX_VALUE
        val entries = ArrayList<PngSuggestedPaletteEntry>(entryCount)
        repeat(entryCount) { index ->
            val offset = entriesStart + index * entrySize
            val componentSize = if (sampleDepth == 8) 1 else 2
            val red = component(offset, componentSize)
            val green = component(offset + componentSize, componentSize)
            val blue = component(offset + componentSize * 2, componentSize)
            val alpha = component(offset + componentSize * 3, componentSize)
            val frequency = u16(offset + componentSize * 4).toInt()
            if (frequency > previousFrequency) abort("frequency.order", "sPLT frequencies must be non-increasing")
            previousFrequency = frequency
            entries += PngSuggestedPaletteEntry(red, green, blue, alpha, frequency)
        }
        return PngSuggestedPaletteMetadata(name, sampleDepth, immutableList(entries))
    }

    private fun parseTransparency(record: PngChunkRecord): PngTransparencyMetadata {
        val payload = payload(record)
        val maximumSample = (1 shl container.header.bitDepth) - 1
        fun normalizedSample(offset: Int): Int = u16(offset).toInt() and maximumSample
        return when (container.header.colorType) {
            0 -> {
                requireLength(payload, 2)
                PngTransparencyMetadata(grayscaleSample = normalizedSample(payload.start))
            }

            2 -> {
                requireLength(payload, 6)
                PngTransparencyMetadata(
                    redSample = normalizedSample(payload.start),
                    greenSample = normalizedSample(payload.start + 2),
                    blueSample = normalizedSample(payload.start + 4),
                )
            }

            3 -> {
                val entries = paletteEntries ?: abort("palette.required", "tRNS indexed PNG requires a valid PLTE chunk")
                if (payload.length !in 1..entries) {
                    abort("palette.length", "tRNS alpha entries must cover one to all PLTE entries")
                }
                reserveDecoded(payload.length.toLong() * TRANSPARENCY_ENTRY_BUDGET_BYTES)
                val alpha = ArrayList<Int>(payload.length)
                repeat(payload.length) { index -> alpha += u8(payload.start + index) }
                PngTransparencyMetadata(paletteAlpha = alpha)
            }

            else -> abort("color-type.invalid", "tRNS is not permitted for PNG color types with alpha")
        }
    }

    private fun collectStructuralDiagnostics(): List<PngDiagnostic> {
        val collected = ArrayList<PngDiagnostic>()
        val counts = HashMap<String, Int>()
        val suggestedPaletteNames = HashSet<String>()
        val backgroundBeforePalette = ArrayList<PngChunkRecord>()
        val transparencyBeforePalette = ArrayList<PngChunkRecord>()
        val masteringDisplay = ArrayList<PngChunkRecord>()
        var sawPalette = false
        var sawIdat = false
        var sawCicp = false

        fun refusal(record: PngChunkRecord, code: String, message: String) {
            collected += PngDiagnostic(
                code = code,
                offset = record.rawRange.startInclusive,
                chunkType = record.type,
                message = message,
                severity = PngDiagnosticSeverity.REFUSAL,
            )
        }

        for (record in container.chunks) {
            when (record.type) {
                "PLTE" -> {
                    for (background in backgroundBeforePalette) {
                        refusal(background, "png.metadata.bKGD.order", "bKGD must follow PLTE when PLTE is present")
                    }
                    for (transparency in transparencyBeforePalette) {
                        refusal(transparency, "png.metadata.tRNS.order", "tRNS must follow PLTE when PLTE is present")
                    }
                    sawPalette = true
                }

                "IDAT" -> sawIdat = true

                else -> if (record.isAncillary) {
                    if (record.type in STRUCTURAL_SINGLETON_TYPES) {
                        val count = (counts[record.type] ?: 0) + 1
                        counts[record.type] = count
                        if (count > 1) {
                            refusal(
                                record,
                                "png.metadata.${record.type}.duplicate",
                                "PNG static metadata chunk ${record.type} may occur only once",
                            )
                        }
                    }
                    if (record.type in STRUCTURAL_PRE_PALETTE_AND_IDAT_TYPES && (sawPalette || sawIdat)) {
                        refusal(
                            record,
                            "png.metadata.${record.type}.order",
                            "PNG metadata chunk ${record.type} must precede PLTE and IDAT",
                        )
                    }
                    if (record.type in STRUCTURAL_PRE_IDAT_TYPES && sawIdat) {
                        refusal(
                            record,
                            "png.metadata.${record.type}.order",
                            "PNG metadata chunk ${record.type} must precede IDAT",
                        )
                    }
                    when (record.type) {
                        "bKGD" -> if (!sawPalette) {
                            if (container.header.colorType == INDEXED_COLOR_TYPE) {
                                refusal(record, "png.metadata.bKGD.order", "bKGD must follow PLTE for indexed PNG")
                            } else {
                                backgroundBeforePalette += record
                            }
                        }

                        "hIST" -> if (!sawPalette) {
                            refusal(record, "png.metadata.hIST.plte.required", "hIST requires a preceding PLTE chunk")
                        }

                        "mDCV" -> masteringDisplay += record
                        "cICP" -> sawCicp = true
                        "sPLT" -> structuralSuggestedPaletteName(record)?.let { name ->
                            if (!suggestedPaletteNames.add(name)) {
                                refusal(record, "png.metadata.sPLT.name.duplicate", "sPLT palette names must be distinct")
                            }
                        }

                        "tRNS" -> if (!sawPalette) {
                            if (container.header.colorType == INDEXED_COLOR_TYPE) {
                                refusal(record, "png.metadata.tRNS.order", "tRNS must follow PLTE for indexed PNG")
                            } else {
                                transparencyBeforePalette += record
                            }
                        }
                    }
                }
            }
        }
        if (!sawCicp) {
            for (record in masteringDisplay) {
                refusal(record, "png.metadata.mDCV.cicp.required", "mDCV requires an accompanying cICP chunk")
            }
        }
        return immutableList(collected)
    }

    private fun structuralSuggestedPaletteName(record: PngChunkRecord): String? {
        val payload = payload(record)
        val maximumEnd = minOf(payload.end, KEYWORD_MAX_BYTES + 1)
        var separator = payload.start
        while (separator < maximumEnd && u8(separator) != 0) separator++
        if (separator == payload.start || separator == maximumEnd) return null

        var previousWasSpace = false
        for (offset in payload.start until separator) {
            val value = u8(offset)
            val isSpace = value == ' '.code
            if ((value !in 0x20..0x7e && value !in 0xa1..0xff) ||
                value == 0xa0 ||
                (isSpace && (offset == payload.start || offset == separator - 1 || previousWasSpace))
            ) {
                return null
            }
            previousWasSpace = isSpace
        }
        return requireActivePayload().latin1String(payload.start, separator - payload.start)
    }

    private fun component(offset: Int, size: Int): Int = if (size == 1) u8(offset) else u16(offset).toInt()

    private fun <T> decode(record: PngChunkRecord, block: () -> T): PngMetadataValue<T> {
        val structuralDiagnostic = structuralDiagnostics.firstOrNull {
            it.offset == record.rawRange.startInclusive && it.chunkType == record.type
        }
        if (structuralDiagnostic != null) return PngMetadataValue.Refused(record, structuralDiagnostic)

        return try {
            PngMetadataValue.Resolved(record, block())
        } catch (refusal: MetadataRefusal) {
            val diagnostic = PngDiagnostic(
                code = "png.metadata.${record.type}.${refusal.code}",
                offset = record.rawRange.startInclusive,
                chunkType = record.type,
                message = refusal.message ?: "PNG metadata could not be resolved",
                severity = PngDiagnosticSeverity.REFUSAL,
            )
            diagnostics += diagnostic
            PngMetadataValue.Refused(record, diagnostic)
        }
    }

    private fun reserveTextChunk() {
        if (textChunkCount >= limits.maxTextChunkCount) {
            abort("count.limit", "PNG repeatable text chunk count exceeds the configured limit")
        }
        textChunkCount++
    }

    private fun reserveSuggestedPalette(entryCount: Int) {
        if (suggestedPaletteCount >= limits.maxSuggestedPaletteCount) {
            abort("count.limit", "sPLT chunk count exceeds the configured limit")
        }
        val totalEntries = suggestedPaletteEntriesTotal.toLong() + entryCount.toLong()
        if (totalEntries > limits.maxSuggestedPaletteEntriesTotal.toLong()) {
            abort("entries.total.limit", "sPLT entries exceed the configured document limit")
        }
        reserveDecoded(entryCount.toLong() * SUGGESTED_PALETTE_ENTRY_BUDGET_BYTES)
        suggestedPaletteCount++
        suggestedPaletteEntriesTotal = totalEntries.toInt()
    }

    private fun reserveDecoded(bytes: Long) {
        if (!decodedMetadataBudget.reserve(bytes)) {
            abort("decoded.limit", "Decoded PNG metadata exceeds the configured document limit")
        }
    }

    private fun payload(record: PngChunkRecord): PayloadRange {
        val source = payloadSources[record.ordinal]
        val payload = if (source != null) {
            MetadataPayload(source.bytes, source.offset, source.length)
        } else {
            val start = record.payloadRange.startInclusive.toInt()
            MetadataPayload(data, start, record.payloadRange.size.toInt())
        }
        activePayload = payload
        return PayloadRange(0, payload.size)
    }

    private fun requireLength(payload: PayloadRange, expected: Int) {
        if (payload.length != expected) abort("length", "PNG metadata payload must contain $expected bytes")
    }

    private fun zeroSeparator(start: Int, end: Int, maximumBytes: Int, missingCode: String): Int {
        val boundedEnd = minOf(end.toLong(), start.toLong() + maximumBytes.toLong() + 1L).toInt()
        for (offset in start until boundedEnd) if (u8(offset) == 0) return offset
        if (end - start > maximumBytes) abort(missingCode, "PNG metadata string exceeds the configured limit")
        abort("layout", "PNG metadata string has no null separator")
    }

    private fun latin1Keyword(start: Int, end: Int): String {
        val length = end - start
        if (length !in 1..KEYWORD_MAX_BYTES) abort("keyword.invalid", "PNG metadata keyword must contain one to 79 bytes")
        var previousWasSpace = false
        for (offset in start until end) {
            val value = u8(offset)
            val isSpace = value == ' '.code
            if ((value !in 0x20..0x7e && value !in 0xa1..0xff) ||
                value == 0xa0 ||
                (isSpace && (offset == start || offset == end - 1 || previousWasSpace))
            ) {
                abort("keyword.invalid", "PNG metadata keyword contains an invalid Latin-1 sequence")
            }
            previousWasSpace = isSpace
        }
        reserveDecoded(length.toLong())
        return requireActivePayload().latin1String(start, length)
    }

    private fun latin1Text(start: Int, end: Int): String {
        val length = end - start
        if (length > limits.maxTextBytes) abort("text.limit", "PNG text exceeds the configured limit")
        reserveDecoded(length.toLong())
        for (offset in start until end) {
            val value = u8(offset)
            if (value != '\n'.code && value !in 0x20..0x7e && value !in 0xa1..0xff) {
                abort("text.invalid", "PNG text contains a disallowed Latin-1 control character")
            }
        }
        return requireActivePayload().latin1String(start, length)
    }

    private fun latin1Text(
        bytes: ByteArray,
        start: Int,
        length: Int,
        alreadyReserved: Boolean = false,
    ): String {
        if (length > limits.maxTextBytes) abort("text.limit", "PNG text exceeds the configured limit")
        if (!alreadyReserved) reserveDecoded(length.toLong())
        for (offset in start until start + length) {
            val value = bytes[offset].toInt() and 0xff
            if (value != '\n'.code && value !in 0x20..0x7e && value !in 0xa1..0xff) {
                abort("text.invalid", "PNG text contains a disallowed Latin-1 control character")
            }
        }
        return String(bytes, start, length, Charsets.ISO_8859_1)
    }

    private fun languageTag(start: Int, end: Int): String {
        if (end - start > limits.maxTextBytes) abort("text.limit", "iTXt language tag exceeds the configured limit")
        for (offset in start until end) {
            val value = u8(offset)
            if (value !in 'A'.code..'Z'.code && value !in 'a'.code..'z'.code &&
                value !in '0'.code..'9'.code && value != '-'.code
            ) {
                abort("language.invalid", "iTXt language tag must be ASCII alphanumeric subtags")
            }
        }
        reserveDecoded((end - start).toLong())
        val tag = requireActivePayload().asciiString(start, end - start)
        if (!tag.isWellFormedBcp47LanguageTag()) {
            abort("language.invalid", "iTXt language tag is not well-formed BCP 47 syntax")
        }
        return tag
    }

    private fun utf8(start: Int, end: Int): String {
        val length = end - start
        if (length > limits.maxTextBytes) abort("text.limit", "UTF-8 text exceeds the configured limit")
        reserveDecoded(length.toLong())
        for (offset in start until end) {
            if (u8(offset) == 0) abort("text.nul", "iTXt text must not contain null bytes")
        }
        return decodeUtf8(requireActivePayload().byteBuffer(start, length))
    }

    private fun utf8(
        bytes: ByteArray,
        start: Int,
        length: Int,
        alreadyReserved: Boolean = false,
    ): String {
        if (length > limits.maxTextBytes) abort("text.limit", "UTF-8 text exceeds the configured limit")
        if (!alreadyReserved) reserveDecoded(length.toLong())
        for (offset in start until start + length) {
            if (bytes[offset] == 0.toByte()) abort("text.nul", "iTXt text must not contain null bytes")
        }
        return decodeUtf8(ByteBuffer.wrap(bytes, start, length))
    }

    private fun maximumInflatedTextBytes(): Int = minOf(limits.maxInflatedTextBytes, limits.maxTextBytes)

    private fun inflate(start: Int, end: Int, maxBytes: Int, subject: String): ByteArray {
        val inflater = Inflater()
        val output = BoundedBytes(maxBytes)
        try {
            val input = requireActivePayload().input(start, end - start)
            inflater.setInput(input.bytes, input.offset, input.length)
            val buffer = ByteArray(INFLATE_BUFFER_BYTES)
            while (!inflater.finished()) {
                val count = try {
                    inflater.inflate(buffer)
                } catch (_: DataFormatException) {
                    abort("compression.invalid", "PNG metadata has an invalid zlib stream")
                }
                when {
                    count > 0 -> {
                        reserveDecoded(count.toLong())
                        if (!output.append(buffer, count)) {
                            abort("$subject.limit", "Inflated PNG metadata exceeds the configured limit")
                        }
                    }

                    inflater.needsDictionary() -> abort("compression.dictionary", "PNG metadata zlib stream requires a dictionary")
                    inflater.needsInput() -> abort("compression.truncated", "PNG metadata zlib stream is truncated")
                    else -> abort("compression.invalid", "PNG metadata zlib stream made no progress")
                }
            }
            if (inflater.remaining != 0) abort("compression.trailing", "PNG metadata zlib stream has trailing bytes")
            return output.toByteArray()
        } finally {
            inflater.end()
        }
    }

    private fun u8(offset: Int): Int = requireActivePayload().u8(offset)

    private fun u16(offset: Int): Long = ((u8(offset) shl 8) or u8(offset + 1)).toLong()

    private fun u32(offset: Int): Long =
        (u8(offset).toLong() shl 24) or
            (u8(offset + 1).toLong() shl 16) or
            (u8(offset + 2).toLong() shl 8) or
            u8(offset + 3).toLong()

    private fun u32LittleEndian(offset: Int): Long =
        u8(offset).toLong() or
            (u8(offset + 1).toLong() shl 8) or
            (u8(offset + 2).toLong() shl 16) or
            (u8(offset + 3).toLong() shl 24)

    private fun abort(code: String, message: String): Nothing = throw MetadataRefusal(code, message)

    private fun requireActivePayload(): MetadataPayload = checkNotNull(activePayload) {
        "PNG metadata byte access requires an active chunk payload"
    }

    private fun decodeUtf8(buffer: ByteBuffer): String = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(buffer)
            .toString()
    } catch (_: CharacterCodingException) {
        abort("utf8.invalid", "iTXt contains malformed UTF-8")
    }

    private data class PayloadRange(val start: Int, val end: Int) {
        val length: Int
            get() = end - start
    }

    private class MetadataRefusal(val code: String, message: String) : RuntimeException(message)
}

private class MetadataPayload(
    private val bytes: ByteArray,
    private val start: Int,
    val size: Int,
) {
    init {
        require(start >= 0 && size >= 0 && start <= bytes.size - size) {
            "PNG metadata payload must be within its backing byte array"
        }
    }

    fun u8(offset: Int): Int = bytes[start + offset].toInt() and 0xFF

    fun latin1String(offset: Int, length: Int): String = String(bytes, start + offset, length, Charsets.ISO_8859_1)

    fun asciiString(offset: Int, length: Int): String = String(bytes, start + offset, length, Charsets.US_ASCII)

    fun byteBuffer(offset: Int, length: Int): ByteBuffer = ByteBuffer.wrap(bytes, start + offset, length)

    fun input(offset: Int, length: Int): MetadataPayloadInput = MetadataPayloadInput(bytes, start + offset, length)
}

private data class MetadataPayloadInput(
    val bytes: ByteArray,
    val offset: Int,
    val length: Int,
)

private class BoundedBytes(private val maximumSize: Int) {
    private var bytes: ByteArray = ByteArray(0)
    private var size: Int = 0

    fun append(source: ByteArray, count: Int): Boolean {
        if (count > maximumSize - size) return false
        val required = size + count
        if (required > bytes.size) grow(required)
        source.copyInto(bytes, size, 0, count)
        size = required
        return true
    }

    fun toByteArray(): ByteArray = bytes.copyOf(size)

    private fun grow(required: Int) {
        val doubled = when {
            bytes.isEmpty() -> required
            bytes.size > maximumSize / 2 -> maximumSize
            else -> bytes.size * 2
        }
        bytes = bytes.copyOf(maxOf(required, doubled))
    }
}

private class MetadataBudget(maximumBytes: Int) {
    private val maximumBytes: Long = maximumBytes.toLong()
    private var usedBytes: Long = 0L

    fun reserve(bytes: Long): Boolean {
        if (bytes < 0L || bytes > maximumBytes - usedBytes) {
            usedBytes = maximumBytes
            return false
        }
        usedBytes += bytes
        return true
    }
}

private fun String.isWellFormedBcp47LanguageTag(): Boolean {
    if (isEmpty()) return true
    if (RFC5646_GRANDFATHERED_TAGS.any { equals(it, ignoreCase = true) }) return true

    val scanner = LanguageSubtagScanner(this)
    if (scanner.isPrivateUseSingleton()) return scanner.consumePrivateUse()

    val languageLength = scanner.length
    if (languageLength !in 2..8 || !scanner.isAlpha()) return false
    scanner.advance()

    if (languageLength <= 3) {
        var extlangCount = 0
        while (extlangCount < 3 && scanner.length == 3 && scanner.isAlpha()) {
            scanner.advance()
            extlangCount++
        }
    }
    if (scanner.length == 4 && scanner.isAlpha()) scanner.advance()
    if ((scanner.length == 2 && scanner.isAlpha()) || (scanner.length == 3 && scanner.isDigit())) {
        scanner.advance()
    }

    val variants = HashSet<Long>()
    while (scanner.isVariant()) {
        if (!variants.add(scanner.caseInsensitiveKey())) return false
        scanner.advance()
    }

    val extensionSingletons = BooleanArray(36)
    while (scanner.isExtensionSingleton()) {
        val singleton = scanner.singletonIndex()
        if (extensionSingletons[singleton]) return false
        extensionSingletons[singleton] = true
        scanner.advance()

        var subtagCount = 0
        while (scanner.length in 2..8 && scanner.isAlphaNumeric()) {
            scanner.advance()
            subtagCount++
        }
        if (subtagCount == 0) return false
    }

    return when {
        scanner.isPrivateUseSingleton() -> scanner.consumePrivateUse()
        else -> !scanner.hasCurrent
    }
}

private class LanguageSubtagScanner(private val tag: String) {
    private var start: Int = 0
    private var end: Int = tag.indexOf('-').let { if (it >= 0) it else tag.length }

    val hasCurrent: Boolean
        get() = start >= 0

    val length: Int
        get() = if (hasCurrent) end - start else -1

    fun advance() {
        if (!hasCurrent) return
        if (end == tag.length) {
            start = -1
            end = -1
            return
        }
        start = end + 1
        end = tag.indexOf('-', start).let { if (it >= 0) it else tag.length }
    }

    fun isAlpha(): Boolean = hasCurrent && (start until end).all { tag[it].isAsciiAlpha() }

    fun isDigit(): Boolean = hasCurrent && (start until end).all { tag[it] in '0'..'9' }

    fun isAlphaNumeric(): Boolean = hasCurrent && (start until end).all { tag[it].isAsciiAlphaNumeric() }

    fun isVariant(): Boolean = isAlphaNumeric() &&
        (length in 5..8 || (length == 4 && tag[start] in '0'..'9'))

    fun isPrivateUseSingleton(): Boolean = length == 1 && (tag[start] == 'x' || tag[start] == 'X')

    fun isExtensionSingleton(): Boolean = length == 1 && tag[start].isAsciiAlphaNumeric() &&
        !isPrivateUseSingleton()

    fun singletonIndex(): Int {
        val value = tag[start]
        return if (value in '0'..'9') value - '0' else value.asciiLowercase() - 'a' + 10
    }

    fun caseInsensitiveKey(): Long {
        var key = 0L
        for (index in start until end) {
            val value = tag[index]
            val digit = if (value in '0'..'9') value - '0' + 1 else value.asciiLowercase() - 'a' + 11
            key = key * 37L + digit
        }
        return key
    }

    fun consumePrivateUse(): Boolean {
        advance()
        if (!hasCurrent) return false
        while (hasCurrent) {
            if (length !in 1..8 || !isAlphaNumeric()) return false
            advance()
        }
        return true
    }
}

private fun Char.isAsciiAlpha(): Boolean = this in 'A'..'Z' || this in 'a'..'z'

private fun Char.isAsciiAlphaNumeric(): Boolean = isAsciiAlpha() || this in '0'..'9'

private fun Char.asciiLowercase(): Char = if (this in 'A'..'Z') this + ('a' - 'A') else this

private fun <T> immutableList(values: List<T>): List<T> = Collections.unmodifiableList(ArrayList(values))

private const val KEYWORD_MAX_BYTES: Int = 79
private const val INFLATE_BUFFER_BYTES: Int = 8192
private const val HISTOGRAM_ENTRY_BUDGET_BYTES: Long = 16L
private const val SUGGESTED_PALETTE_ENTRY_BUDGET_BYTES: Long = 48L
private const val TRANSPARENCY_ENTRY_BUDGET_BYTES: Long = 16L
private const val INDEXED_COLOR_TYPE: Int = 3
// Closed grandfathered production from RFC 5646 section 2.1, not an IANA registry snapshot.
private val RFC5646_GRANDFATHERED_TAGS: Set<String> = setOf(
    "art-lojban",
    "cel-gaulish",
    "en-gb-oed",
    "i-ami",
    "i-bnn",
    "i-default",
    "i-enochian",
    "i-hak",
    "i-klingon",
    "i-lux",
    "i-mingo",
    "i-navajo",
    "i-pwn",
    "i-tao",
    "i-tay",
    "i-tsu",
    "no-bok",
    "no-nyn",
    "sgn-be-fr",
    "sgn-be-nl",
    "sgn-ch-de",
    "zh-guoyu",
    "zh-hakka",
    "zh-min",
    "zh-min-nan",
    "zh-xiang",
)
private val STRUCTURAL_SINGLETON_TYPES: Set<String> = setOf(
    "iCCP",
    "sRGB",
    "gAMA",
    "cHRM",
    "cICP",
    "mDCV",
    "cLLI",
    "eXIf",
    "pHYs",
    "tIME",
    "sBIT",
    "bKGD",
    "hIST",
    "tRNS",
)
private val STRUCTURAL_PRE_PALETTE_AND_IDAT_TYPES: Set<String> = setOf(
    "iCCP",
    "sRGB",
    "gAMA",
    "cHRM",
    "cICP",
    "mDCV",
    "cLLI",
    "sBIT",
)
private val STRUCTURAL_PRE_IDAT_TYPES: Set<String> = setOf("bKGD", "eXIf", "hIST", "pHYs", "sPLT", "tRNS")
