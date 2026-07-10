package org.skia.foundation.skcms

import org.graphiks.kanvas.color.ColorProfiles
import org.skia.foundation.SkICC
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SkcmsCompatTest {
    @Test
    fun `parse rejects the former synthetic selector envelope`() {
        val bytes = ByteArray(132)
        writeU32(bytes, 0, bytes.size)
        "acsp".toByteArray().copyInto(bytes, destinationOffset = 36)
        bytes[128] = 1
        bytes[129] = 1
        bytes[130] = 'K'.code.toByte()
        bytes[131] = 'V'.code.toByte()

        assertNull(skcmsParse(bytes))
    }

    @Test
    fun `parsed profile owns immutable original bytes`() {
        val original = SkICC.WriteToICC(SkNamedTransferFn.kSRGB, SkNamedGamut.kDisplayP3)
        val expected = original.copyOf()
        val profile = assertNotNull(skcmsParse(original))

        original.fill(0)
        profile.bytes.fill(0)
        profile.buffer!!.fill(0)

        assertContentEquals(expected, profile.bytes)
        assertContentEquals(expected, profile.buffer)
        assertEquals(expected.size, profile.size)
    }

    @Test
    fun `parsed display p3 profile preserves color semantics`() {
        val bytes = SkICC.WriteToICC(SkNamedTransferFn.kSRGB, SkNamedGamut.kDisplayP3)
        val profile = assertNotNull(skcmsParse(bytes))

        assertEquals(ColorProfiles.displayP3().colorModel, profile.colorProfile.colorModel)
        assertTransferFunctionNear(
            checkNotNull(ColorProfiles.displayP3().transferFunction),
            checkNotNull(profile.colorProfile.transferFunction),
        )
        assertMatrixNear(
            checkNotNull(ColorProfiles.displayP3().toXyzD50),
            checkNotNull(profile.colorProfile.toXyzD50),
        )
    }

    private fun assertMatrixNear(
        expected: org.graphiks.math.SkcmsMatrix3x3,
        actual: org.graphiks.math.SkcmsMatrix3x3,
    ) {
        for (row in 0 until 3) for (column in 0 until 3) {
            assertEquals(expected[row, column], actual[row, column], 1f / 65_536f)
        }
    }

    private fun assertTransferFunctionNear(
        expected: org.graphiks.math.SkcmsTransferFunction,
        actual: org.graphiks.math.SkcmsTransferFunction,
    ) {
        val expectedValues = listOf(expected.g, expected.a, expected.b, expected.c, expected.d, expected.e, expected.f)
        val actualValues = listOf(actual.g, actual.a, actual.b, actual.c, actual.d, actual.e, actual.f)
        kotlin.test.assertTrue(
            expectedValues.zip(actualValues).all { (left, right) -> abs(left - right) <= 2f / 65_536f },
            "transfer function differs: expected=$expected actual=$actual",
        )
    }

    private fun writeU32(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value ushr 24).toByte()
        bytes[offset + 1] = (value ushr 16).toByte()
        bytes[offset + 2] = (value ushr 8).toByte()
        bytes[offset + 3] = value.toByte()
    }
}
