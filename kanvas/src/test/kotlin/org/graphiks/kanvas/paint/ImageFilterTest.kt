package org.graphiks.kanvas.paint

import org.graphiks.kanvas.types.Color
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull

class ImageFilterTest {
    @Test
    fun `Blur with no input`() {
        val f = ImageFilter.Blur(sigmaX = 4f, sigmaY = 4f)
        assertEquals(4f, f.sigmaX)
        assertNull(f.input)
    }

    @Test
    fun `Blur with input chain`() {
        val inner = ImageFilter.Blur(2f, 2f)
        val outer = ImageFilter.Blur(4f, 4f, input = inner)
        assertEquals(inner, outer.input)
    }

    @Test
    fun `DropShadow filter`() {
        val f = ImageFilter.DropShadow(2f, 3f, 4f, 4f, Color.BLACK)
        assertEquals(2f, f.dx)
        assertEquals(3f, f.dy)
        assertEquals(Color.BLACK, f.color)
    }

    @Test
    fun `ColorFilter wrapper`() {
        val cf = ColorFilter.Blend(Color.RED, BlendMode.MULTIPLY)
        val f = ImageFilter.ColorFilter(cf)
        assertEquals(cf, f.filter)
    }

    @Test
    fun `Compose two filters`() {
        val inner = ImageFilter.Blur(2f, 2f)
        val outer = ImageFilter.Blur(4f, 4f)
        val f = ImageFilter.Compose(outer, inner)
        assertEquals(outer, f.outer)
        assertEquals(inner, f.inner)
    }

    @Test
    fun `Blend two filters`() {
        val bg = ImageFilter.Blur(2f, 2f)
        val fg = ImageFilter.Blur(4f, 4f)
        val f = ImageFilter.Blend(BlendMode.SRC_OVER, bg, fg)
        assertEquals(bg, f.background)
    }
}
