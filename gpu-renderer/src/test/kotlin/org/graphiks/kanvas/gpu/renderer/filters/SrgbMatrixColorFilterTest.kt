package org.graphiks.kanvas.gpu.renderer.filters

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SrgbMatrixColorFilterTest {
    @Test
    fun `identity preserves encoded sRGB straight color before premultiplication`() {
        val filter = SrgbMatrixColorFilter(SrgbMatrixColorFilterDescriptor(ColorMatrix.identity()))

        val actual = filter.applyEncodedStraightRgba(0.5f, 0.25f, 0.75f, 0.5f)

        assertEquals(0.25f, actual[0], 1e-5f)
        assertEquals(0.125f, actual[1], 1e-5f)
        assertEquals(0.375f, actual[2], 1e-5f)
        assertEquals(0.5f, actual[3], 1e-5f)
    }

    @Test
    fun `matrix applies in linear sRGB then encodes and premultiplies`() {
        val matrix = floatArrayOf(
            0.5f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
        val filter = SrgbMatrixColorFilter(SrgbMatrixColorFilterDescriptor(matrix))

        val actual = filter.applyEncodedStraightRgba(0.5f, 0.25f, 0.75f, 0.5f)

        // decode(0.5)=0.214041..., encode(decode(0.5)*0.5)=0.360780..., then premul by 0.5.
        assertEquals(0.18039f, actual[0], 1e-4f)
        assertEquals(0.125f, actual[1], 1e-5f)
        assertEquals(0.375f, actual[2], 1e-5f)
        assertEquals(0.5f, actual[3], 1e-5f)
    }

    @Test
    fun `descriptor packs the exact native ColorMatrix uniform ABI`() {
        val descriptor = SrgbMatrixColorFilterDescriptor(ColorMatrix.identity())

        val bytes = descriptor.packNativeUniform(0.5f, 0.25f, 0.75f, 0.5f)

        assertEquals(96, bytes.size)
        assertTrue(bytes.any { it.toInt() != 0 })
    }
}
