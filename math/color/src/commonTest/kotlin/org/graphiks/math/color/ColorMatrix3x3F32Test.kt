package org.graphiks.math.color

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ColorMatrix3x3F32Test {
    @Test
    fun `maps RGB without a homogeneous divide`() {
        val matrix = ColorMatrix3x3F32.of(
            1f, 2f, 3f,
            0f, 1f, 4f,
            5f, 6f, 0f,
        )
        val output = FloatArray(3)

        matrix.map(floatArrayOf(1f, 2f, 3f), 0, output, 0)

        assertContentEquals(floatArrayOf(14f, 14f, 17f), output)
    }

    @Test
    fun `row major access exposes the supplied coefficients`() {
        val matrix = ColorMatrix3x3F32.of(
            1f, 2f, 3f,
            4f, 5f, 6f,
            7f, 8f, 9f,
        )

        assertEquals(1f, matrix[0, 0])
        assertEquals(6f, matrix[1, 2])
        assertEquals(8f, matrix[2, 1])
    }

    @Test
    fun `row major import and export make defensive copies`() {
        val source = FloatArray(9) { it.toFloat() }
        val matrix = ColorMatrix3x3F32.fromRowMajor(source)
        source[0] = 99f
        val exported = matrix.toFloatArray()
        exported[1] = 88f

        assertEquals(0f, matrix[0, 0])
        assertEquals(1f, matrix[0, 1])
    }

    @Test
    fun `identity preserves RGB components`() {
        val output = FloatArray(3)

        ColorMatrix3x3F32.Identity.map(floatArrayOf(0.25f, 0.5f, 0.75f), 0, output, 0)

        assertContentEquals(floatArrayOf(0.25f, 0.5f, 0.75f), output)
    }

    @Test
    fun `concat computes this times right`() {
        val left = ColorMatrix3x3F32.of(
            2f, 0f, 0f,
            0f, 3f, 0f,
            0f, 0f, 4f,
        )
        val right = ColorMatrix3x3F32.of(
            1f, 1f, 0f,
            0f, 1f, 1f,
            1f, 0f, 1f,
        )
        val output = FloatArray(3)

        left.concat(right).map(floatArrayOf(1f, 2f, 3f), 0, output, 0)

        assertContentEquals(floatArrayOf(6f, 15f, 16f), output)
    }

    @Test
    fun `equal matrices have structural equality`() {
        val values = FloatArray(9) { it.toFloat() }
        val first = ColorMatrix3x3F32.fromRowMajor(values)
        val second = ColorMatrix3x3F32.fromRowMajor(values)

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        assertNotEquals(ColorMatrix3x3F32.Identity, first)
    }

    @Test
    fun `construction permits non finite coefficients for profile validation`() {
        val matrix = ColorMatrix3x3F32.of(
            Float.NaN, 0f, 0f,
            0f, Float.POSITIVE_INFINITY, 0f,
            0f, 0f, 1f,
        )

        assertEquals(Float.NaN, matrix[0, 0])
        assertEquals(Float.POSITIVE_INFINITY, matrix[1, 1])
    }

    @Test
    fun `inverse accepts finite near singular colour matrices`() {
        val matrix = ColorMatrix3x3F32.of(
            1f, 0f, 0f,
            0f, 1e-12f, 0f,
            0f, 0f, 1f,
        )

        assertNotNull(matrix.inverseOrNull())
    }

    @Test
    fun `inverse of a non diagonal matrix composes to identity`() {
        val matrix = ColorMatrix3x3F32.of(
            1f, 2f, 3f,
            0f, 1f, 4f,
            5f, 6f, 0f,
        )
        val inverse = assertNotNull(matrix.inverseOrNull())

        assertContentEquals(ColorMatrix3x3F32.Identity.toFloatArray(), matrix.concat(inverse).toFloatArray())
    }

    @Test
    fun `inverse rejects singular and overflowing matrices`() {
        assertNull(ColorMatrix3x3F32.of(
            1f, 0f, 0f,
            1f, 0f, 0f,
            0f, 0f, 1f,
        ).inverseOrNull())
        assertNull(ColorMatrix3x3F32.of(
            1e-39f, 0f, 0f,
            0f, 1e-39f, 0f,
            0f, 0f, 1e-39f,
        ).inverseOrNull())
    }

    @Test
    fun `inverse rejects non finite coefficients`() {
        assertNull(ColorMatrix3x3F32.of(
            Float.NaN, 0f, 0f,
            0f, 1f, 0f,
            0f, 0f, 1f,
        ).inverseOrNull())
    }
}
