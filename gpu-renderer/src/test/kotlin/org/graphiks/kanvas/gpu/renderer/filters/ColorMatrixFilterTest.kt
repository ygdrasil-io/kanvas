package org.graphiks.kanvas.gpu.renderer.filters

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ColorMatrixFilterTest {

    @Test
    fun `execute with identity matrix produces accepted result`() {
        val filter = ColorMatrixFilter()
        val result = filter.execute(ColorMatrix())
        assertTrue(result.accepted)
    }

    @Test
    fun `identity matrix has correct dimensions`() {
        val matrix = ColorMatrix.identity()
        assertEquals(20, matrix.size)
    }

    @Test
    fun `identity matrix has ones on diagonal`() {
        val values = ColorMatrix.identity()
        assertEquals(1f, values[0])
        assertEquals(1f, values[6])
        assertEquals(1f, values[12])
        assertEquals(1f, values[18])
    }

    @Test
    fun `identity matrix has zeros on last column bias`() {
        val values = ColorMatrix.identity()
        assertEquals(0f, values[4])
        assertEquals(0f, values[9])
        assertEquals(0f, values[14])
        assertEquals(0f, values[19])
    }

    @Test
    fun `custom color matrix values are preserved`() {
        val customValues = floatArrayOf(
            0.5f, 0f, 0f, 0f, 0f,
            0f, 0.5f, 0f, 0f, 0f,
            0f, 0f, 0.5f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
        val matrix = ColorMatrix(customValues)
        assertEquals(customValues, matrix.values)
    }
}
