package org.graphiks.kanvas.color.icc

import org.graphiks.kanvas.color.ColorModel
import org.graphiks.kanvas.color.ColorProfile
import org.graphiks.math.SkcmsMatrix3x3
import org.graphiks.math.SkcmsTransferFunction
import kotlin.math.round

/** Writes the RGB matrix/TRC subset represented by [ColorProfile]. */
public object IccProfileWriter {
    public fun writeMatrixTrc(profile: ColorProfile): ByteArray {
        require(profile.colorModel == ColorModel.RGB) { "ICC matrix/TRC output requires an RGB profile" }
        require(profile.unsupportedCode == null && profile.hasMatrixTrc && !profile.isHdr) {
            "ICC matrix/TRC output requires a supported SDR matrix/TRC profile"
        }
        val matrix = requireNotNull(profile.toXyzD50) { "ICC matrix/TRC output requires a matrix" }
        val transferFunction = requireNotNull(profile.transferFunction) {
            "ICC matrix/TRC output requires a scalar transfer function"
        }
        validateMatrix(matrix)
        val transferPayload = parametricCurveTag(transferFunction)
        val tags = listOf(
            Tag(IccSignature.DESCRIPTION, multiLocalizedUnicodeTag("Kanvas matrix/TRC RGB profile")),
            Tag(IccSignature.COPYRIGHT, multiLocalizedUnicodeTag("Copyright Kanvas contributors")),
            Tag(IccSignature.WHITE_POINT, xyzTag(D50_X, D50_Y, D50_Z)),
            Tag(IccSignature.R_XYZ, xyzTag(matrix[0, 0], matrix[1, 0], matrix[2, 0])),
            Tag(IccSignature.G_XYZ, xyzTag(matrix[0, 1], matrix[1, 1], matrix[2, 1])),
            Tag(IccSignature.B_XYZ, xyzTag(matrix[0, 2], matrix[1, 2], matrix[2, 2])),
            Tag(IccSignature.R_TRC, transferPayload.copyOf()),
            Tag(IccSignature.G_TRC, transferPayload.copyOf()),
            Tag(IccSignature.B_TRC, transferPayload.copyOf()),
        )
        return writeProfile(tags)
    }

    private fun writeProfile(tags: List<Tag>): ByteArray {
        val tagTableEnd = HEADER_AND_COUNT_SIZE + tags.size * TAG_ENTRY_SIZE
        var profileSize = tagTableEnd
        tags.forEach { tag -> profileSize = align4(profileSize + tag.payload.size) }
        val bytes = ByteArray(profileSize)

        writeU32(bytes, PROFILE_SIZE_OFFSET, profileSize)
        writeU32(bytes, VERSION_OFFSET, ICC_VERSION_4_3)
        writeSignature(bytes, PROFILE_CLASS_OFFSET, IccSignature.DISPLAY_CLASS)
        writeSignature(bytes, DATA_COLOR_SPACE_OFFSET, IccSignature.RGB)
        writeSignature(bytes, PCS_OFFSET, IccSignature.XYZ)
        writeDateTime(bytes, DATE_TIME_OFFSET)
        writeSignature(bytes, HEADER_SIGNATURE_OFFSET, IccSignature.ACSP)
        writeS15Fixed16(bytes, ILLUMINANT_OFFSET, D50_X)
        writeS15Fixed16(bytes, ILLUMINANT_OFFSET + 4, D50_Y)
        writeS15Fixed16(bytes, ILLUMINANT_OFFSET + 8, D50_Z)
        writeU32(bytes, TAG_COUNT_OFFSET, tags.size)

        var dataOffset = tagTableEnd
        tags.forEachIndexed { index, tag ->
            val entryOffset = HEADER_AND_COUNT_SIZE + index * TAG_ENTRY_SIZE
            writeSignature(bytes, entryOffset, tag.signature)
            writeU32(bytes, entryOffset + 4, dataOffset)
            writeU32(bytes, entryOffset + 8, tag.payload.size)
            tag.payload.copyInto(bytes, destinationOffset = dataOffset)
            dataOffset = align4(dataOffset + tag.payload.size)
        }
        check(dataOffset == bytes.size)
        return bytes
    }

    private fun multiLocalizedUnicodeTag(text: String): ByteArray {
        val encoded = text.toByteArray(Charsets.UTF_16BE)
        return ByteArray(MLUC_TEXT_OFFSET + encoded.size).also { bytes ->
            writeSignature(bytes, 0, IccSignature.MULTI_LOCALIZED_UNICODE_TYPE)
            writeU32(bytes, 8, 1)
            writeU32(bytes, 12, MLUC_RECORD_SIZE)
            writeU16(bytes, 16, LANGUAGE_ENGLISH)
            writeU16(bytes, 18, COUNTRY_US)
            writeU32(bytes, 20, encoded.size)
            writeU32(bytes, 24, MLUC_TEXT_OFFSET)
            encoded.copyInto(bytes, destinationOffset = MLUC_TEXT_OFFSET)
        }
    }

    private fun xyzTag(x: Float, y: Float, z: Float): ByteArray = ByteArray(XYZ_TAG_SIZE).also { bytes ->
        writeSignature(bytes, 0, IccSignature.XYZ_TYPE)
        writeS15Fixed16(bytes, 8, x)
        writeS15Fixed16(bytes, 12, y)
        writeS15Fixed16(bytes, 16, z)
    }

