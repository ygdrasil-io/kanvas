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
    private var majorVersion: Int = 0
    private var profileClass: IccSignature = IccSignature(0)
    private var dataColorSpace: IccSignature = IccSignature(0)

    fun parse(): ColorProfileParseResult {
        parseHeader()
        val tags = parseTagTable()
        validateCommonRequiredTags(tags)
        val profile = when (dataColorSpace) {
            IccSignature.RGB -> parseRgb(tags)
            IccSignature.GRAY -> parseGray(tags)
            else -> abort("icc.header.color-space", "Only RGB and GRAY ICC profiles are supported")
        }
        return ColorProfileParseResult.Success(profile)
    }

    private fun parseHeader() {
        val declaredSize = inputReader.u32(PROFILE_SIZE_OFFSET)
        if (declaredSize != bytes.size.toLong() || declaredSize < HEADER_AND_COUNT_SIZE) {
            abort("icc.header.size", "Declared ICC profile size must exactly match the input")
        }
        if (declaredSize > limits.maxBytes.toLong()) {
            abort("icc.limit.bytes", "Declared ICC profile size exceeds maxBytes")
        }
        profileSize = declaredSize.toInt()
        reader = IccBigEndianReader(bytes, profileSize)

        if (reader.signature(HEADER_SIGNATURE_OFFSET) != IccSignature.ACSP) {
            abort("icc.header.signature", "ICC header signature is not acsp")
        }
        majorVersion = bytes[VERSION_OFFSET].toInt() and 0xff
        val minorVersion = bytes[VERSION_OFFSET + 1].toInt() ushr 4 and 0x0f
        val bugfixVersion = bytes[VERSION_OFFSET + 1].toInt() and 0x0f
        if ((majorVersion != 2 && majorVersion != 4) ||
            minorVersion > 4 || bugfixVersion > 9 ||
            !reader.isZero(VERSION_OFFSET + 2, 2)
        ) {
            abort("icc.header.version", "Only ICC major versions 2 and 4 are supported")
        }
        if (!reader.isZero(HEADER_RESERVED_OFFSET, HEADER_RESERVED_SIZE)) {
            abort("icc.header.reserved", "ICC header reserved bytes must be zero")
        }
        if (reader.u32(DEVICE_ATTRIBUTES_LOW_OFFSET) and DEVICE_ATTRIBUTES_RESERVED_MASK != 0L) {
            abort("icc.header.attributes", "ICC device attribute reserved bits must be zero")
        }
        if (reader.u32(RENDERING_INTENT_OFFSET) !in 0L..3L) {
            abort("icc.header.intent", "ICC rendering intent must be one of 0 through 3 with reserved bits zero")
        }

        profileClass = reader.signature(PROFILE_CLASS_OFFSET)
        dataColorSpace = reader.signature(DATA_COLOR_SPACE_OFFSET)
        when (dataColorSpace) {
            IccSignature.RGB -> if (profileClass != IccSignature.INPUT_CLASS && profileClass != IccSignature.DISPLAY_CLASS) {
                abort("icc.header.class", "RGB matrix/TRC profiles must be input or display class")
            }
            IccSignature.GRAY -> if (profileClass != IccSignature.INPUT_CLASS &&
                profileClass != IccSignature.DISPLAY_CLASS && profileClass != IccSignature.OUTPUT_CLASS
            ) {
                abort("icc.header.class", "GRAY matrix/TRC profile class is unsupported")
            }
            else -> abort("icc.header.color-space", "Only RGB and GRAY ICC profiles are supported")
        }
        if (reader.signature(PCS_OFFSET) != IccSignature.XYZ) {
            abort("icc.header.pcs", "Only XYZ profile connection space is supported")
        }
        val illuminantX = reader.s15Fixed16(ILLUMINANT_OFFSET)
        val illuminantY = reader.s15Fixed16(ILLUMINANT_OFFSET + 4)
        val illuminantZ = reader.s15Fixed16(ILLUMINANT_OFFSET + 8)
        if (!roundsToD50(illuminantX, D50_X) ||
            !roundsToD50(illuminantY, D50_Y) ||
            !roundsToD50(illuminantZ, D50_Z)
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
        validateTagLayout(tags.values, tableEnd.toInt())
        tags.values.distinct().forEach { tag ->
            if (!reader.isZero(tag.offset + 4, 4)) {
                abort("icc.tag.reserved", "ICC tag type reserved bytes must be zero")
            }
        }
        return tags
    }

    private fun validateTagLayout(tags: Collection<TagRecord>, tableEnd: Int) {
        val ranges = tags.distinct().sortedBy(TagRecord::offset)
        if (ranges.isEmpty() || ranges.first().offset != tableEnd) {
            abort("icc.profile.envelope", "First ICC tag data must immediately follow the tag table")
        }
        var expectedOffset = tableEnd.toLong()
        ranges.forEach { tag ->
            if (tag.offset.toLong() < expectedOffset) {
                abort("icc.tag.overlap", "ICC tag data ranges partially overlap")
            }
            if (tag.offset.toLong() > expectedOffset) {
                abort("icc.profile.envelope", "ICC profile contains unexplained bytes between tag elements")
            }
            val tagEnd = tag.offset.toLong() + tag.size.toLong()
            val paddedEnd = align4(tagEnd)
            if (paddedEnd > profileSize.toLong()) {
                abort("icc.profile.envelope", "ICC profile is missing required tag alignment padding")
            }
            if (!reader.isZero(tagEnd.toInt(), (paddedEnd - tagEnd).toInt())) {
                abort("icc.profile.padding", "ICC tag alignment padding must be zero")
            }
            expectedOffset = paddedEnd
        }
        if (expectedOffset != profileSize.toLong()) {
            abort("icc.profile.envelope", "ICC profile has bytes outside its tag data envelope")
        }
    }

    private fun validateCommonRequiredTags(tags: Map<IccSignature, TagRecord>) {
        val description = requiredTag(tags, IccSignature.DESCRIPTION)
        val copyright = requiredTag(tags, IccSignature.COPYRIGHT)
        val mediaWhitePoint = parseXyzTag(requiredTag(tags, IccSignature.WHITE_POINT))
        if (profileClass == IccSignature.DISPLAY_CLASS &&
            (!roundsToD50(mediaWhitePoint[0], D50_X) ||
                !roundsToD50(mediaWhitePoint[1], D50_Y) ||
                !roundsToD50(mediaWhitePoint[2], D50_Z))
        ) {
            abort("icc.tag.white-point", "Display ICC media white point must be D50")
        }
        val expectedDescriptionType = if (majorVersion == 2) {
            IccSignature.DESCRIPTION_TYPE
        } else {
            IccSignature.MULTI_LOCALIZED_UNICODE_TYPE
        }
        val expectedCopyrightType = if (majorVersion == 2) {
            IccSignature.TEXT_TYPE
        } else {
            IccSignature.MULTI_LOCALIZED_UNICODE_TYPE
        }
        requireTagType(description, expectedDescriptionType, "profile description")
        requireTagType(copyright, expectedCopyrightType, "copyright")
    }

    private fun requireTagType(tag: TagRecord, expected: IccSignature, name: String) {
        if (reader.signature(tag.offset) != expected) {
            abort("icc.tag.type", "ICC $name tag has an incompatible type")
        }
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
        val matrix = SkcmsMatrix3x3.of(
            r[0], g[0], b[0],
            r[1], g[1], b[1],
            r[2], g[2], b[2],
        )
        if (!isRepresentableMatrix(matrix)) {
            abort("icc.profile.matrix", "RGB ICC matrix is non-finite, singular, or not invertible as Float")
        }
        return ColorProfile(
            colorModel = ColorModel.RGB,
            toXyzD50 = matrix,
            transferFunction = transferFunction,
        )
    }

    private fun parseGray(tags: Map<IccSignature, TagRecord>): ColorProfile {
        val curve = parseCurve(requiredTag(tags, IccSignature.K_TRC))
        val transferFunction = (curve as? ParametricIccCurve)?.toTransferFunction()
            ?: abort("icc.curve.sampled", "Sampled TRCs cannot be represented by the current ColorProfile contract")
        return ColorProfile(
            colorModel = ColorModel.GRAY,
            toXyzD50 = SkcmsMatrix3x3.of(
                D50_X, 0f, 0f,
                0f, D50_Y, 0f,
                0f, 0f, D50_Z,
            ),
            transferFunction = transferFunction,
        )
    }

    private fun requiredTag(tags: Map<IccSignature, TagRecord>, signature: IccSignature): TagRecord =
        tags[signature] ?: abort("icc.profile.tags", "Required ICC tag $signature is missing")

    private fun parseXyzTag(tag: TagRecord): FloatArray {
        if (tag.size != XYZ_TAG_SIZE || reader.signature(tag.offset) != IccSignature.XYZ_TYPE) {
            abort("icc.tag.type", "ICC XYZ tag has the wrong type or size")
        }
        return readXyz(tag.offset + 8)
    }

    private fun parseCurve(tag: TagRecord): IccCurve = when (reader.signature(tag.offset)) {
        IccSignature.PARAMETRIC_CURVE_TYPE -> {
            if (majorVersion != 4) abort("icc.curve.version", "Parametric ICC curves require a v4 profile")
            parseParametricCurve(tag)
        }
        IccSignature.CURVE_TYPE -> parseSampledCurve(tag)
        else -> abort("icc.curve.type", "Unsupported ICC curve tag type")
    }

    private fun parseParametricCurve(tag: TagRecord): IccCurve {
        if (tag.size < PARAMETRIC_HEADER_SIZE) {
            abort("icc.curve.range", "Parametric ICC curve header is truncated")
        }
        val functionType = reader.u16(tag.offset + 8)
        if (!reader.isZero(tag.offset + 10, 2)) {
            abort("icc.tag.reserved", "ICC parametric curve selector reserved bytes must be zero")
        }
        if (functionType !in PARAMETRIC_PARAMETER_COUNTS.indices) {
            abort("icc.curve.type", "Unsupported ICC parametric curve selector $functionType")
        }
        val parameterCount = PARAMETRIC_PARAMETER_COUNTS[functionType]
        val requiredSize = PARAMETRIC_HEADER_SIZE + parameterCount * 4L
        if (tag.size.toLong() != requiredSize) {
            abort("icc.curve.range", "Parametric ICC curve size does not match its selector")
        }
        val parameters = FloatArray(parameterCount) { index ->
            reader.s15Fixed16(tag.offset + PARAMETRIC_HEADER_SIZE + index * 4)
        }
        val validationError = parametricCurveValidationError(functionType, parameters)
        if (validationError != null) {
            abort("icc.curve.values", "Invalid parametric ICC curve: $validationError")
        }
        return ParametricIccCurve(functionType, parameters)
    }

    private fun parseSampledCurve(tag: TagRecord): IccCurve {
        if (tag.size < CURVE_HEADER_SIZE) {
            abort("icc.curve.range", "Sampled ICC curve header is truncated")
        }
        val countLong = reader.u32(tag.offset + 8)
        val requiredSize = CURVE_HEADER_SIZE.toLong() + countLong * 2L
        if (requiredSize != tag.size.toLong() && !isZeroPaddedV2SingleGamma(tag, countLong, requiredSize)) {
            abort("icc.curve.range", "Sampled ICC curve size does not match its count")
        }
        val count = countLong.toInt()
        if (count == 0) return ParametricIccCurve(0, floatArrayOf(1f))
        if (count == 1) {
            val gamma = reader.u16(tag.offset + CURVE_HEADER_SIZE) / 256f
            if (gamma <= 0f) abort("icc.curve.values", "Sampled ICC gamma must be positive")
            return ParametricIccCurve(0, floatArrayOf(gamma))
        }
        abort("icc.curve.sampled", "Sampled TRCs cannot be represented by the current ColorProfile contract")
    }

    private fun isZeroPaddedV2SingleGamma(tag: TagRecord, count: Long, semanticSize: Long): Boolean {
        if (majorVersion != 2 || count != 1L) return false
        val paddedSize = align4(semanticSize)
        if (tag.size.toLong() != paddedSize || paddedSize <= semanticSize) return false
        return reader.isZero(tag.offset + semanticSize.toInt(), (paddedSize - semanticSize).toInt())
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

    private fun roundsToD50(value: Float, expected: Float): Boolean =
        abs(value - expected) < D50_ROUNDING_HALF_UNIT

    private fun isRepresentableMatrix(matrix: SkcmsMatrix3x3): Boolean {
        val a = matrix[0, 0].toDouble()
        val b = matrix[0, 1].toDouble()
        val c = matrix[0, 2].toDouble()
        val d = matrix[1, 0].toDouble()
        val e = matrix[1, 1].toDouble()
        val f = matrix[1, 2].toDouble()
        val g = matrix[2, 0].toDouble()
        val h = matrix[2, 1].toDouble()
        val i = matrix[2, 2].toDouble()
        val determinant = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g)
        if (!determinant.isFinite() || determinant == 0.0) return false
        val inverseDeterminant = 1.0 / determinant
        val inverseValues = doubleArrayOf(
            (e * i - f * h) * inverseDeterminant,
            (c * h - b * i) * inverseDeterminant,
            (b * f - c * e) * inverseDeterminant,
            (f * g - d * i) * inverseDeterminant,
            (a * i - c * g) * inverseDeterminant,
            (c * d - a * f) * inverseDeterminant,
            (d * h - e * g) * inverseDeterminant,
            (b * g - a * h) * inverseDeterminant,
            (a * e - b * d) * inverseDeterminant,
        )
        return inverseValues.all { it.isFinite() && abs(it) <= Float.MAX_VALUE.toDouble() }
    }
}

