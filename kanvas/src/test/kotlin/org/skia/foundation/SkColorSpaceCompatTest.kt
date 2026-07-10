package org.skia.foundation

import org.graphiks.kanvas.color.ColorProfile
import org.graphiks.kanvas.color.ColorProfiles
import org.graphiks.kanvas.color.cicp.CicpColorInfo
import org.graphiks.kanvas.color.cicp.toColorProfile
import org.graphiks.kanvas.color.icc.IccParseLimits
import org.graphiks.kanvas.color.icc.IccProfileParser
import org.graphiks.math.SkcmsMatrix3x3
import org.skia.foundation.skcms.SkNamedGamut
import org.skia.foundation.skcms.SkNamedTransferFn
import org.skia.foundation.skcms.SkcmsICCProfile
import org.skia.foundation.skcms.skcmsParse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SkColorSpaceCompatTest {
    @Test
    fun `makeRGB snapshots its caller supplied matrix and identity flags`() {
        val callerMatrix = copyMatrix(SkNamedGamut.kSRGB)
        val colorSpace = assertNotNull(SkColorSpace.makeRGB(SkNamedTransferFn.kSRGB, callerMatrix))

        callerMatrix.vals[0][0] = -1f

        assertMatrixEquals(SkNamedGamut.kSRGB, colorSpace.toXYZD50)
        assertTrue(colorSpace.isSRGB())
        assertTrue(colorSpace.gammaCloseToSRGB())
        assertFalse(colorSpace.gammaIsLinear())
    }

    @Test
    fun `toXYZD50 returns a defensive matrix copy and preserves identity flags`() {
        val expected = copyMatrix(SkNamedGamut.kSRGB)
        val callerMatrix = copyMatrix(expected)
        val colorSpace = assertNotNull(SkColorSpace.makeRGB(SkNamedTransferFn.kSRGB, callerMatrix))

        colorSpace.toXYZD50.vals[0][0] = -1f

        assertMatrixEquals(expected, colorSpace.toXYZD50)
        assertTrue(colorSpace.isSRGB())
        assertTrue(colorSpace.gammaCloseToSRGB())
        assertFalse(colorSpace.gammaIsLinear())
    }

    @Test
    fun `toXYZD50 mutation cannot contaminate shared sRGB color spaces`() {
        val expected = copyMatrix(SkNamedGamut.kSRGB)
        val exposed = SkColorSpace.makeSRGB().toXYZD50

        try {
            exposed.vals[0][0] = -1f

            assertMatrixEquals(expected, SkColorSpace.makeSRGB().toXYZD50)
            assertMatrixEquals(expected, SkColorSpace.makeSRGBLinear().toXYZD50)
            assertMatrixEquals(expected, SkNamedGamut.kSRGB)
            assertTrue(SkColorSpace.makeSRGB().isSRGB())
            assertFalse(SkColorSpace.makeSRGB().gammaIsLinear())
            assertFalse(SkColorSpace.makeSRGBLinear().isSRGB())
            assertTrue(SkColorSpace.makeSRGBLinear().gammaIsLinear())
        } finally {
            restoreMatrix(exposed, expected)
        }
    }

    @Test
    fun `make preserves display p3 profile semantics`() {
        val profile = SkcmsICCProfile.fromColorProfile(ColorProfiles.displayP3())
        val colorSpace = assertNotNull(SkColorSpace.make(profile))

        assertFalse(colorSpace.isSRGB())
        assertEquals(SkColorSpaceProfileStatus.kSupported, colorSpace.profileStatus)
        assertNull(colorSpace.profileRefusalCode)
        assertMatrixNear(
            checkNotNull(ColorProfiles.displayP3().toXyzD50),
            colorSpace.toXYZD50,
        )
    }

    @Test
    fun `Rec2020 facade and color space match reparsed published bytes exactly`() {
        val facade = SkcmsICCProfile.fromColorProfile(ColorProfiles.rec2020())
        val reparsed = assertNotNull(skcmsParse(facade.bytes))
        val colorSpace = assertNotNull(SkColorSpace.make(facade))

        assertEquals(reparsed.colorProfile, facade.colorProfile)
        assertMatrixEquals(
            checkNotNull(reparsed.colorProfile.toXyzD50),
            checkNotNull(facade.colorProfile.toXyzD50),
        )
        assertMatrixEquals(checkNotNull(reparsed.colorProfile.toXyzD50), colorSpace.toXYZD50)
    }

    @Test
    fun `make refuses LUT HDR gray and unsupported profiles`() {
        val lutBytes = resource("icc/rgb-lut-a2b-b2a.icc")
        val lut = IccProfileParser.parse(lutBytes, IccParseLimits()).getOrThrow()
        val hdr = CicpColorInfo(primaries = 9, transfer = 16, matrix = 0, fullRange = true)
            .toColorProfile()
            .getOrThrow()
        val grayBytes = grayProfileBytes()
        val gray = IccProfileParser.parse(grayBytes, IccParseLimits()).getOrThrow()
        val unsupported = ColorProfile.unsupported("icc.profile.unsupported")

        listOf(
            SkcmsICCProfile.fromParsedColorProfile(lut, lutBytes),
            SkcmsICCProfile.fromColorProfile(hdr),
            SkcmsICCProfile.fromParsedColorProfile(gray, grayBytes),
            SkcmsICCProfile.fromColorProfile(unsupported),
        ).forEach { profile -> assertNull(SkColorSpace.make(profile)) }
    }

    @Test
    fun `profile aware adapter exposes explicit LUT HDR gray and generic refusals without claiming sRGB`() {
        val lutBytes = resource("icc/rgb-lut-a2b-b2a.icc")
        val lut = IccProfileParser.parse(lutBytes, IccParseLimits()).getOrThrow()
        val hdr = CicpColorInfo(primaries = 9, transfer = 16, matrix = 0, fullRange = true)
            .toColorProfile()
            .getOrThrow()
        val grayBytes = grayProfileBytes()
        val gray = IccProfileParser.parse(grayBytes, IccParseLimits()).getOrThrow()
        val unsupported = ColorProfile.unsupported("icc.profile.unsupported")
        val cases = listOf(
            SkcmsICCProfile.fromParsedColorProfile(lut, lutBytes) to "icc.profile.shape.unsupported",
            SkcmsICCProfile.fromColorProfile(hdr) to "color.hdr.unsupported",
            SkcmsICCProfile.fromParsedColorProfile(gray, grayBytes) to "icc.gray.unsupported",
            SkcmsICCProfile.fromColorProfile(unsupported) to "icc.profile.unsupported",
        )

        cases.forEach { (profile, expectedRefusal) ->
            val colorSpace = SkColorSpace.makeProfileAware(profile)

            assertFalse(colorSpace.isSRGB())
            assertEquals(SkColorSpaceProfileStatus.kUnsupported, colorSpace.profileStatus)
            assertEquals(expectedRefusal, colorSpace.profileRefusalCode)
            assertEquals(profile.colorProfile, colorSpace.colorProfile)
        }
    }

    private fun grayProfileBytes(): ByteArray {
        val bytes = SkICC.WriteToICC(
            checkNotNull(ColorProfiles.sRGB().transferFunction),
            checkNotNull(ColorProfiles.sRGB().toXyzD50),
        )
        writeU32(bytes, 16, signature("GRAY"))
        repeat(readU32(bytes, 128)) { index ->
            val entry = 132 + index * 12
            if (readU32(bytes, entry) == signature("rTRC")) {
                writeU32(bytes, entry, signature("kTRC"))
            }
        }
        return bytes
    }

    private fun resource(name: String): ByteArray {
        val stream = assertNotNull(javaClass.classLoader.getResourceAsStream(name), "missing $name")
        return stream.use { it.readBytes() }
    }

    private fun signature(value: String): Int =
        value.fold(0) { result, character -> (result shl 8) or character.code }

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

    private fun assertMatrixNear(
        expected: org.graphiks.math.SkcmsMatrix3x3,
        actual: org.graphiks.math.SkcmsMatrix3x3,
    ) {
        for (row in 0 until 3) for (column in 0 until 3) {
            kotlin.test.assertEquals(expected[row, column], actual[row, column], 1f / 65_536f)
        }
    }

    private fun assertMatrixEquals(
        expected: SkcmsMatrix3x3,
        actual: SkcmsMatrix3x3,
    ) {
        for (row in 0 until 3) for (column in 0 until 3) {
            kotlin.test.assertEquals(expected[row, column], actual[row, column])
        }
    }

    private fun copyMatrix(matrix: SkcmsMatrix3x3): SkcmsMatrix3x3 = SkcmsMatrix3x3.of(
        matrix[0, 0], matrix[0, 1], matrix[0, 2],
        matrix[1, 0], matrix[1, 1], matrix[1, 2],
        matrix[2, 0], matrix[2, 1], matrix[2, 2],
    )

    private fun restoreMatrix(target: SkcmsMatrix3x3, source: SkcmsMatrix3x3) {
        for (row in 0 until 3) for (column in 0 until 3) {
            target.vals[row][column] = source[row, column]
        }
    }
}
