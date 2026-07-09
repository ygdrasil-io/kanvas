package org.graphiks.kanvas.color.icc

import org.graphiks.kanvas.color.ColorModel
import org.graphiks.kanvas.color.ColorProfile
import org.graphiks.kanvas.color.ColorProfileParseResult
import org.graphiks.math.SkcmsMatrix3x3
import org.graphiks.math.SkcmsTransferFunction
import kotlin.math.abs

public data class IccParseLimits(
    public val maxBytes: Int = 16 * 1024 * 1024,
    public val maxTags: Int = 4096,
)

public object IccProfileParser {
    public fun parse(bytes: ByteArray, limits: IccParseLimits): ColorProfileParseResult {
        if (limits.maxBytes < 0 || limits.maxTags < 0) {
            return failure("icc.limit.invalid", "ICC parse limits must not be negative")
        }
        if (bytes.size > limits.maxBytes) {
            return failure("icc.limit.bytes", "ICC input has ${bytes.size} bytes; limit is ${limits.maxBytes}")
        }
        if (bytes.size < HEADER_AND_COUNT_SIZE) {
            return failure("icc.header.size", "ICC input is shorter than the 132-byte header and tag count")
        }
        val declaredTagCount = readU32WithoutAllocation(bytes, TAG_COUNT_OFFSET)
        if (declaredTagCount > limits.maxTags.toLong()) {
            return failure("icc.limit.tags", "ICC tag count $declaredTagCount exceeds ${limits.maxTags}")
        }

        return try {
            Parser(bytes, limits).parse()
        } catch (abort: IccParseAbort) {
            abort.failure
        }
    }

    private fun failure(code: String, message: String): ColorProfileParseResult.Failure =
        ColorProfileParseResult.Failure(code, message)

    private fun readU32WithoutAllocation(bytes: ByteArray, offset: Int): Long =
        ((bytes[offset].toLong() and 0xffL) shl 24) or
            ((bytes[offset + 1].toLong() and 0xffL) shl 16) or
            ((bytes[offset + 2].toLong() and 0xffL) shl 8) or
            (bytes[offset + 3].toLong() and 0xffL)
}

