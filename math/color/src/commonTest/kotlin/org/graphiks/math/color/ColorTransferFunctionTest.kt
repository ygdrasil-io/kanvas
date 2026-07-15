package org.graphiks.math.color

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ColorTransferFunctionTest {
    @Test
    fun `sRgb preset values`() {
        val tf = ColorTransferFunction.sRgb
        assertEquals(2.4f, tf.g)
        assertEquals(1f / 1.055f, tf.a, 1e-6f)
        assertEquals(0.055f / 1.055f, tf.b, 1e-6f)
        assertEquals(1f / 12.92f, tf.c, 1e-6f)
        assertEquals(0.04045f, tf.d, 1e-6f)
        assertEquals(0f, tf.e)
        assertEquals(0f, tf.f)
    }

    @Test
    fun `linear preset values`() {
        val tf = ColorTransferFunction.linear
        assertEquals(1f, tf.g)
        assertEquals(1f, tf.a)
        assertEquals(0f, tf.b)
        assertEquals(0f, tf.c)
        assertEquals(0f, tf.d)
        assertEquals(0f, tf.e)
        assertEquals(0f, tf.f)
    }

    @Test
    fun `rec2020 preset values`() {
        val tf = ColorTransferFunction.rec2020
        assertEquals(2.2222222f, tf.g)
        assertEquals(0.9096724f, tf.a, 1e-6f)
        assertEquals(0.0903276f, tf.b, 1e-6f)
        assertEquals(1f / 4.5f, tf.c, 1e-7f)
        assertEquals(0.0812429f, tf.d, 1e-6f)
        assertEquals(0.04f / 4.5f, tf.toLinear(0.04f), 1e-7f)
    }

    @Test
    fun `pq uses its normalized ST 2084 curve`() {
        val tf = ColorTransferFunction.pq
        assertEquals(0f, tf.toLinear(0f), 1e-6f)
        assertEquals(0.0005154176f, tf.toLinear(0.25f), 1e-9f)
        assertEquals(0.009224571f, tf.toLinear(0.5f), 1e-8f)
        assertEquals(0.09833779f, tf.toLinear(0.75f), 1e-7f)
        assertEquals(1f, tf.toLinear(1f), 1e-6f)
        assertFailsWith<IllegalArgumentException> { tf.toLinear(-0.01f) }
        assertFailsWith<IllegalArgumentException> { tf.toLinear(1.01f) }
    }

    @Test
    fun `hlg uses its reference inverse OETF`() {
        val tf = ColorTransferFunction.hlg
        assertEquals(0f, tf.toLinear(0f), 1e-6f)
        assertEquals(0.020833334f, tf.toLinear(0.25f), 1e-8f)
        assertEquals(1f / 12f, tf.toLinear(0.5f), 1e-6f)
        assertEquals(0.26496255f, tf.toLinear(0.75f), 1e-7f)
        assertEquals(1f, tf.toLinear(1f), 1e-6f)
        assertEquals(tf.toLinear(0.499999f), tf.toLinear(0.500001f), 1e-6f)
        assertFailsWith<IllegalArgumentException> { tf.toLinear(Float.NaN) }
    }

    @Test
    fun `advertised transfer functions map normalized white to white`() {
        assertEquals(1f, ColorTransferFunction.sRgb.toLinear(1f), 1e-6f)
        assertEquals(1f, ColorTransferFunction.linear.toLinear(1f), 1e-6f)
        assertEquals(1f, ColorTransferFunction.rec2020.toLinear(1f), 1e-6f)
        assertEquals(1f, ColorTransferFunction.pq.toLinear(1f), 1e-6f)
        assertEquals(1f, ColorTransferFunction.hlg.toLinear(1f), 1e-6f)
    }

    @Test
    fun `data class equality`() {
        val a = ColorTransferFunction.sRgb
        val b = ColorTransferFunction.sRgb
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `parametric creates custom ICC transfer function`() {
        val tf = ColorTransferFunction.parametric(
            g = 2.2f, a = 1f, b = 0f,
            c = 0.1f, d = 0.05f, e = 0f, f = 0f,
        )
        assertEquals(2.2f, tf.g)
        assertEquals(1f, tf.a)
        assertEquals(0f, tf.b)
        assertEquals(0.1f, tf.c)
        assertEquals(0.05f, tf.d)
        assertEquals(0f, tf.e)
        assertEquals(0f, tf.f)
    }
}
