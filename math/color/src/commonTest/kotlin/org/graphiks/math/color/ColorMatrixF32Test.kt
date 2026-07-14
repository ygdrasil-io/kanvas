package org.graphiks.math.color

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ColorMatrixF32Test {
    @Test
    fun `default is identity`() {
        val m = ColorMatrixF32()
        val arr = m.toFloatArray()
        assertEquals(1f, arr[0])  // R scale
        assertEquals(1f, arr[6])  // G scale
        assertEquals(1f, arr[12]) // B scale
        assertEquals(1f, arr[18]) // A scale
        assertEquals(0f, arr[4])  // R bias
        assertEquals(0f, arr[9])  // G bias
    }

    @Test
    fun `setScale creates diagonal matrix`() {
        val m = ColorMatrixF32()
        m.setScale(0.5f, 0.25f, 0.125f)
        val arr = m.toFloatArray()
        assertEquals(0.5f, arr[0])
        assertEquals(0.25f, arr[6])
        assertEquals(0.125f, arr[12])
        assertEquals(1f, arr[18])  // A scale unchanged
        assertEquals(0f, arr[1])   // off-diagonals zero
    }

    @Test
    fun `setSaturation zero produces grayscale`() {
        val m = ColorMatrixF32()
        m.setSaturation(0f)
        val arr = m.toFloatArray()
        // All three RGB rows should have same luma weights
        assertEquals(arr[0], arr[5], 1e-6f)
        assertEquals(arr[0], arr[10], 1e-6f)
    }

    @Test
    fun `setSaturation one produces identity-like`() {
        val m = ColorMatrixF32()
        m.setSaturation(1f)
        val arr = m.toFloatArray()
        assertEquals(1f, arr[0], 1e-6f)  // R scale
        assertEquals(0f, arr[1], 1e-6f)  // off-diagonal zero
    }

    @Test
    fun `matrix multiply`() {
        val a = ColorMatrixF32()
        a.setScale(2f, 1f, 1f)
        val b = ColorMatrixF32()
        b.setScale(1f, 0.5f, 1f)
        val c = a * b
        val arr = c.toFloatArray()
        assertEquals(2f, arr[0])   // 2 * 1
        assertEquals(0.5f, arr[6]) // 1 * 0.5
        assertEquals(1f, arr[12])  // 1 * 1
    }

    @Test
    fun `postTranslate adds to bias`() {
        val m = ColorMatrixF32()
        m.postTranslate(0.5f, 0.25f, 0.125f, 0f)
        val arr = m.toFloatArray()
        assertEquals(0.5f, arr[4])
        assertEquals(0.25f, arr[9])
        assertEquals(0.125f, arr[14])
    }

    @Test
    fun `setRowMajor and getRowMajor`() {
        val values = FloatArray(20) { it.toFloat() }
        val m = ColorMatrixF32(values)
        val dst = FloatArray(20)
        m.getRowMajor(dst)
        for (i in 0 until 20) {
            assertEquals(i.toFloat(), dst[i])
        }
    }

    @Test
    fun `equals and hashCode`() {
        val a = ColorMatrixF32()
        val b = ColorMatrixF32()
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())

        a.setScale(0.5f, 1f, 1f)
        assertNotEquals(a, b)
    }

    @Test
    fun `preConcat and postConcat`() {
        val a = ColorMatrixF32()
        a.setScale(2f, 1f, 1f)
        val b = ColorMatrixF32()
        b.setScale(1f, 3f, 1f)
        val c = a * b
        a.preConcat(b) // a = b * a
        val arr = a.toFloatArray()
        assertEquals(c.toFloatArray().asList(), arr.asList())
    }

    @Test
    fun `setRGB2YUV and setYUV2RGB`() {
        val m = ColorMatrixF32()
        m.setRGB2YUV()
        val rgb2yuv = m.toFloatArray()
        m.setYUV2RGB()
        val yuv2rgb = m.toFloatArray()
        // Both should be non-identity
        assertNotEquals(1f, rgb2yuv[1])
        assertNotEquals(1f, yuv2rgb[2])
    }
}
