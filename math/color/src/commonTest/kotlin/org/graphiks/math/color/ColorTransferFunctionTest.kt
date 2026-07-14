package org.graphiks.math.color

import kotlin.test.Test
import kotlin.test.assertEquals
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
        assertTrue(tf.c > 0f)
        assertEquals(0.0812429f, tf.d, 1e-6f)
    }

    @Test
    fun `pq preset values`() {
        val tf = ColorTransferFunction.pq
        assertEquals(0.8359375f, tf.g)
        assertEquals(0.1593018f, tf.a, 1e-6f)
        assertEquals(0f, tf.b)
        assertEquals(0f, tf.d)
    }

    @Test
    fun `hlg preset values`() {
        val tf = ColorTransferFunction.hlg
        assertEquals(1.2f, tf.g)
        assertEquals(0.7746413f, tf.a, 1e-6f)
        assertEquals(0.0042930f, tf.b, 1e-6f)
        assertTrue(tf.c > 0f)
        assertEquals(0f, tf.d)
    }

    @Test
    fun `data class equality`() {
        val a = ColorTransferFunction.sRgb
        val b = ColorTransferFunction.sRgb
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }
}
