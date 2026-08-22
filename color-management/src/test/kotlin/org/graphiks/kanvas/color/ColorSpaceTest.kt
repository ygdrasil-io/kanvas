package org.graphiks.kanvas.color

import org.graphiks.kanvas.color.cicp.CicpColorInfo
import org.graphiks.kanvas.color.cicp.toColorProfile
import org.graphiks.kanvas.color.icc.IccParseLimits
import org.graphiks.kanvas.color.icc.IccProfileParser
import org.graphiks.kanvas.color.icc.IccProfileWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ColorSpaceTest {
    @Test
    fun `classifies built in and serialized RGB profiles without a codec facade`() {
        assertEquals(ColorSpace.SRGB, ColorProfiles.sRGB().toColorSpaceOrNull())
        assertEquals(ColorSpace.DISPLAY_P3, ColorProfiles.displayP3().toColorSpaceOrNull())
        assertEquals(ColorSpace.LINEAR_SRGB, ColorProfile(
            colorModel = ColorModel.RGB,
            toXyzD50 = requireNotNull(ColorProfiles.sRGB().toXyzD50),
            transferFunction = org.graphiks.math.color.ColorTransferFunction.linear,
        ).toColorSpaceOrNull())

        val serializedDisplayP3 = IccProfileParser.parse(
            IccProfileWriter.writeMatrixTrc(ColorProfiles.displayP3()),
            IccParseLimits(),
        ).getOrThrow()

        assertEquals(ColorSpace.DISPLAY_P3, serializedDisplayP3.toColorSpaceOrNull())
    }

    @Test
    fun `classifies supported HDR profiles and refuses unsupported profiles`() {
        val rec2020Pq = CicpColorInfo(
            primaries = 9,
            transfer = 16,
            matrix = 0,
            fullRange = true,
        ).toColorProfile().getOrThrow()

        assertEquals(
            ColorSpace("Rec.2020 PQ", TransferFunction.PQ, Gamut.REC2020),
            rec2020Pq.toColorSpaceOrNull(),
        )
        assertNull(ColorProfile.unsupported("icc.profile.unsupported").toColorSpaceOrNull())
    }

    @Test
    fun `classifies LUT profiles without a matrix as profile failures`() {
        val lutProfile = parseResource("rgb-lut-a2b-b2a.icc")

        assertEquals(
            ColorSpaceClassification.Unsupported(ColorSpaceClassificationFailure.PROFILE),
            lutProfile.classifyColorSpace(),
        )
    }

    private fun parseResource(name: String): ColorProfile {
        val stream = checkNotNull(javaClass.classLoader.getResourceAsStream("icc/$name")) {
            "missing icc/$name"
        }
        return stream.use { IccProfileParser.parse(it.readBytes(), IccParseLimits()).getOrThrow() }
    }
}
