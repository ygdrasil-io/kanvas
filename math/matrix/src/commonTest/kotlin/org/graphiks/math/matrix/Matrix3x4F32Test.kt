package org.graphiks.math.matrix

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.graphiks.math.vector.Vector3F32

class Matrix3x4F32Test {

    @Test
    fun `row-major factory exposes all coefficients`() {
        val matrix = Matrix3x4F32.of(
            1f, 2f, 3f, 4f,
            5f, 6f, 7f, 8f,
            9f, 10f, 11f, 12f,
        )

        assertEquals(1f, matrix[0, 0])
        assertEquals(4f, matrix[0, 3])
        assertEquals(8f, matrix.rc(1, 3))
        assertEquals(9f, matrix.m20)
        assertEquals(12f, matrix.m23)
        assertEquals(
            floatArrayOf(1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f, 10f, 11f, 12f).toList(),
            matrix.toFloatArray().toList(),
        )
    }

    @Test
    fun `maps a 3D vector using the affine fourth column`() {
        val matrix = Matrix3x4F32.of(
            2f, 3f, 5f, 7f,
            11f, 13f, 17f, 19f,
            23f, 29f, 31f, 37f,
        )

        assertEquals(Vector3F32.of(30f, 107f, 211f), matrix.map(Vector3F32.of(1f, 2f, 3f)))
    }

    @Test
    fun `array factory validates its shape and copies input`() {
        val values = FloatArray(12) { it.toFloat() }
        val matrix = Matrix3x4F32.fromRowMajor(values)
        values[0] = 99f

        assertEquals(0f, matrix.m00)
        assertFailsWith<IllegalArgumentException> {
            Matrix3x4F32.fromRowMajor(FloatArray(11))
        }
    }

    @Test
    fun `element access rejects invalid coordinates`() {
        val matrix = Matrix3x4F32.zero()

        assertFailsWith<IllegalArgumentException> { matrix[-1, 0] }
        assertFailsWith<IllegalArgumentException> { matrix[3, 0] }
        assertFailsWith<IllegalArgumentException> { matrix[0, 4] }
    }
}
