package org.graphiks.kanvas.color

import org.graphiks.math.SkcmsMatrix3x3
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class ColorTransformContractTest {

    @Test
    fun `compile rejects unsupported source profile`() {
        val result = ColorTransform.compile(
            source = ColorProfile.unsupported("icc.lut.missing"),
            destination = ColorProfiles.sRGB(),
            alphaType = AlphaType.UNPREMULTIPLIED,
        )

        assertEquals("icc.lut.missing", result.failureOrNull()!!.code)
    }

    @Test
    fun `compile rejects gray profiles`() {
        val result = ColorTransform.compile(
            source = ColorProfile(colorModel = ColorModel.GRAY),
            destination = ColorProfile(colorModel = ColorModel.GRAY),
            alphaType = AlphaType.UNPREMULTIPLIED,
        )

        assertEquals("color.profile.unsupported", result.failureOrNull()!!.code)
    }

    @Test
    fun `compile rejects incomplete rgb profiles`() {
        val result = ColorTransform.compile(
            source = ColorProfile(
                colorModel = ColorModel.RGB,
                transferFunction = ColorProfiles.sRGB().transferFunction,
            ),
            destination = ColorProfile(
                colorModel = ColorModel.RGB,
                transferFunction = ColorProfiles.sRGB().transferFunction,
            ),
            alphaType = AlphaType.UNPREMULTIPLIED,
        )

        assertEquals("color.profile.unsupported", result.failureOrNull()!!.code)
    }

    @Test
    fun `identical spaces use no op transform`() {
        val result = ColorTransform.compile(
            source = ColorProfiles.sRGB(),
            destination = ColorProfiles.sRGB(),
            alphaType = AlphaType.UNPREMULTIPLIED,
        ).getOrThrow()
        val pixels = floatArrayOf(0.25f, 0.5f, 0.75f, 0.5f)

        result.apply(pixels, 1)

        assertContentEquals(floatArrayOf(0.25f, 0.5f, 0.75f, 0.5f), pixels)
    }

    @Test
    fun `compiled transforms retain a copy of caller supplied matrix`() {
        val matrix = SkcmsMatrix3x3.IDENTITY
        val profile = ColorProfile(
            colorModel = ColorModel.RGB,
            toXyzD50 = matrix,
            transferFunction = ColorProfiles.sRGB().transferFunction,
        )
        val transform = ColorTransform.compile(
            source = profile,
            destination = profile,
            alphaType = AlphaType.UNPREMULTIPLIED,
        ).getOrThrow()

        matrix.vals[0][0] = 0f
        val pixels = floatArrayOf(0.25f, 0.5f, 0.75f, 0.5f)
        transform.apply(pixels, 1)

        assertEquals(1f, profile.toXyzD50!![0, 0])
        assertContentEquals(floatArrayOf(0.25f, 0.5f, 0.75f, 0.5f), pixels)
    }
}
