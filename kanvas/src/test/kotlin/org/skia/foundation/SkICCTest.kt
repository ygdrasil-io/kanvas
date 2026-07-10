package org.skia.foundation

import org.graphiks.kanvas.color.ColorProfiles
import org.graphiks.kanvas.color.icc.IccParseLimits
import org.graphiks.kanvas.color.icc.IccProfileParser
import org.skia.foundation.skcms.SkNamedGamut
import org.skia.foundation.skcms.SkNamedTransferFn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SkICCTest {
    @Test
    fun `WriteToICC emits a parsable display p3 profile`() {
        val bytes = SkICC.WriteToICC(SkNamedTransferFn.kSRGB, SkNamedGamut.kDisplayP3)
        val profile = IccProfileParser.parse(bytes, IccParseLimits()).getOrThrow()

        assertEquals("acsp", bytes.copyOfRange(36, 40).toString(Charsets.US_ASCII))
        assertEquals(4, bytes[8].toInt() and 0xff)
        assertTrue(bytes.slice(100 until 128).all { it == 0.toByte() })
        assertEquals(ColorProfiles.displayP3().colorModel, profile.colorModel)
        assertMatrixNear(
            checkNotNull(ColorProfiles.displayP3().toXyzD50),
            checkNotNull(profile.toXyzD50),
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
}
