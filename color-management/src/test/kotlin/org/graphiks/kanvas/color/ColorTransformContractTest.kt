package org.graphiks.kanvas.color

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
}
