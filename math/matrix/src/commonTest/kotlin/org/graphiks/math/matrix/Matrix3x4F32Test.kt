package org.graphiks.math.matrix

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.graphiks.math.geometry.Point3F32
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
    fun `translation affects a point only`() {
        val matrix = Matrix3x4F32.of(
            2f, 3f, 5f, 7f,
            11f, 13f, 17f, 19f,
            23f, 29f, 31f, 37f,
        )
        val point = Point3F32(41f, 43f, 47f)
        val vector = Vector3F32(41f, 43f, 47f)

        val transformedPoint = matrix.transform(point)
        assertEquals(2f * 41f + 3f * 43f + 5f * 47f + 7f, transformedPoint.x)
        assertEquals(11f * 41f + 13f * 43f + 17f * 47f + 19f, transformedPoint.y)
        assertEquals(23f * 41f + 29f * 43f + 31f * 47f + 37f, transformedPoint.z)
        val operatorPoint = matrix * point
        assertEquals(2f * 41f + 3f * 43f + 5f * 47f + 7f, operatorPoint.x)
        assertEquals(11f * 41f + 13f * 43f + 17f * 47f + 19f, operatorPoint.y)
        assertEquals(23f * 41f + 29f * 43f + 31f * 47f + 37f, operatorPoint.z)

        val transformedVector = matrix.transform(vector)
        assertEquals(2f * 41f + 3f * 43f + 5f * 47f, transformedVector.x)
        assertEquals(11f * 41f + 13f * 43f + 17f * 47f, transformedVector.y)
        assertEquals(23f * 41f + 29f * 43f + 31f * 47f, transformedVector.z)
        val operatorVector = matrix * vector
        assertEquals(2f * 41f + 3f * 43f + 5f * 47f, operatorVector.x)
        assertEquals(11f * 41f + 13f * 43f + 17f * 47f, operatorVector.y)
        assertEquals(23f * 41f + 29f * 43f + 31f * 47f, operatorVector.z)
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
