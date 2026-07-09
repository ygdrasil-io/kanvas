package org.graphiks.kanvas.color.icc

import org.graphiks.kanvas.color.ColorModel
import org.graphiks.kanvas.color.ColorProfiles
import org.graphiks.math.SkcmsMatrix3x3
import org.graphiks.math.SkcmsTransferFunction
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class IccProfileParserTest {

    @Test
    fun `parses srgb matrix trc profile`() {
        val profile = parseResource("srgb-matrix-trc.icc")

        assertEquals(ColorModel.RGB, profile.colorModel)
        assertTrue(profile.hasMatrixTrc)
        assertMatrixNear(ColorProfiles.sRGB().toXyzD50!!, profile.toXyzD50!!)
        assertTransferFunctionNear(ColorProfiles.sRGB().transferFunction!!, profile.transferFunction!!)
    }

    @Test
    fun `parses display p3 matrix trc profile`() {
        val profile = parseResource("display-p3-matrix-trc.icc")

        assertEquals(ColorModel.RGB, profile.colorModel)
        assertTrue(profile.hasMatrixTrc)
        assertEquals(ColorProfiles.displayP3().toXyzD50, profile.toXyzD50)
        assertTransferFunctionNear(ColorProfiles.displayP3().transferFunction!!, profile.transferFunction!!)
    }

    @Test
    fun `rejects tag range outside profile bytes`() {
        val failure = IccProfileParser.parse(resource("invalid-tag-offset.icc"), IccParseLimits())
            .failureOrNull()!!

        assertEquals("icc.tag.range", failure.code)
    }

    @Test
    fun `rejects duplicate tag signatures`() {
        val bytes = resource("srgb-matrix-trc.icc")
        copyFourBytes(bytes, sourceOffset = 132, destinationOffset = 144)

        val failure = IccProfileParser.parse(bytes, IccParseLimits()).failureOrNull()!!

        assertEquals("icc.tag.duplicate", failure.code)
    }

    @Test
    fun `enforces max bytes before parsing the header`() {
        val failure = IccProfileParser.parse(
            bytes = ByteArray(129),
            limits = IccParseLimits(maxBytes = 128),
        ).failureOrNull()!!

        assertEquals("icc.limit.bytes", failure.code)
    }

    @Test
    fun `enforces max tags before allocating tag records`() {
        val failure = IccProfileParser.parse(
            bytes = resource("srgb-matrix-trc.icc"),
            limits = IccParseLimits(maxTags = 1),
        ).failureOrNull()!!

        assertEquals("icc.limit.tags", failure.code)
    }

    @Test
    fun `rejects malformed ICC signature`() {
        val bytes = resource("srgb-matrix-trc.icc")
        bytes[36] = 'X'.code.toByte()

        val failure = IccProfileParser.parse(bytes, IccParseLimits()).failureOrNull()!!

        assertEquals("icc.header.signature", failure.code)
    }

    @Test
    fun `rejects unsupported parametric curve selector`() {
        val bytes = resource("srgb-matrix-trc.icc")
        val curveOffset = tagOffset(bytes, IccSignature.R_TRC.value)
        bytes[curveOffset + 8] = 0
        bytes[curveOffset + 9] = 5

        val failure = IccProfileParser.parse(bytes, IccParseLimits()).failureOrNull()!!

        assertEquals("icc.curve.type", failure.code)
    }

    @Test
    fun `rejects incomplete RGB matrix trc tags`() {
        val bytes = resource("srgb-matrix-trc.icc")
        writeU32(bytes, 132, IccSignature.DESCRIPTION.value)

        val failure = IccProfileParser.parse(bytes, IccParseLimits()).failureOrNull()!!

        assertEquals("icc.profile.tags", failure.code)
    }

    @Test
    fun `parses grayscale matrix trc profile`() {
        val bytes = resource("srgb-matrix-trc.icc")
        writeU32(bytes, 16, IccSignature.GRAY.value)
        writeU32(bytes, 180, IccSignature.K_TRC.value)

        val profile = IccProfileParser.parse(bytes, IccParseLimits()).getOrThrow()

        assertEquals(ColorModel.GRAY, profile.colorModel)
        assertTrue(profile.hasMatrixTrc)
    }

    @Test
    fun `parses para selectors zero through four`() {
        val parameters = listOf(
            floatArrayOf(2f),
            floatArrayOf(2f, 1f, -0.25f),
            floatArrayOf(2f, 1f, -0.25f, 0.1f),
            floatArrayOf(2f, 1f, 0f, 0.5f, 0.25f),
            floatArrayOf(2f, 1f, 0f, 0.1f, 0.25f, 0.4f, 0.01f),
        )

        parameters.forEachIndexed { selector, values ->
            val bytes = resource("srgb-matrix-trc.icc")
            val curveOffset = tagOffset(bytes, IccSignature.R_TRC.value)
            writeU16(bytes, curveOffset + 8, selector)
            values.forEachIndexed { index, value -> writeS15Fixed16(bytes, curveOffset + 12 + index * 4, value) }

            val profile = IccProfileParser.parse(bytes, IccParseLimits()).getOrThrow()

            assertEquals(2f, profile.transferFunction!!.g, 0f, "selector $selector")
        }
    }

    @Test
    fun `parses single value curv as gamma`() {
        val bytes = resource("srgb-matrix-trc.icc")
        val curveOffset = tagOffset(bytes, IccSignature.R_TRC.value)
        writeU32(bytes, curveOffset, IccSignature.CURVE_TYPE.value)
        writeU32(bytes, curveOffset + 8, 1)
        writeU16(bytes, curveOffset + 12, 0x0200)

        val profile = IccProfileParser.parse(bytes, IccParseLimits()).getOrThrow()

        assertEquals(2f, profile.transferFunction!!.g, 0f)
    }

    @Test
    fun `sampled curv returns typed refusal instead of srgb`() {
        val bytes = resource("srgb-matrix-trc.icc")
        val curveOffset = tagOffset(bytes, IccSignature.R_TRC.value)
        writeU32(bytes, curveOffset, IccSignature.CURVE_TYPE.value)
        writeU32(bytes, curveOffset + 8, 3)
        writeU16(bytes, curveOffset + 12, 0)
        writeU16(bytes, curveOffset + 14, 0x4000)
        writeU16(bytes, curveOffset + 16, 0xffff)

        val failure = IccProfileParser.parse(bytes, IccParseLimits()).failureOrNull()!!

        assertEquals("icc.curve.sampled", failure.code)
    }

    @Test
    fun `sampled curv interpolates and inverts monotonically`() {
        val curve = SampledIccCurve(floatArrayOf(0f, 0.25f, 1f))

        assertEquals(0.125f, curve.evaluate(0.25f), 1e-6f)
        assertEquals(0.25f, curve.inverse(0.125f), 1e-6f)
        assertEquals(1f, curve.evaluate(2f), 0f)
    }

    @Test
    fun `para type 0 evaluates gamma`() {
        val curve = ParametricIccCurve(0, floatArrayOf(2f))

        assertEquals(0.25f, curve.evaluate(0.5f), 1e-6f)
        assertEquals(0.5f, curve.inverse(0.25f), 1e-6f)
    }

    @Test
    fun `para type 1 evaluates thresholded gamma`() {
        val curve = ParametricIccCurve(1, floatArrayOf(2f, 1f, -0.25f))

        assertEquals(0f, curve.evaluate(0.2f), 0f)
        assertEquals(0.0625f, curve.evaluate(0.5f), 1e-6f)
        assertEquals(0.5f, curve.inverse(0.0625f), 1e-6f)
    }

    @Test
    fun `para type 2 evaluates offset gamma`() {
        val curve = ParametricIccCurve(2, floatArrayOf(2f, 1f, -0.25f, 0.1f))

        assertEquals(0.1f, curve.evaluate(0.2f), 1e-6f)
        assertEquals(0.1625f, curve.evaluate(0.5f), 1e-6f)
        assertEquals(0.5f, curve.inverse(0.1625f), 1e-6f)
    }

    @Test
    fun `para type 3 evaluates linear lower segment`() {
        val curve = ParametricIccCurve(3, floatArrayOf(2f, 1f, 0f, 0.5f, 0.25f))

        assertEquals(0.1f, curve.evaluate(0.2f), 1e-6f)
        assertEquals(0.25f, curve.evaluate(0.5f), 1e-6f)
        assertEquals(0.2f, curve.inverse(0.1f), 1e-6f)
    }

    @Test
    fun `para type 4 evaluates both offset segments`() {
        val curve = ParametricIccCurve(4, floatArrayOf(2f, 1f, 0f, 0.1f, 0.25f, 0.4f, 0.01f))

        assertEquals(0.09f, curve.evaluate(0.2f), 1e-6f)
        assertEquals(0.35f, curve.evaluate(0.5f), 1e-6f)
        assertEquals(0.2f, curve.inverse(0.09f), 1e-6f)
    }

    private fun parseResource(name: String) = IccProfileParser.parse(resource(name), IccParseLimits()).getOrThrow()

    private fun resource(name: String): ByteArray {
        val stream = assertNotNull(javaClass.classLoader.getResourceAsStream("icc/$name"), "missing icc/$name")
        return stream.use { it.readBytes() }
    }

    private fun tagOffset(bytes: ByteArray, signature: Int): Int {
        val count = readU32(bytes, 128)
        repeat(count) { index ->
            val entry = 132 + index * 12
            if (readU32(bytes, entry) == signature) return readU32(bytes, entry + 4)
        }
        error("missing tag ${IccSignature(signature)}")
    }

    private fun copyFourBytes(bytes: ByteArray, sourceOffset: Int, destinationOffset: Int) {
        repeat(4) { bytes[destinationOffset + it] = bytes[sourceOffset + it] }
    }

    private fun readU32(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xff) shl 24) or
            ((bytes[offset + 1].toInt() and 0xff) shl 16) or
            ((bytes[offset + 2].toInt() and 0xff) shl 8) or
            (bytes[offset + 3].toInt() and 0xff)

    private fun writeU32(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value ushr 24).toByte()
        bytes[offset + 1] = (value ushr 16).toByte()
        bytes[offset + 2] = (value ushr 8).toByte()
        bytes[offset + 3] = value.toByte()
    }

    private fun writeU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value ushr 8).toByte()
        bytes[offset + 1] = value.toByte()
    }

    private fun writeS15Fixed16(bytes: ByteArray, offset: Int, value: Float) {
        writeU32(bytes, offset, (value * 65536f).toInt())
    }

    private fun assertTransferFunctionNear(expected: SkcmsTransferFunction, actual: SkcmsTransferFunction) {
        val differences = listOf(
            abs(expected.g - actual.g),
            abs(expected.a - actual.a),
            abs(expected.b - actual.b),
            abs(expected.c - actual.c),
            abs(expected.d - actual.d),
            abs(expected.e - actual.e),
            abs(expected.f - actual.f),
        )
        assertTrue(differences.all { it <= 2e-5f }, "transfer function differs: $actual")
    }

    private fun assertMatrixNear(expected: SkcmsMatrix3x3, actual: SkcmsMatrix3x3) {
        for (row in 0 until 3) for (column in 0 until 3) {
            assertEquals(expected[row, column], actual[row, column], 1f / 65536f, "matrix[$row,$column]")
        }
    }
}