    private fun parametricCurveTag(transferFunction: SkcmsTransferFunction): ByteArray {
        val parameters = floatArrayOf(
            transferFunction.g,
            transferFunction.a,
            transferFunction.b,
            transferFunction.c,
            transferFunction.d,
            transferFunction.e,
            transferFunction.f,
        )
        require(parametricCurveValidationError(PARAMETRIC_TYPE_4, parameters) == null) {
            "Transfer function cannot be represented as an ICC parametric type 4 curve"
        }
        val fixedParameters = IntArray(parameters.size) { quantizeS15Fixed16(parameters[it]) }
        makeQuantizedCurveMonotonic(fixedParameters)
        val quantized = FloatArray(fixedParameters.size) { fixedParameters[it] / FIXED_SCALE.toFloat() }
        require(parametricCurveValidationError(PARAMETRIC_TYPE_4, quantized) == null) {
            "Transfer function is invalid after ICC signed 15.16 quantization"
        }

        return ByteArray(PARAMETRIC_TYPE_4_SIZE).also { bytes ->
            writeSignature(bytes, 0, IccSignature.PARAMETRIC_CURVE_TYPE)
            writeU16(bytes, 8, PARAMETRIC_TYPE_4)
            fixedParameters.forEachIndexed { index, fixed -> writeU32(bytes, 12 + index * 4, fixed) }
        }
    }

    private fun makeQuantizedCurveMonotonic(parameters: IntArray) {
        var quantized = FloatArray(parameters.size) { parameters[it] / FIXED_SCALE.toFloat() }
        var validationError = parametricCurveValidationError(PARAMETRIC_TYPE_4, quantized)
        var adjustments = 0
        while (validationError == "downward jump" && adjustments < MAX_LOWER_SLOPE_ADJUSTMENTS) {
            parameters[LOWER_SLOPE_INDEX]--
            quantized = FloatArray(parameters.size) { parameters[it] / FIXED_SCALE.toFloat() }
            validationError = parametricCurveValidationError(PARAMETRIC_TYPE_4, quantized)
            adjustments++
        }
    }

    private fun validateMatrix(matrix: SkcmsMatrix3x3) {
        val values = DoubleArray(9)
        for (row in 0 until 3) for (column in 0 until 3) {
            values[row * 3 + column] = quantizeS15Fixed16(matrix[row, column]) / FIXED_SCALE
        }
        val determinant =
            values[0] * (values[4] * values[8] - values[5] * values[7]) -
                values[1] * (values[3] * values[8] - values[5] * values[6]) +
                values[2] * (values[3] * values[7] - values[4] * values[6])
        require(determinant.isFinite() && determinant != 0.0) {
            "ICC matrix must remain finite and invertible after signed 15.16 quantization"
        }
    }

    private fun writeDateTime(bytes: ByteArray, offset: Int) {
        intArrayOf(2026, 7, 10, 0, 0, 0).forEachIndexed { index, value ->
            writeU16(bytes, offset + index * 2, value)
        }
    }

    private fun writeSignature(bytes: ByteArray, offset: Int, signature: IccSignature) {
        writeU32(bytes, offset, signature.value)
    }

    private fun writeS15Fixed16(bytes: ByteArray, offset: Int, value: Float) {
        writeU32(bytes, offset, quantizeS15Fixed16(value))
    }

    private fun quantizeS15Fixed16(value: Float): Int {
        require(value.isFinite()) { "ICC signed 15.16 values must be finite" }
        val scaled = value.toDouble() * FIXED_SCALE
        require(scaled >= Int.MIN_VALUE.toDouble() && scaled <= Int.MAX_VALUE.toDouble()) {
            "ICC signed 15.16 value is out of range: $value"
        }
        return round(scaled).toInt()
    }

    private fun writeU16(bytes: ByteArray, offset: Int, value: Int) {
        require(value in 0..0xffff)
        bytes[offset] = (value ushr 8).toByte()
        bytes[offset + 1] = value.toByte()
    }

    private fun writeU32(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value ushr 24).toByte()
        bytes[offset + 1] = (value ushr 16).toByte()
        bytes[offset + 2] = (value ushr 8).toByte()
        bytes[offset + 3] = value.toByte()
    }

    private fun align4(value: Int): Int = (value + 3) and -4

    private data class Tag(val signature: IccSignature, val payload: ByteArray)

    private const val PROFILE_SIZE_OFFSET: Int = 0
    private const val VERSION_OFFSET: Int = 8
    private const val PROFILE_CLASS_OFFSET: Int = 12
    private const val DATA_COLOR_SPACE_OFFSET: Int = 16
    private const val PCS_OFFSET: Int = 20
    private const val DATE_TIME_OFFSET: Int = 24
    private const val HEADER_SIGNATURE_OFFSET: Int = 36
    private const val ILLUMINANT_OFFSET: Int = 68
    private const val TAG_COUNT_OFFSET: Int = 128
    private const val HEADER_AND_COUNT_SIZE: Int = 132
    private const val TAG_ENTRY_SIZE: Int = 12
    private const val XYZ_TAG_SIZE: Int = 20
    private const val PARAMETRIC_TYPE_4_SIZE: Int = 40
    private const val MLUC_RECORD_SIZE: Int = 12
    private const val MLUC_TEXT_OFFSET: Int = 28
    private const val ICC_VERSION_4_3: Int = 0x04300000
    private const val LANGUAGE_ENGLISH: Int = 0x656e
    private const val COUNTRY_US: Int = 0x5553
    private const val PARAMETRIC_TYPE_4: Int = 4
    private const val LOWER_SLOPE_INDEX: Int = 3
    private const val MAX_LOWER_SLOPE_ADJUSTMENTS: Int = 8
    private const val FIXED_SCALE: Double = 65_536.0
    private const val D50_X: Float = 0.9642f
    private const val D50_Y: Float = 1f
    private const val D50_Z: Float = 0.8249f
}
