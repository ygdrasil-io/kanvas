package org.skia.foundation

import org.graphiks.kanvas.color.ColorProfile
import org.graphiks.kanvas.color.ColorProfiles
import org.graphiks.kanvas.color.cicp.CicpColorInfo
import org.graphiks.kanvas.color.cicp.toColorProfile
import org.graphiks.kanvas.color.icc.IccParseLimits
import org.graphiks.kanvas.color.icc.IccProfileParser
import org.skia.foundation.skcms.SkcmsICCProfile
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SkColorSpaceCompatTest {
    @Test
    fun `make preserves display p3 profile semantics`() {
        val profile = SkcmsICCProfile.fromColorProfile(ColorProfiles.displayP3())
        val colorSpace = assertNotNull(SkColorSpace.make(profile))

        assertFalse(colorSpace.isSRGB())
        assertMatrixNear(
            checkNotNull(ColorProfiles.displayP3().toXyzD50),
            colorSpace.toXYZD50,
        )
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
            SkcmsICCProfile.fromColorProfile(lut, lutBytes),
            SkcmsICCProfile.fromColorProfile(hdr),
            SkcmsICCProfile.fromColorProfile(gray, grayBytes),
            SkcmsICCProfile.fromColorProfile(unsupported),
        ).forEach { profile -> assertNull(SkColorSpace.make(profile)) }
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
}
