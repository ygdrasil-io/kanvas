package org.graphiks.kanvas.color.icc

import org.graphiks.kanvas.color.ColorModel
import org.graphiks.kanvas.color.ColorProfile
import org.graphiks.kanvas.color.ColorProfiles
import org.graphiks.math.SkcmsMatrix3x3
import org.graphiks.math.SkcmsTransferFunction
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class IccProfileWriterTest {
    @Test
    fun `writes valid ICC v4 matrix trc profiles for supported RGB spaces`() {
        val profiles = listOf(
            ColorProfiles.sRGB(),
            ColorProfiles.displayP3(),
            ColorProfiles.rec2020(),
            ColorProfile(
                colorModel = ColorModel.RGB,
                toXyzD50 = checkNotNull(ColorProfiles.rec2020().toXyzD50),
                transferFunction = LINEAR_TRANSFER,
            ),
        )

        profiles.forEach { expected ->
            val bytes = IccProfileWriter.writeMatrixTrc(expected)
            val actual = IccProfileParser.parse(bytes, IccParseLimits()).getOrThrow()

            assertEquals(4, bytes[8].toInt() and 0xff)
            val hasNegativePcsXyz = checkNotNull(expected.toXyzD50).let { matrix ->
                (0 until 3).any { row -> (0 until 3).any { column -> matrix[row, column] < 0f } }
            }
            assertEquals(if (hasNegativePcsXyz) 4 else 3, bytes[9].toInt() ushr 4)
            assertEquals("acsp", ascii(bytes, 36))
            assertEquals("mntr", ascii(bytes, 12))
            assertEquals("RGB ", ascii(bytes, 16))
            assertEquals("XYZ ", ascii(bytes, 20))
            assertTrue(bytes.slice(100 until 128).all { it == 0.toByte() })
            assertMatrixNear(checkNotNull(expected.toXyzD50), checkNotNull(actual.toXyzD50))
            assertQuantizedWhiteIsD50(bytes)
            assertTransferFunctionNear(
                checkNotNull(expected.transferFunction),
                checkNotNull(actual.transferFunction),
            )
        }
    }

    @Test
    fun `writes required tags as contiguous aligned elements with para type four curves`() {
        val bytes = IccProfileWriter.writeMatrixTrc(ColorProfiles.displayP3())
        val expectedSignatures = intArrayOf(
            signature("desc"),
            signature("cprt"),
            signature("wtpt"),
            signature("rXYZ"),
            signature("gXYZ"),
            signature("bXYZ"),
            signature("rTRC"),
            signature("gTRC"),
            signature("bTRC"),
        )

        assertEquals(expectedSignatures.size, readU32(bytes, 128))
        assertContentEquals(
            expectedSignatures,
            IntArray(expectedSignatures.size) { readU32(bytes, 132 + it * 12) },
        )

        var expectedOffset = 132 + expectedSignatures.size * 12
        expectedSignatures.forEachIndexed { index, signature ->
            val entryOffset = 132 + index * 12
            val tagOffset = readU32(bytes, entryOffset + 4)
            val tagSize = readU32(bytes, entryOffset + 8)
            assertEquals(expectedOffset, tagOffset, IccSignature(signature).toString())
            assertEquals(listOf<Byte>(0, 0, 0, 0), bytes.slice(tagOffset + 4 until tagOffset + 8))
            if (signature == signature("rTRC") || signature == signature("gTRC") || signature == signature("bTRC")) {
                assertEquals("para", ascii(bytes, tagOffset))
                assertEquals(4, readU16(bytes, tagOffset + 8))
                assertEquals(0, readU16(bytes, tagOffset + 10))
            }
            expectedOffset = align4(tagOffset + tagSize)
        }
        assertEquals(bytes.size, expectedOffset)
    }

    @Test
    fun `quantizes signed fixed values without overflow or non finite coercion`() {
        val nanMatrix = SkcmsMatrix3x3.of(
            Float.NaN, 0f, 0f,
            0f, 1f, 0f,
            0f, 0f, 1f,
        )
        val overflowMatrix = SkcmsMatrix3x3.of(
            32_768f, 0f, 0f,
            0f, 1f, 0f,
            0f, 0f, 1f,
        )

        listOf(nanMatrix, overflowMatrix).forEach { matrix ->
            assertFailsWith<IllegalArgumentException> {
                IccProfileWriter.writeMatrixTrc(
                    ColorProfile(ColorModel.RGB, matrix, checkNotNull(ColorProfiles.sRGB().transferFunction)),
                )
            }
        }
    }

    @Test
    fun `rejects a matrix that becomes singular in ICC fixed point`() {
        val singularMatrix = SkcmsMatrix3x3.of(
            1f, 0f, 0f,
            1f, 0f, 0f,
            0f, 0f, 1f,
        )

        assertFailsWith<IllegalArgumentException> {
            IccProfileWriter.writeMatrixTrc(
                ColorProfile(
                    colorModel = ColorModel.RGB,
                    toXyzD50 = singularMatrix,
                    transferFunction = checkNotNull(ColorProfiles.sRGB().transferFunction),
                ),
            )
        }
    }

    @Test
    fun `rejects an invertible RGB matrix whose white is not D50`() {
        val identity = SkcmsMatrix3x3.of(
            1f, 0f, 0f,
            0f, 1f, 0f,
            0f, 0f, 1f,
        )

        assertFailsWith<IllegalArgumentException> {
            IccProfileWriter.writeMatrixTrc(
                ColorProfile(
                    colorModel = ColorModel.RGB,
                    toXyzD50 = identity,
                    transferFunction = checkNotNull(ColorProfiles.sRGB().transferFunction),
                ),
            )
        }
    }

    private fun assertMatrixNear(expected: SkcmsMatrix3x3, actual: SkcmsMatrix3x3) {
        for (row in 0 until 3) for (column in 0 until 3) {
            assertEquals(expected[row, column], actual[row, column], WHITE_NORMALIZATION_TOLERANCE)
        }
    }

    private fun assertQuantizedWhiteIsD50(bytes: ByteArray) {
        val matrix = Array(3) { IntArray(3) }
        listOf("rXYZ", "gXYZ", "bXYZ").forEachIndexed { column, tag ->
            val tagOffset = findTag(bytes, signature(tag))
            repeat(3) { row -> matrix[row][column] = readU32(bytes, tagOffset + 8 + row * 4) }
        }
        val expected = intArrayOf(
            readU32(bytes, 68),
            readU32(bytes, 72),
            readU32(bytes, 76),
        )
        repeat(3) { row -> assertEquals(expected[row], matrix[row].sum(), "D50 row $row") }
    }

    private fun findTag(bytes: ByteArray, wanted: Int): Int {
        repeat(readU32(bytes, 128)) { index ->
            val entry = 132 + index * 12
            if (readU32(bytes, entry) == wanted) return readU32(bytes, entry + 4)
        }
        error("missing ICC tag ${IccSignature(wanted)}")
    }

    private fun assertTransferFunctionNear(expected: SkcmsTransferFunction, actual: SkcmsTransferFunction) {
        val expectedValues = listOf(expected.g, expected.a, expected.b, expected.c, expected.d, expected.e, expected.f)
        val actualValues = listOf(actual.g, actual.a, actual.b, actual.c, actual.d, actual.e, actual.f)
        assertTrue(
            expectedValues.zip(actualValues).all { (left, right) -> abs(left - right) <= 2f * FIXED_TOLERANCE },
            "transfer function differs: expected=$expected actual=$actual",
        )
    }

    private fun ascii(bytes: ByteArray, offset: Int): String =
        bytes.copyOfRange(offset, offset + 4).toString(Charsets.US_ASCII)

    private fun signature(value: String): Int {
        require(value.length == 4)
        return value.fold(0) { result, character -> (result shl 8) or character.code }
    }

    private fun readU16(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xff) shl 8) or (bytes[offset + 1].toInt() and 0xff)

    private fun readU32(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xff) shl 24) or
            ((bytes[offset + 1].toInt() and 0xff) shl 16) or
            ((bytes[offset + 2].toInt() and 0xff) shl 8) or
            (bytes[offset + 3].toInt() and 0xff)

    private fun align4(value: Int): Int = (value + 3) and -4

    private companion object {
        const val FIXED_TOLERANCE: Float = 1f / 65_536f
        const val WHITE_NORMALIZATION_TOLERANCE: Float = 64f / 65_536f
        val LINEAR_TRANSFER: SkcmsTransferFunction = SkcmsTransferFunction(
            g = 1f,
            a = 1f,
            b = 0f,
            c = 1f,
            d = 0f,
            e = 0f,
            f = 0f,
        )
    }
}
