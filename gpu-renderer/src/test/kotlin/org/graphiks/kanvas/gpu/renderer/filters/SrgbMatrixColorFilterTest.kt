package org.graphiks.kanvas.gpu.renderer.filters

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals

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
    fun `descriptor packs every ColorMatrix uniform row translation and alpha at exact ABI offsets`() {
        val matrix = FloatArray(20) { index -> (index + 1) / 10f }
        val descriptor = SrgbMatrixColorFilterDescriptor(matrix)

        val bytes = descriptor.packNativeUniform(0.11f, 0.22f, 0.33f, 0.44f)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        assertEquals(96, bytes.size)
        assertEquals(0.11f, buffer.getFloat(0), 0f)
        assertEquals(0.22f, buffer.getFloat(4), 0f)
        assertEquals(0.33f, buffer.getFloat(8), 0f)
        assertEquals(0.44f, buffer.getFloat(12), 0f)
        assertEquals(listOf(0.1f, 0.2f, 0.3f, 0.4f), floatsAt(buffer, 16, 4))
        assertEquals(listOf(0.6f, 0.7f, 0.8f, 0.9f), floatsAt(buffer, 32, 4))
        assertEquals(listOf(1.1f, 1.2f, 1.3f, 1.4f), floatsAt(buffer, 48, 4))
        assertEquals(listOf(1.6f, 1.7f, 1.8f, 1.9f), floatsAt(buffer, 64, 4))
        assertEquals(listOf(0.5f, 1.0f, 1.5f, 2.0f), floatsAt(buffer, 80, 4))
    }
}

private fun floatsAt(buffer: ByteBuffer, offset: Int, count: Int): List<Float> =
    List(count) { index -> buffer.getFloat(offset + index * 4) }
