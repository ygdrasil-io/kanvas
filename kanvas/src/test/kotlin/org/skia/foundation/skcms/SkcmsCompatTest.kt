package org.skia.foundation.skcms

import org.graphiks.kanvas.color.ColorModel
import org.graphiks.kanvas.color.ColorProfile
import org.graphiks.kanvas.color.ColorProfiles
import org.graphiks.kanvas.color.icc.IccParseLimits
import org.graphiks.kanvas.color.icc.IccProfileParser
import org.graphiks.math.SkcmsMatrix3x3
import org.graphiks.math.SkcmsTransferFunction
import org.skia.foundation.SkICC
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    @Test
    fun `legacy data class source and binary surface remains available`() {
        val original = SkcmsICCProfile(byteArrayOf(1, 2, 3))
        val (bytes, transferFn, matrix) = original
        val copied = original.copy(bytes = byteArrayOf(4, 5, 6))

        assertContentEquals(byteArrayOf(1, 2, 3), bytes)
        assertEquals(SkNamedTransferFn.kSRGB, transferFn)
        assertEquals(SkNamedGamut.kSRGB, matrix)
        assertContentEquals(byteArrayOf(4, 5, 6), copied.bytes)
        assertEquals(
            "SkcmsICCProfile(bytes=[1, 2, 3], transferFn=${SkNamedTransferFn.kSRGB}, " +
                "toXYZD50=${SkNamedGamut.kSRGB})",
            original.toString(),
        )

        val methods = SkcmsICCProfile::class.java.declaredMethods
        assertTrue(methods.any { it.name == "component1" && it.returnType == ByteArray::class.java })
        assertTrue(methods.any { it.name == "component2" && it.returnType == SkcmsTransferFunction::class.java })
        assertTrue(methods.any { it.name == "component3" && it.returnType == SkcmsMatrix3x3::class.java })
        assertTrue(methods.any { it.name == "copy" && it.parameterCount == 3 })
        val copyDefault = methods.single { it.name == "copy\$default" && it.parameterCount == 6 }
        val bridgeCopy = copyDefault.invoke(null, original, null, null, null, 7, null) as SkcmsICCProfile
        assertEquals(original, bridgeCopy)

        val defaultConstructor = SkcmsICCProfile::class.java.declaredConstructors.single {
            it.parameterCount == 5 && it.parameterTypes[0] == ByteArray::class.java
        }
        val bridgeConstructed = defaultConstructor.newInstance(
            byteArrayOf(7, 8),
            null,
            null,
            6,
            null,
        ) as SkcmsICCProfile
        assertContentEquals(byteArrayOf(7, 8), bridgeConstructed.bytes)
        assertEquals(SkNamedTransferFn.kSRGB, bridgeConstructed.transferFn)
        assertEquals(SkNamedGamut.kSRGB, bridgeConstructed.toXYZD50)
    }

    @Test
    fun `legacy getters remain nonnull for profiles outside the facade subset`() {
        val profile = SkcmsICCProfile.fromColorProfile(ColorProfile(ColorModel.GRAY))

        val transferFn: SkcmsTransferFunction = profile.transferFn
        val matrix: SkcmsMatrix3x3 = profile.toXYZD50

        assertEquals(SkNamedTransferFn.kSRGB, transferFn)
        assertEquals(SkNamedGamut.kSRGB, matrix)
    }

    @Test
    fun `public color profile factory emits bytes consistent with its semantics`() {
        val profile = SkcmsICCProfile.fromColorProfile(ColorProfiles.displayP3())
        val reparsed = IccProfileParser.parse(profile.bytes, IccParseLimits()).getOrThrow()

        assertTransferFunctionNear(
            checkNotNull(profile.colorProfile.transferFunction),
            checkNotNull(reparsed.transferFunction),
        )
        assertMatrixNear(
            checkNotNull(profile.colorProfile.toXyzD50),
            checkNotNull(reparsed.toXyzD50),
        )
        assertTrue(
            SkcmsICCProfile.Companion::class.java.declaredMethods.none {
                it.name == "fromColorProfile" && it.parameterCount == 2
            },
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