private data class TagRecord(val offset: Int, val size: Int)

private class IccParseAbort(
    val failure: ColorProfileParseResult.Failure,
) : RuntimeException(null, null, false, false)

private fun abort(code: String, message: String): Nothing =
    throw IccParseAbort(ColorProfileParseResult.Failure(code, message))

private fun align4(value: Long): Long = (value + 3L) and -4L

private const val PROFILE_SIZE_OFFSET: Int = 0
private const val VERSION_OFFSET: Int = 8
private const val PROFILE_CLASS_OFFSET: Int = 12
private const val DATA_COLOR_SPACE_OFFSET: Int = 16
private const val PCS_OFFSET: Int = 20
private const val HEADER_SIGNATURE_OFFSET: Int = 36
private const val DEVICE_ATTRIBUTES_LOW_OFFSET: Int = 60
private const val RENDERING_INTENT_OFFSET: Int = 64
private const val ILLUMINANT_OFFSET: Int = 68
private const val HEADER_RESERVED_OFFSET: Int = 100
private const val HEADER_RESERVED_SIZE: Int = 28
private const val TAG_COUNT_OFFSET: Int = 128
private const val HEADER_AND_COUNT_SIZE: Int = 132
private const val TAG_ENTRY_SIZE: Int = 12
private const val MIN_TAG_SIZE: Long = 8L
private const val XYZ_TAG_SIZE: Int = 20
private const val CURVE_HEADER_SIZE: Int = 12
private const val PARAMETRIC_HEADER_SIZE: Int = 12
private const val D50_X: Float = 0.9642f
private const val D50_Y: Float = 1f
private const val D50_Z: Float = 0.8249f
private const val D50_ROUNDING_HALF_UNIT: Float = 0.00005f
private const val DEVICE_ATTRIBUTES_RESERVED_MASK: Long = 0xfffffff0L
private val PARAMETRIC_PARAMETER_COUNTS: IntArray = intArrayOf(1, 3, 4, 5, 7)