private class Parser(
    private val bytes: ByteArray,
    private val limits: IccParseLimits,
) {
    private val inputReader = IccBigEndianReader(bytes, bytes.size)
    private lateinit var reader: IccBigEndianReader
    private var profileSize: Int = 0

    fun parse(): ColorProfileParseResult {
        parseHeader()
        val tags = parseTagTable()
        val profile = when (reader.signature(DATA_COLOR_SPACE_OFFSET)) {
            IccSignature.RGB -> parseRgb(tags)
            IccSignature.GRAY -> parseGray(tags)
            else -> abort("icc.header.color-space", "Only RGB and GRAY ICC profiles are supported")
        }
        return ColorProfileParseResult.Success(profile)
    }

    private fun parseHeader() {
        val declaredSize = inputReader.u32(PROFILE_SIZE_OFFSET)
        if (declaredSize < HEADER_AND_COUNT_SIZE || declaredSize > bytes.size.toLong()) {
            abort("icc.header.size", "Declared ICC profile size is outside the input")
        }
        if (declaredSize > limits.maxBytes.toLong()) {
            abort("icc.limit.bytes", "Declared ICC profile size exceeds maxBytes")
        }
        profileSize = declaredSize.toInt()
        reader = IccBigEndianReader(bytes, profileSize)

        if (reader.signature(HEADER_SIGNATURE_OFFSET) != IccSignature.ACSP) {
            abort("icc.header.signature", "ICC header signature is not acsp")
        }
        val majorVersion = bytes[VERSION_OFFSET].toInt() and 0xff
        if (majorVersion != 2 && majorVersion != 4) {
            abort("icc.header.version", "Only ICC major versions 2 and 4 are supported")
        }
        when (reader.signature(PROFILE_CLASS_OFFSET)) {
            IccSignature.INPUT_CLASS, IccSignature.DISPLAY_CLASS, IccSignature.OUTPUT_CLASS -> Unit
            else -> abort("icc.header.class", "ICC profile class is not a matrix/TRC device class")
        }
        if (reader.signature(PCS_OFFSET) != IccSignature.XYZ) {
            abort("icc.header.pcs", "Only XYZ profile connection space is supported")
        }
        val illuminantX = reader.s15Fixed16(ILLUMINANT_OFFSET)
        val illuminantY = reader.s15Fixed16(ILLUMINANT_OFFSET + 4)
        val illuminantZ = reader.s15Fixed16(ILLUMINANT_OFFSET + 8)
        if (abs(illuminantX - D50_X) > D50_TOLERANCE ||
            abs(illuminantY - D50_Y) > D50_TOLERANCE ||
            abs(illuminantZ - D50_Z) > D50_TOLERANCE
        ) {
            abort("icc.header.illuminant", "ICC illuminant is not D50")
        }
    }

    private fun parseTagTable(): Map<IccSignature, TagRecord> {
        val tagCountLong = reader.u32(TAG_COUNT_OFFSET)
        if (tagCountLong > limits.maxTags.toLong()) {
            abort("icc.limit.tags", "ICC tag count $tagCountLong exceeds ${limits.maxTags}")
        }
        val tableEnd = HEADER_AND_COUNT_SIZE.toLong() + tagCountLong * TAG_ENTRY_SIZE.toLong()
        if (tableEnd > profileSize.toLong()) {
            abort("icc.tag.table", "ICC tag table is outside the declared profile")
        }

        val tagCount = tagCountLong.toInt()
        val tags = LinkedHashMap<IccSignature, TagRecord>()
        repeat(tagCount) { index ->
            val entryOffset = HEADER_AND_COUNT_SIZE + index * TAG_ENTRY_SIZE
            val signature = reader.signature(entryOffset)
            if (tags.containsKey(signature)) {
                abort("icc.tag.duplicate", "Duplicate ICC tag $signature")
            }
            val offset = reader.u32(entryOffset + 4)
            val size = reader.u32(entryOffset + 8)
            if (size < MIN_TAG_SIZE || offset < tableEnd || offset > profileSize.toLong() - size) {
                abort("icc.tag.range", "ICC tag $signature has an invalid range")
            }
            if ((offset and 3L) != 0L) {
                abort("icc.tag.range", "ICC tag $signature is not four-byte aligned")
            }
            tags[signature] = TagRecord(offset.toInt(), size.toInt())
        }
        return tags
    }

    private fun parseRgb(tags: Map<IccSignature, TagRecord>): ColorProfile {
        val rXyz = requiredTag(tags, IccSignature.R_XYZ)
        val gXyz = requiredTag(tags, IccSignature.G_XYZ)
        val bXyz = requiredTag(tags, IccSignature.B_XYZ)
        val rTrc = parseCurve(requiredTag(tags, IccSignature.R_TRC))
        val gTrc = parseCurve(requiredTag(tags, IccSignature.G_TRC))
        val bTrc = parseCurve(requiredTag(tags, IccSignature.B_TRC))

        val transferFunction = commonTransferFunction(rTrc, gTrc, bTrc)
        val r = parseXyzTag(rXyz)
        val g = parseXyzTag(gXyz)
        val b = parseXyzTag(bXyz)
        return ColorProfile(
            colorModel = ColorModel.RGB,
            toXyzD50 = SkcmsMatrix3x3.of(
                r[0], g[0], b[0],
                r[1], g[1], b[1],
                r[2], g[2], b[2],
            ),
            transferFunction = transferFunction,
        )
    }

    private fun parseGray(tags: Map<IccSignature, TagRecord>): ColorProfile {
        val curve = parseCurve(requiredTag(tags, IccSignature.K_TRC))
        val transferFunction = (curve as? ParametricIccCurve)?.toTransferFunction()
            ?: abort("icc.curve.sampled", "Sampled TRCs cannot be represented by the current ColorProfile contract")
        val illuminant = readXyz(ILLUMINANT_OFFSET)
        return ColorProfile(
            colorModel = ColorModel.GRAY,
            toXyzD50 = SkcmsMatrix3x3.of(
                illuminant[0], 0f, 0f,
                0f, illuminant[1], 0f,
                0f, 0f, illuminant[2],
            ),
            transferFunction = transferFunction,
        )
    }

    private fun requiredTag(tags: Map<IccSignature, TagRecord>, signature: IccSignature): TagRecord =
        tags[signature] ?: abort("icc.profile.tags", "Required ICC tag $signature is missing")

    private fun parseXyzTag(tag: TagRecord): FloatArray {
        if (tag.size < XYZ_TAG_SIZE || reader.signature(tag.offset) != IccSignature.XYZ_TYPE) {
            abort("icc.tag.type", "ICC XYZ tag has the wrong type or size")
        }
        return readXyz(tag.offset + 8)
    }

    private fun parseCurve(tag: TagRecord): IccCurve = when (reader.signature(tag.offset)) {
        IccSignature.PARAMETRIC_CURVE_TYPE -> parseParametricCurve(tag)
        IccSignature.CURVE_TYPE -> parseSampledCurve(tag)
        else -> abort("icc.curve.type", "Unsupported ICC curve tag type")
    }

    private fun parseParametricCurve(tag: TagRecord): IccCurve {
        if (tag.size < PARAMETRIC_HEADER_SIZE) {
            abort("icc.curve.range", "Parametric ICC curve header is truncated")
        }
        val functionType = reader.u16(tag.offset + 8)
        if (functionType !in PARAMETRIC_PARAMETER_COUNTS.indices) {
            abort("icc.curve.type", "Unsupported ICC parametric curve selector $functionType")
        }
        val parameterCount = PARAMETRIC_PARAMETER_COUNTS[functionType]
        val requiredSize = PARAMETRIC_HEADER_SIZE + parameterCount * 4L
        if (tag.size.toLong() < requiredSize) {
            abort("icc.curve.range", "Parametric ICC curve parameters are truncated")
        }
        val parameters = FloatArray(parameterCount) { index ->
            reader.s15Fixed16(tag.offset + PARAMETRIC_HEADER_SIZE + index * 4)
        }
        if (parameters[0] <= 0f || (functionType >= 1 && parameters[1] <= 0f)) {
            abort("icc.curve.values", "Parametric ICC curve is not monotonic")
        }
        return ParametricIccCurve(functionType, parameters)
    }

    private fun parseSampledCurve(tag: TagRecord): IccCurve {
        if (tag.size < CURVE_HEADER_SIZE) {
            abort("icc.curve.range", "Sampled ICC curve header is truncated")
        }
        val countLong = reader.u32(tag.offset + 8)
        val payloadSize = countLong * 2L
        if (countLong > Int.MAX_VALUE || payloadSize > tag.size.toLong() - CURVE_HEADER_SIZE) {
            abort("icc.curve.range", "Sampled ICC curve payload is outside its tag")
        }
        val count = countLong.toInt()
        if (count == 0) return ParametricIccCurve(0, floatArrayOf(1f))
        if (count == 1) {
            val gamma = reader.u16(tag.offset + CURVE_HEADER_SIZE) / 256f
            if (gamma <= 0f) abort("icc.curve.values", "Sampled ICC gamma must be positive")
            return ParametricIccCurve(0, floatArrayOf(gamma))
        }

        val samples = FloatArray(count) { index ->
            reader.u16(tag.offset + CURVE_HEADER_SIZE + index * 2) / 65535f
        }
        if ((1..samples.lastIndex).any { index -> samples[index - 1] > samples[index] }) {
            abort("icc.curve.values", "Sampled ICC curve must be monotonic")
        }
        return SampledIccCurve(samples)
    }

    private fun commonTransferFunction(vararg curves: IccCurve): SkcmsTransferFunction {
        val transferFunctions = curves.map {
            (it as? ParametricIccCurve)?.toTransferFunction()
                ?: abort("icc.curve.sampled", "Sampled TRCs cannot be represented by the current ColorProfile contract")
        }
        if (transferFunctions.any { it != transferFunctions[0] }) {
            abort("icc.profile.trc", "RGB ICC profile uses different channel transfer functions")
        }
        return transferFunctions[0]
    }

    private fun readXyz(offset: Int): FloatArray = floatArrayOf(
        reader.s15Fixed16(offset),
        reader.s15Fixed16(offset + 4),
        reader.s15Fixed16(offset + 8),
    )
}

