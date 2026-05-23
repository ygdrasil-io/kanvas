package org.graphiks.math

import kotlin.test.Test
import kotlin.test.assertEquals

class SkMathBackendTest {
    @Test
    fun `dot products match scalar reference`() {
        assertEquals(
            SkMathScalar.dot2(1.25f, -2.5f, 3.5f, 4.25f),
            SkMathBackend.dot2(1.25f, -2.5f, 3.5f, 4.25f),
        )
        assertEquals(
            SkMathScalar.dot3(1.25f, -2.5f, 3.5f, 4.25f, -5.5f, 6.75f),
            SkMathBackend.dot3(1.25f, -2.5f, 3.5f, 4.25f, -5.5f, 6.75f),
        )
        assertEquals(
            SkMathScalar.dot4(1.25f, -2.5f, 3.5f, -4.5f, 4.25f, -5.5f, 6.75f, 7.25f),
            SkMathBackend.dot4(1.25f, -2.5f, 3.5f, -4.5f, 4.25f, -5.5f, 6.75f, 7.25f),
        )
    }

    @Test
    fun `m44 concat matches scalar reference`() {
        val a = floatArrayOf(
            1.25f, -0.10f, 0.33f, 0.0f,
            0.20f, 0.75f, -0.25f, 0.0f,
            0.50f, -0.40f, 1.10f, 0.0f,
            13f, -7f, 3f, 1f,
        )
        val b = floatArrayOf(
            0.90f, 0.25f, 0.10f, 0.001f,
            -0.20f, 1.10f, 0.15f, -0.002f,
            0.05f, -0.35f, 0.80f, 0.003f,
            5f, 7f, -2f, 1f,
        )
        val scalar = FloatArray(16)
        val backend = FloatArray(16)

        SkMathScalar.m44Concat(a, b, scalar)
        SkMathBackend.m44Concat(a, b, backend)

        assertEquals(scalar.toList(), backend.toList())
    }
}
