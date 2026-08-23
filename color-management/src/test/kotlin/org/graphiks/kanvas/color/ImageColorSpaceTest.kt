package org.graphiks.kanvas.color

import org.graphiks.kanvas.color.icc.IccProfile
import org.graphiks.kanvas.color.cicp.CicpColorInfo
import org.graphiks.kanvas.color.cicp.toColorProfile
import org.graphiks.math.color.ColorTransferFunction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImageColorSpaceTest {
    @Test
    fun `creates a supported named sRGB image color space`() {
        val colorSpace = ImageColorSpace.sRGB()

        assertEquals(ImageColorSpaceProfileStatus.SUPPORTED, colorSpace.profileStatus)
        assertNull(colorSpace.profileRefusalCode)
        assertEquals(ColorSpace.SRGB, colorSpace.toColorSpaceOrNull())
        assertTrue(colorSpace.isSrgb())
        assertFalse(colorSpace.isLinear())
        assertNull(colorSpace.iccProfile)
    }

    @Test
    fun `creates supported custom Matrix TRC image color spaces`() {
        val colorSpace = ImageColorSpace.fromMatrixTrc(
            requireNotNull(ColorProfiles.displayP3().transferFunction),
            requireNotNull(ColorProfiles.displayP3().toXyzD50),
        )

        assertEquals(ImageColorSpaceProfileStatus.SUPPORTED, colorSpace.profileStatus)
        assertEquals(ColorSpace.DISPLAY_P3, colorSpace.toColorSpaceOrNull())
        assertFalse(colorSpace.isSrgb())
    }

    @Test
    fun `retains a LUT ICC profile with its existing refusal code`() {
        val colorSpace = ImageColorSpace.fromIccProfile(parseResource("rgb-lut-a2b-b2a.icc"))

        assertEquals(ImageColorSpaceProfileStatus.UNSUPPORTED, colorSpace.profileStatus)
        assertEquals("icc.profile.shape.unsupported", colorSpace.profileRefusalCode)
        assertNotNull(colorSpace.iccProfile)
    }

    @Test
    fun `retains explicit profile refusal codes`() {
        val colorSpace = ImageColorSpace.fromColorProfile(ColorProfile.unsupported("profile.unsupported"))

        assertEquals(ImageColorSpaceProfileStatus.UNSUPPORTED, colorSpace.profileStatus)
        assertEquals("profile.unsupported", colorSpace.profileRefusalCode)
    }

    @Test
    fun `does not expose Matrix TRC components from refused profiles`() {
        val matrix = requireNotNull(ColorProfiles.sRGB().toXyzD50)
        val transfer = requireNotNull(ColorProfiles.sRGB().transferFunction)
        val hdr = CicpColorInfo(primaries = 9, transfer = 16, matrix = 0, fullRange = true)
            .toColorProfile()
            .getOrThrow()
        val profiles = listOf(
            ColorProfile(ColorModel.GRAY, matrix, transfer),
            hdr,
            ColorProfile(ColorModel.RGB, matrix, transfer, unsupportedCode = "profile.unsupported"),
        )

        profiles.forEach { profile ->
            val colorSpace = ImageColorSpace.fromColorProfile(profile)

            assertEquals(ImageColorSpaceProfileStatus.UNSUPPORTED, colorSpace.profileStatus)
            assertNull(colorSpace.transferFunction)
            assertNull(colorSpace.toXyzD50)
            assertNull(colorSpace.toColorSpaceOrNull())
            assertFalse(colorSpace.isLinear())
        }
    }

    private fun parseResource(name: String): IccProfile {
        val stream = checkNotNull(javaClass.classLoader.getResourceAsStream("icc/$name")) {
            "missing icc/$name"
        }
        return stream.use { IccProfile.parse(it.readBytes()).getOrThrow() }
    }
}