private data class TagRecord(val offset: Int, val size: Int)

private class IccParseAbort(
    val failure: ColorProfileParseResult.Failure,
) : RuntimeException(null, null, false, false)

private fun abort(code: String, message: String): Nothing =
    throw IccParseAbort(ColorProfileParseResult.Failure(code, message))

private const val PROFILE_SIZE_OFFSET: Int = 0
private const val VERSION_OFFSET: Int = 8
private const val PROFILE_CLASS_OFFSET: Int = 12
private const val DATA_COLOR_SPACE_OFFSET: Int = 16
private const val PCS_OFFSET: Int = 20
private const val HEADER_SIGNATURE_OFFSET: Int = 36
private const val ILLUMINANT_OFFSET: Int = 68
private const val TAG_COUNT_OFFSET: Int = 128
private const val HEADER_AND_COUNT_SIZE: Int = 132
private const val TAG_ENTRY_SIZE: Int = 12
private const val MIN_TAG_SIZE: Long = 4L
private const val XYZ_TAG_SIZE: Int = 20
private const val CURVE_HEADER_SIZE: Int = 12
private const val PARAMETRIC_HEADER_SIZE: Int = 12
private const val D50_X: Float = 0.9642f
private const val D50_Y: Float = 1f
private const val D50_Z: Float = 0.8249f
private const val D50_TOLERANCE: Float = 0.01f
private val PARAMETRIC_PARAMETER_COUNTS: IntArray = intArrayOf(1, 3, 4, 5, 7)
